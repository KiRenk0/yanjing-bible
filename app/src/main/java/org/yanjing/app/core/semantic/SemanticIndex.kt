package org.yanjing.app.core.semantic

import org.yanjing.app.core.VerseKey
import java.io.InputStream
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.PriorityQueue

data class SemanticCandidate(val key: VerseKey, val cosine: Float)
class SemanticIndex(input: InputStream) {
    val count: Int
    val dimensions: Int
    private val keys: IntArray
    private val inverseNorms: FloatArray
    private val vectors: ByteArray
    init {
        DataInputStream(input.buffered()).use { stream ->
            val header = ByteArray(20).also(stream::readFully)
            require(String(header, 0, 8, Charsets.US_ASCII) == "YJSEM001") { "语义索引格式错误" }
            val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).apply { position(8) }
            count = buffer.int; dimensions = buffer.int
            require(count in 1..40000 && dimensions == 512 && buffer.int == 256)
            keys = IntArray(count); inverseNorms = FloatArray(count); vectors = ByteArray(count * dimensions)
            val row = ByteArray(dimensions + 8)
            for (i in 0 until count) {
                stream.readFully(row)
                val metadata = ByteBuffer.wrap(row).order(ByteOrder.LITTLE_ENDIAN)
                keys[i] = metadata.int; inverseNorms[i] = metadata.float
                require(inverseNorms[i].isFinite() && inverseNorms[i] > 0)
                System.arraycopy(row, 8, vectors, i * dimensions, dimensions)
            }
            require(stream.read() == -1) { "语义索引包含多余内容" }
        }
    }
    fun nearest(query: FloatArray, limit: Int = 60, accepts: (VerseKey) -> Boolean = { true }, checkpoint: () -> Unit = {}): List<SemanticCandidate> {
        require(query.size == dimensions && query.all { it.isFinite() })
        require(limit > 0)
        val heap = PriorityQueue<SemanticCandidate>(compareBy { it.cosine })
        for (row in 0 until count) {
            if (row % 128 == 0) checkpoint()
            val encoded = keys[row]
            val key = VerseKey(encoded / 1_000_000, (encoded / 1000) % 1000, encoded % 1000)
            if (!accepts(key)) continue
            var dot = 0f
            val base = row * dimensions
            for (j in 0 until dimensions) dot += vectors[base + j].toInt() * query[j]
            val score = (dot * inverseNorms[row]).coerceIn(-1f, 1f)
            if (heap.size < limit) heap.add(SemanticCandidate(key, score))
            else if (score > heap.element().cosine) { heap.poll(); heap.add(SemanticCandidate(key, score)) }
        }
        return heap.sortedByDescending { it.cosine }
    }
}
