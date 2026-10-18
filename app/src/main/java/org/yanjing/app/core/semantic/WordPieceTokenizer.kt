package org.yanjing.app.core.semantic

/** BERT WordPiece matching the pinned tokenizer.json (case-sensitive, no accent stripping). */
class WordPieceTokenizer(vocabulary: List<String>, private val maxTokens: Int = 256) {
    private val vocab = vocabulary.withIndex().associate { it.value to it.index.toLong() }
    private val unknown = requireNotNull(vocab["[UNK]"])
    private val cls = requireNotNull(vocab["[CLS]"])
    private val sep = requireNotNull(vocab["[SEP]"])
    private val special = Regex("\\[PAD]|\\[UNK]|\\[CLS]|\\[SEP]|\\[MASK]")
    init { require(maxTokens >= 2) }

    fun encode(text: String): LongArray {
        val result = ArrayList<Long>()
        result += cls
        var from = 0
        for (match in special.findAll(text)) {
            tokenize(text.substring(from, match.range.first), result)
            if (result.size < maxTokens - 1) result += requireNotNull(vocab[match.value])
            from = match.range.last + 1
        }
        tokenize(text.substring(from), result)
        result += sep
        return result.toLongArray()
    }
    private fun tokenize(text: String, output: MutableList<Long>) {
        val word = StringBuilder()
        fun flush() {
            if (word.isEmpty()) return
            if (output.size < maxTokens - 1) output += pieces(word.toString()).take(maxTokens - 1 - output.size)
            word.setLength(0)
        }
        var offset = 0
        while (offset < text.length && output.size < maxTokens - 1) {
            val cp = text.codePointAt(offset)
            offset += Character.charCount(cp)
            val type = Character.getType(cp)
            if (cp == 0 || cp == 0xfffd || (type in listOf(Character.CONTROL.toInt(), Character.FORMAT.toInt()) && cp !in listOf(9,10,13))) continue
            when {
                Character.isWhitespace(cp) || Character.isSpaceChar(cp) -> flush()
                isChinese(cp) || isPunctuation(cp) -> { flush(); if (output.size < maxTokens - 1) output += vocab[String(Character.toChars(cp))] ?: unknown }
                else -> word.appendCodePoint(cp)
            }
        }
        flush()
    }
    private fun pieces(word: String): List<Long> {
        if (word.codePointCount(0, word.length) > 100) return listOf(unknown)
        val result = mutableListOf<Long>()
        var start = 0
        while (start < word.length) {
            var end = word.length
            var found: Long? = null
            while (end > start) {
                val part = (if (start > 0) "##" else "") + word.substring(start, end)
                found = vocab[part]
                if (found != null) break
                end = word.offsetByCodePoints(end, -1)
            }
            if (found == null) return listOf(unknown)
            result += found
            start = end
        }
        return result
    }
    private fun isChinese(cp: Int): Boolean = cp in 0x4e00..0x9fff || cp in 0x3400..0x4dbf ||
        cp in 0x20000..0x2a6df || cp in 0x2a700..0x2b73f || cp in 0x2b740..0x2b81f ||
        cp in 0x2b820..0x2ceaf || cp in 0xf900..0xfaff || cp in 0x2f800..0x2fa1f
    private fun isPunctuation(cp: Int): Boolean = cp in 33..47 || cp in 58..64 || cp in 91..96 || cp in 123..126 ||
        Character.getType(cp) in intArrayOf(Character.CONNECTOR_PUNCTUATION.toInt(), Character.DASH_PUNCTUATION.toInt(),
            Character.START_PUNCTUATION.toInt(), Character.END_PUNCTUATION.toInt(), Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
            Character.FINAL_QUOTE_PUNCTUATION.toInt(), Character.OTHER_PUNCTUATION.toInt())
}
