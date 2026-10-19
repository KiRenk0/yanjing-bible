package org.yanjing.app.core

data class ClassicWork(val version: BibleVersion, val book: Int, val title: String,
    val names: List<String>, val verseCounts: List<Int>, val source: String) {
    private val unit get() = when(version) { BibleVersion.TAO_TE_CHING -> "章"; BibleVersion.DIAMOND_SUTRA -> "分"; else -> "篇" }
    fun chapterTitle(chapter: Int) = if (names.size == 1) names[0] else if(version==BibleVersion.TAO_TE_CHING) "第 $chapter 章" else "第 $chapter $unit · ${names.getOrNull(chapter-1).orEmpty()}"
    val summary get() = "${names.size} $unit · ${verseCounts.sum()} 段 · 现代汉语搜索文言原文"
    val sourceUrl get() = when(source) {
        "chinese-poetry" -> "https://github.com/chinese-poetry/chinese-poetry/tree/fa1814a8044db4fb0c7a9e0a0449974e8c3463b7/四书五经"
        "hooosberg" -> "https://github.com/hooosberg/Rushi/tree/bda9d95a2876aec4dc4d8b60b2594d9de79a1062/scriptures"
        else -> "https://github.com/banghuazhao/Chinese-Classical-Literature-Android/tree/f2c40b864c00c97d2d148a82857884fe8ba527d4/app/src/main/assets/files"
    }
    val credit get() = when(source) { "chinese-poetry" -> "chinese-poetry · MIT"; "hooosberg" -> "Rushi · CC0 1.0"; else -> "Chinese-Classical-Literature-Android · MIT" }
}

object Classics {
    val works: List<ClassicWork> by lazy {
        listOf(ClassicWork(BibleVersion.ANALECTS,Analects.BOOK,"论语",Analects.names,Analects.verseCounts,"chinese-poetry")) + classicWorks
    }
    fun forVersion(version: BibleVersion) = works.firstOrNull { it.version == version }
    fun forBook(book: Int) = works.firstOrNull { it.book == book }
    fun suggestions(version: BibleVersion): List<String> = when(version) {
        BibleVersion.ANALECTS -> Analects.suggestions
        BibleVersion.GREAT_LEARNING -> listOf("先完善自己再管理家庭和国家", "一个人独处也要诚实自律", "每天改进自己", "大学 1:1")
        BibleVersion.DOCTRINE_MEAN -> listOf("与人和睦也要坚持原则", "做好当前处境中该做的事", "广泛学习认真思考踏实行动", "中庸 1:1")
        BibleVersion.MENCIUS -> listOf("强权不能让我放弃原则", "经历困难才能承担重任", "尊敬别人的老人爱护别人的孩子", "孟子 1:1")
        BibleVersion.HEART_SUTRA -> listOf("放下执着和恐惧", "现象和空性并不分离", "心经 1:1")
        BibleVersion.DIAMOND_SUTRA -> listOf("帮助别人不执着于回报", "事物像梦和泡沫一样短暂", "不把心依附在外在事物上", "金刚经 32")
        BibleVersion.TAO_TE_CHING -> listOf("像水一样帮助别人而不争", "漫长道路从脚下开始", "了解自己比战胜别人更重要", "道德经 8")
        BibleVersion.ZHUANGZI -> listOf("顺着事物规律做事减少强行用力", "生命有限知识无限", "井里的青蛙不了解大海", "庄子 1:1")
        else -> emptyList()
    }
    fun parse(raw: String, normalizer: TextNormalizer, version: BibleVersion): ReferenceQuery? {
        if (version == BibleVersion.ANALECTS) return Analects.parse(raw,normalizer)
        val work = forVersion(version) ?: return null
        var text=normalizer.script(raw.trim()).replace(Regex("^《?${work.title}》?[·・\\s]*"),"").trim()
        val named=work.names.withIndex().firstOrNull { text.startsWith(normalizer.script(it.value)) }
        if(named!=null) {
            text=text.removePrefix(normalizer.script(named.value)).removePrefix("篇").trim()
            if(text.isEmpty()) return ReferenceQuery(work.book,named.index+1,null,null)
            if(':' !in text) text="${named.index+1}:"+text.removePrefix("第").removeSuffix("段").removeSuffix("章").trim()
            else if(text.substringBefore(':').trim().toIntOrNull()!=named.index+1) return null
        }
        text=Regex("[零〇一二三四五六七八九十百]+").replace(text) { chineseNumber(it.value)?.toString() ?: it.value }
            .replace(Regex("第\\s*"),"").replace(Regex("\\s+"),"")
        val m=Regex("^(\\d+)(?:(?:篇|章|:)(\\d+)(?:[-–—](\\d+))?[段章节]?|[篇章])?$").matchEntire(text) ?: return null
        val chapter=m.groupValues[1].toIntOrNull() ?: return null
        val start=m.groupValues[2].takeIf(String::isNotEmpty)?.toIntOrNull()
        val end=m.groupValues[3].takeIf(String::isNotEmpty)?.toIntOrNull() ?: start
        return ReferenceQuery(work.book,chapter,start,end)
    }
    private fun chineseNumber(text: String): Int? {
        val digits="零一二三四五六七八九"
        if(text.length==1 && text[0] in digits) return digits.indexOf(text[0])
        val parts=text.split('十')
        if(parts.size!=2 || parts.any { it.length>1 || (it.isNotEmpty() && it[0] !in digits) }) return null
        return (parts[0].firstOrNull()?.let(digits::indexOf) ?: 1)*10+(parts[1].firstOrNull()?.let(digits::indexOf) ?: 0)
    }
}
