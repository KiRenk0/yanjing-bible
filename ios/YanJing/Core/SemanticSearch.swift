import Foundation
import Accelerate
import CryptoKit
import OnnxRuntimeBindings

/// Uses the same pinned BGE model, WordPiece vocabulary and quantized corpus indexes as Android.
final class SemanticSearch {
    private let env: ORTEnv
    private let session: ORTSession
    private let tokenizer: WordPieceTokenizer
    private let root: URL
    private var loadedIndex = ""
    private var data = Data()
    init(root: URL) throws {
        self.root = root
        let model = root.appendingPathComponent("semantic/bge-small-zh-int8.onnx")
        let bytes = try Data(contentsOf: model, options: .mappedIfSafe)
        guard SHA256.hash(data: bytes).map({ String(format: "%02x", $0) }).joined() == "15b717c382bcb518ba457b93ea6850ede7f4f1cd8937454aa06972366cd19bcc" else { throw LibraryError.invalidData("model") }
        env = try ORTEnv(loggingLevel: .error)
        let options = try ORTSessionOptions()
        try options.setIntraOpNumThreads(2)
        session = try ORTSession(env: env, modelPath: model.path, sessionOptions: options)
        tokenizer = try WordPieceTokenizer(vocabulary: String(contentsOf: root.appendingPathComponent("semantic/vocab.txt"), encoding: .utf8).components(separatedBy: "\n"))
    }
    func embedding(_ text: String) throws -> [Float] {
        let ids = tokenizer.encode("为这个句子生成表示以用于检索相关文章：" + TextTools.simplified(text))
        func tensor(_ values: [Int64]) throws -> ORTValue {
            let bytes = values.withUnsafeBytes { NSMutableData(bytes: $0.baseAddress!, length: $0.count) }
            return try ORTValue(tensorData: bytes, elementType: .int64, shape: [1, NSNumber(value: ids.count)])
        }
        let names = try session.outputNames()
        guard let outputName = names.first else { throw LibraryError.invalidData("model output") }
        let output = try session.run(withInputs: ["input_ids": tensor(ids), "attention_mask": tensor(Array(repeating: 1, count: ids.count)), "token_type_ids": tensor(Array(repeating: 0, count: ids.count))], outputNames: Set([outputName]), runOptions: nil)
        guard let value = output[outputName] else { throw LibraryError.invalidData("embedding") }
        let raw = try value.tensorData() as Data
        guard raw.count >= 512 * MemoryLayout<Float>.size else { throw LibraryError.invalidData("embedding size") }
        var vector = raw.prefix(512 * 4).withUnsafeBytes { Array($0.bindMemory(to: Float.self)) }
        let norm = sqrt(vector.reduce(Float(0)) { $0 + $1 * $1 })
        guard norm.isFinite, norm > 0, vector.allSatisfy(\.isFinite) else { throw LibraryError.invalidData("embedding norm") }
        for i in vector.indices { vector[i] /= norm }
        return vector
    }
    func nearest(_ text: String, index: String) throws -> [(VerseKey, Double)] {
        try Task.checkCancellation()
        let query = try embedding(text)
        if loadedIndex != index {
            data = try Data(contentsOf: root.appendingPathComponent("semantic/\(index).i8"), options: .mappedIfSafe)
            loadedIndex = index
        }
        guard data.count >= 20, String(data: data.prefix(8), encoding: .ascii) == "YJSEM001" else { throw LibraryError.invalidData("index header") }
        return try data.withUnsafeBytes { buffer in
            let count = Int(UInt32(littleEndian: buffer.loadUnaligned(fromByteOffset: 8, as: UInt32.self)))
            let dimensions = UInt32(littleEndian: buffer.loadUnaligned(fromByteOffset: 12, as: UInt32.self))
            let tokens = UInt32(littleEndian: buffer.loadUnaligned(fromByteOffset: 16, as: UInt32.self))
            guard count > 0, count <= 60000, dimensions == 512, tokens == 256, data.count == 20 + count * 520 else { throw LibraryError.invalidData("index dimensions") }
            var best: [VerseKey: Double] = [:]
            var floats = [Float](repeating: 0, count: 512)
            for row in 0..<count {
                if row % 128 == 0 { try Task.checkCancellation() }
                let offset = 20 + row * 520
                let encoded = Int(UInt32(littleEndian: buffer.loadUnaligned(fromByteOffset: offset, as: UInt32.self)))
                let inverse = Float(bitPattern: UInt32(littleEndian: buffer.loadUnaligned(fromByteOffset: offset + 4, as: UInt32.self)))
                guard inverse.isFinite, inverse > 0 else { throw LibraryError.invalidData("index norm") }
                let bytes = buffer.baseAddress!.advanced(by: offset + 8).assumingMemoryBound(to: Int8.self)
                vDSP_vflt8(bytes, 1, &floats, 1, 512)
                var dot: Float = 0
                vDSP_dotpr(floats, 1, query, 1, &dot, 512)
                let score = Double(dot * inverse)
                let key = VerseKey(encoded / 1_000_000, (encoded / 1000) % 1000, encoded % 1000)
                if score > best[key, default: -.infinity] { best[key] = score }
            }
            let ranked = best.sorted { $0.value > $1.value }
            let threshold = max(0.53, (ranked.first?.value ?? 1) - 0.10)
            return ranked.prefix(80).filter { $0.value >= threshold }.map { ($0.key, $0.value) }
        }
    }
}

struct WordPieceTokenizer {
    private let vocab: [String: Int64]
    init(vocabulary: [String]) throws {
        var result: [String: Int64] = [:]
        for (index, token) in vocabulary.enumerated() where !token.isEmpty { result[token] = Int64(index) }
        guard ["[UNK]", "[CLS]", "[SEP]"].allSatisfy({ result[$0] != nil }) else { throw LibraryError.invalidData("vocabulary") }
        vocab = result
    }
    func encode(_ text: String) -> [Int64] {
        var output = [vocab["[CLS]"]!]
        var word = ""
        func flush() {
            guard !word.isEmpty else { return }
            output += pieces(word).prefix(max(0, 255 - output.count)); word = ""
        }
        let special = try! NSRegularExpression(pattern: "\\[PAD\\]|\\[UNK\\]|\\[CLS\\]|\\[SEP\\]|\\[MASK\\]")
        let matches = special.matches(in: text, range: NSRange(text.startIndex..., in: text))
        var from = text.startIndex
        func append(_ part: Substring) {
            for scalar in part.unicodeScalars {
                guard output.count < 255 else { break }
                let cp = scalar.value
                if cp == 0 || cp == 0xfffd || ((CharacterSet.controlCharacters.contains(scalar) || scalar.properties.generalCategory == .format) && ![9, 10, 13].contains(cp)) { continue }
                if CharacterSet.whitespacesAndNewlines.contains(scalar) { flush() }
                else if Self.isChinese(cp) || CharacterSet.punctuationCharacters.contains(scalar) || (33...47).contains(cp) || (58...64).contains(cp) || (91...96).contains(cp) || (123...126).contains(cp) {
                    flush(); if output.count < 255 { output.append(vocab[String(scalar)] ?? vocab["[UNK]"]!) }
                } else { word.unicodeScalars.append(scalar) }
            }
            flush()
        }
        for match in matches {
            guard let range = Range(match.range, in: text) else { continue }
            append(text[from..<range.lowerBound]); if output.count < 255 { output.append(vocab[String(text[range])]!) }; from = range.upperBound
        }
        append(text[from...]); output.append(vocab["[SEP]"]!)
        return output
    }
    private func pieces(_ word: String) -> [Int64] {
        let scalars = Array(word.unicodeScalars)
        guard scalars.count <= 100 else { return [vocab["[UNK]"]!] }
        var result: [Int64] = [], start = 0
        while start < scalars.count {
            var end = scalars.count, found: Int64?
            while end > start {
                let piece = (start > 0 ? "##" : "") + String(String.UnicodeScalarView(scalars[start..<end]))
                if let id = vocab[piece] { found = id; break }; end -= 1
            }
            guard let id = found else { return [vocab["[UNK]"]!] }
            result.append(id); start = end
        }
        return result
    }
    private static func isChinese(_ cp: UInt32) -> Bool {
        let ranges: [ClosedRange<UInt32>] = [0x4e00...0x9fff, 0x3400...0x4dbf, 0x20000...0x2a6df, 0x2a700...0x2b73f, 0x2b740...0x2b81f, 0x2b820...0x2ceaf, 0xf900...0xfaff, 0x2f800...0x2fa1f]
        return ranges.contains { $0.contains(cp) }
    }
}
