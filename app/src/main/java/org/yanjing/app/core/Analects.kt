package org.yanjing.app.core

/** Chapter/paragraph numbering follows the bundled, pinned chinese-poetry edition. */
object Analects {
    const val BOOK = 68
    val names = listOf("学而", "为政", "八佾", "里仁", "公冶长", "雍也", "述而", "泰伯", "子罕", "乡党", "先进", "颜渊", "子路", "宪问", "卫灵公", "季氏", "阳货", "微子", "子张", "尧曰")
    val verseCounts = listOf(16,24,26,26,28,30,38,21,31,27,26,24,30,44,42,14,26,11,25,3)
    fun chapterName(chapter: Int) = names.getOrNull(chapter - 1).orEmpty()
    fun chapterTitle(chapter: Int) = "第 $chapter 篇 · ${chapterName(chapter)}"
    val suggestions = listOf("自己不愿意的事不要强加给别人", "学习要结合思考", "别人不了解我也不生气", "向不如自己的人请教", "论语 1:1", "学而篇")

    fun parse(raw: String, normalizer: TextNormalizer): ReferenceQuery? {
        var text = normalizer.script(raw.trim()).replace(Regex("^《?论语》?[·・\\s]*"), "").trim()
        val named = names.withIndex().firstOrNull { text.startsWith(it.value) }
        if (named != null) {
            text = text.removePrefix(named.value).removePrefix("篇").trim()
            if (text.isEmpty()) return ReferenceQuery(BOOK, named.index + 1, null, null)
            // The full displayed reference includes the name and numeric chapter:verse.
            if (':' !in text) text = "${named.index + 1}:" + text.removePrefix("第").removeSuffix("章").trim()
            else if (text.substringBefore(':').trim().toIntOrNull() != named.index + 1) return null
        }
        text = Regex("[一二三四五六七八九十百零〇]+").replace(text) { match ->
            chineseNumber(match.value)?.toString() ?: match.value
        }
        text = text.replace(Regex("第\\s*"), "").replace(Regex("\\s+"), "")
        val match = Regex("^(\\d+)(?:(?:篇|:)(\\d+)(?:[-–—](\\d+))?章?|篇)?$").matchEntire(text) ?: return null
        val chapter = match.groupValues[1].toIntOrNull() ?: -1
        val start = match.groupValues[2].takeIf(String::isNotEmpty)?.let { it.toIntOrNull() ?: -1 }
        val end = match.groupValues[3].takeIf(String::isNotEmpty)?.let { it.toIntOrNull() ?: -1 } ?: start
        return ReferenceQuery(BOOK, chapter, start, end)
    }

    private fun chineseNumber(text: String): Int? {
        val digits = "零一二三四五六七八九"
        if (text.length == 1 && text[0] in digits) return digits.indexOf(text[0])
        val parts = text.split('十')
        if (parts.size != 2 || parts.any { it.length > 1 || (it.isNotEmpty() && it[0] !in digits) }) return null
        return (parts[0].firstOrNull()?.let { digits.indexOf(it) } ?: 1) * 10 + (parts[1].firstOrNull()?.let { digits.indexOf(it) } ?: 0)
    }
}
