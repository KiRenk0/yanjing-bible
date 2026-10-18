package org.yanjing.app.core

object Quran {
    // A distinct namespace preserves all existing Bible and favorite keys.
    const val BOOK = 67
    val verseCounts: List<Int> get() = quranVerseCounts
    val basmala: String get() = quranBasmala
    fun chapterPrelude(chapter: Int): String? = if (chapter !in 2..114 || chapter == 9) null else quranBasmalaVariants[chapter] ?: basmala
    fun chapterName(number: Int) = quranChapterNames.getOrNull(number - 1).orEmpty()
    fun chapterTitle(number: Int) = "第 $number 章 · ${chapterName(number)}"
    val suggestions = listOf("古兰经 1:1-7", "困难之后会有容易", "不要因绝望而放弃", "古兰经 2:255", "公平待人", "古兰经 112:1-4")
    private val names: Map<String, Int> by lazy {
        buildMap {
            quranChapterNames.forEachIndexed { i, name -> put(name, i + 1); put(name + "章", i + 1) }
            quranTransliterations.forEachIndexed { i, name -> put(name.lowercase(), i + 1) }
            put("开端章",1); put("黄牛章",2); put("忠诚章",112); put("黎明章",113); put("世人章",114)
        }
    }
    fun parse(raw: String, normalizer: TextNormalizer): ReferenceQuery? {
        val text = normalizer.script(raw.trim()).map { c ->
            if (c in '٠'..'٩' || c in '۰'..'۹') Character.digit(c,10).digitToChar() else c
        }.joinToString("").trim()
        val named = names.entries.sortedByDescending { it.key.length }.firstOrNull { text == it.key || text.startsWith(it.key + " ") || text.startsWith(it.key + ":") || (text.startsWith(it.key) && text.getOrNull(it.key.length)?.isDigit() == true) }
        if (named != null) {
            val rest = text.removePrefix(named.key).trim().removePrefix(":").trim().removeSuffix("节").trim()
            if (rest.isEmpty()) return ReferenceQuery(BOOK,named.value,null,null)
            Regex("^(\\d+)(?:[-–—](\\d+))?$").matchEntire(rest)?.let {
                val start=it.groupValues[1].toIntOrNull() ?: -1
                val end=it.groupValues[2].takeIf(String::isNotEmpty)?.let { value -> value.toIntOrNull() ?: -1 } ?: start
                return ReferenceQuery(BOOK,named.value,start,end)
            }
        }
        val cleaned = text.replace(Regex("^(古兰经|可兰经|quran|qur'an|قرآن)\\s*"), "")
            .replace("第", "").trim()
        val match = Regex("^(\\d+)\\s*(?:(?::|章)\\s*(\\d+)\\s*(?:[-–—]\\s*(\\d+))?\\s*节?|章)?$").matchEntire(cleaned) ?: return null
        val chapter = match.groupValues[1].toIntOrNull() ?: -1
        val start = match.groupValues[2].takeIf(String::isNotEmpty)?.let { it.toIntOrNull() ?: -1 }
        val end = match.groupValues[3].takeIf(String::isNotEmpty)?.let { it.toIntOrNull() ?: -1 } ?: start
        return ReferenceQuery(BOOK,chapter,start,end)
    }
}
