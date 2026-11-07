package org.yanjing.app.core

import org.yanjing.app.i18n.tr
import org.yanjing.app.R

import java.text.Normalizer
import java.util.Locale

enum class Scripture(private val titleRes: Int) { BIBLE(R.string.ui_0e2cd5747f), QURAN(R.string.ui_606dbd6b27), ANALECTS(R.string.ui_c51146217b), BUDDHISM(R.string.ui_1363ec4228), TAOISM(R.string.ui_d1e21b634c); val title: String get() = tr(titleRes) }

enum class BibleVersion(val id: String, private val titleRes: Int, private val descriptionRes: Int, val scripture: Scripture = Scripture.BIBLE) {
    CUVS("cuvs", R.string.ui_baace69a71, R.string.ui_7b13bab106),
    CUVT("cuvt", R.string.ui_d23d957ed9, R.string.ui_2f5dcf76af),
    WENLI("wenli", R.string.ui_bf20918500, R.string.ui_fa7e933721),
    KJV("kjv", R.string.ui_fd94c82c5f, R.string.ui_cfd9ce64d1),
    VULGATE("vulgate", R.string.ui_246ccd291a, R.string.ui_eced9bec0a),
    QURAN_ZH("quran_zh", R.string.ui_edf5851580, R.string.ui_d931243865, Scripture.QURAN),
    QURAN_AR("quran_ar", R.string.ui_d76bdeaf42, R.string.ui_e7c6bcd6de, Scripture.QURAN),
    ANALECTS("analects", R.string.ui_d119fee8ed, R.string.ui_0d068ebbea, Scripture.ANALECTS),
    GREAT_LEARNING("daxue", R.string.ui_0d8bb379d0, R.string.ui_544227b298, Scripture.ANALECTS),
    DOCTRINE_MEAN("zhongyong", R.string.ui_4bb73f988e, R.string.ui_544227b298, Scripture.ANALECTS),
    MENCIUS("mengzi", R.string.ui_6069e51f61, R.string.ui_83f279fc4d, Scripture.ANALECTS),
    HEART_SUTRA("heart_sutra", R.string.ui_af70da5ec9, R.string.ui_cff0c6b7e1, Scripture.BUDDHISM),
    DIAMOND_SUTRA("diamond_sutra", R.string.ui_91811727af, R.string.ui_6c34cc91f4, Scripture.BUDDHISM),
    LOTUS_SUTRA("lotus_sutra", R.string.ui_482fc29a96, R.string.ui_b4d5e67994, Scripture.BUDDHISM),
    TAO_TE_CHING("tao_te_ching", R.string.ui_758969d38e, R.string.ui_2744c4fa16, Scripture.TAOISM),
    ZHUANGZI("zhuangzi", R.string.ui_2d6696e0b7, R.string.ui_195c12a441, Scripture.TAOISM);

    val title: String get() = tr(titleRes)
    val description: String get() = tr(descriptionRes)
    val isQuran: Boolean get() = scripture == Scripture.QURAN
    val isAnalects: Boolean get() = this == ANALECTS
    val isClassical: Boolean get() = scripture !in listOf(Scripture.BIBLE,Scripture.QURAN)
    val isRtl: Boolean get() = this == QURAN_AR
    val peers: List<BibleVersion> get() = if(isClassical) listOf(this) else entries.filter { it.scripture == scripture }
    val corpusId: String get() = if((isClassical && !isAnalects) || this==VULGATE) id else scripture.name
    val defaultBook: Int get() = when { isClassical -> Classics.forVersion(this)!!.book; isQuran -> Quran.BOOK; this==VULGATE -> 150; else -> 43 }
    fun acceptsBook(book: Int) = when { isClassical -> book==defaultBook; isQuran -> book==Quran.BOOK; this==VULGATE -> book in 101..173; else -> book in 1..66 }
}

data class VerseKey(val book: Int, val chapter: Int, val verse: Int) {
    val encoded: String get() = "$book:$chapter:$verse"
    val scripture: Scripture get() = if (book == Quran.BOOK) Scripture.QURAN else Classics.forBook(book)?.version?.scripture ?: Scripture.BIBLE
    val reference: String get() = when {
        book == Quran.BOOK -> "古兰经 $chapter:$verse"
        book == Analects.BOOK -> "论语·${Analects.chapterName(chapter)} $chapter:$verse"
        Classics.forBook(book) != null -> Classics.forBook(book)!!.let { "${it.title}·${it.names.getOrNull(chapter-1).orEmpty()} $chapter:$verse" }
        book in 101..173 -> "${Vulgate.bookName(book)}〔拉丁文〕 $chapter:$verse"
        else -> "${org.yanjing.app.i18n.I18n.bookName(book)} $chapter:$verse"
    }
    companion object {
        fun decode(raw: String): VerseKey? {
            val values = raw.split(':').map { it.toIntOrNull() ?: return null }
            if (values.size != 3 || values[1] <= 0 || values[2] <= 0) return null
            val (book, chapter, verse) = values
            val counts = when {
                book == Quran.BOOK -> Quran.verseCounts
                book in 101..173 -> Vulgate.verseCounts[book]
                else -> Classics.forBook(book)?.verseCounts
            }
            if (counts != null) return if (counts.getOrNull(chapter-1)?.let { verse <= it } == true) VerseKey(book, chapter, verse) else null
            return if (book in 1..66) VerseKey(book, chapter, verse) else null
        }
    }
}

data class Verse(val key: VerseKey, val text: String, val normalized: String, val notes: String = "", val searchGloss: String = "", val canonicalKeys: List<VerseKey> = emptyList())
data class SearchHit(val verse: Verse, val score: Double, val reason: String, val matchedModes: Set<SearchMode> = emptySet())
enum class SearchMode(private val titleRes: Int, private val descriptionRes: Int) {
    SEMANTIC(R.string.ui_948d1d6892, R.string.ui_83c7b2c9ca),
    EXPRESSION(R.string.ui_b9347c5e68, R.string.ui_13a8f0257f),
    KEYWORD(R.string.ui_cda0204d03, R.string.ui_ab41007196);
    val title: String get() = tr(titleRes)
    val description: String get() = tr(descriptionRes)
}
enum class Testament(private val titleRes: Int) { ALL(R.string.ui_67d66ee478), OLD(R.string.ui_f9c7acfecb), NEW(R.string.ui_a18281e062);
    val title: String get() = tr(titleRes)
    fun accepts(book: Int) = this == ALL || (this == OLD && (book in 1..39 || book in 101..146)) || (this == NEW && (book in 40..66 || book in 147..173))
}
data class SearchResponse(val hits: List<SearchHit>, val total: Int, val message: String? = null)

object VerseMappings {
    // In the pinned ChiUnL source, Matthew 6:34 is empty and its text is joined to 6:33.
    // Always keep the actual source verse key when displaying, saving, or copying this text.
    fun resolve(version: BibleVersion, key: VerseKey): VerseKey = when (version) {
        BibleVersion.CUVS, BibleVersion.CUVT -> cuvVerseBridges[key] ?: key
        BibleVersion.WENLI -> if (key == VerseKey(40, 6, 34)) VerseKey(40, 6, 33) else key
        else -> key
    }
}

object Books {
    val names = listOf("创世记", "出埃及记", "利未记", "民数记", "申命记", "约书亚记", "士师记", "路得记", "撒母耳记上", "撒母耳记下", "列王纪上", "列王纪下", "历代志上", "历代志下", "以斯拉记", "尼希米记", "以斯帖记", "约伯记", "诗篇", "箴言", "传道书", "雅歌", "以赛亚书", "耶利米书", "耶利米哀歌", "以西结书", "但以理书", "何西阿书", "约珥书", "阿摩司书", "俄巴底亚书", "约拿书", "弥迦书", "那鸿书", "哈巴谷书", "西番雅书", "哈该书", "撒迦利亚书", "玛拉基书", "马太福音", "马可福音", "路加福音", "约翰福音", "使徒行传", "罗马书", "哥林多前书", "哥林多后书", "加拉太书", "以弗所书", "腓立比书", "歌罗西书", "帖撒罗尼迦前书", "帖撒罗尼迦后书", "提摩太前书", "提摩太后书", "提多书", "腓利门书", "希伯来书", "雅各书", "彼得前书", "彼得后书", "约翰一书", "约翰二书", "约翰三书", "犹大书", "启示录")
    val shortNames = listOf("创", "出", "利", "民", "申", "书", "士", "得", "撒上", "撒下", "王上", "王下", "代上", "代下", "拉", "尼", "斯", "伯", "诗", "箴", "传", "歌", "赛", "耶", "哀", "结", "但", "何", "珥", "摩", "俄", "拿", "弥", "鸿", "哈", "番", "该", "亚", "玛", "太", "可", "路", "约", "徒", "罗", "林前", "林后", "加", "弗", "腓", "西", "帖前", "帖后", "提前", "提后", "多", "门", "来", "雅", "彼前", "彼后", "约一", "约二", "约三", "犹", "启")
    val english = listOf("Genesis", "Exodus", "Leviticus", "Numbers", "Deuteronomy", "Joshua", "Judges", "Ruth", "1 Samuel", "2 Samuel", "1 Kings", "2 Kings", "1 Chronicles", "2 Chronicles", "Ezra", "Nehemiah", "Esther", "Job", "Psalms", "Proverbs", "Ecclesiastes", "Song of Solomon", "Isaiah", "Jeremiah", "Lamentations", "Ezekiel", "Daniel", "Hosea", "Joel", "Amos", "Obadiah", "Jonah", "Micah", "Nahum", "Habakkuk", "Zephaniah", "Haggai", "Zechariah", "Malachi", "Matthew", "Mark", "Luke", "John", "Acts", "Romans", "1 Corinthians", "2 Corinthians", "Galatians", "Ephesians", "Philippians", "Colossians", "1 Thessalonians", "2 Thessalonians", "1 Timothy", "2 Timothy", "Titus", "Philemon", "Hebrews", "James", "1 Peter", "2 Peter", "1 John", "2 John", "3 John", "Jude", "Revelation")
    fun aliases(): Map<String, Int> = buildMap {
        names.forEachIndexed { i, name -> put(name, i + 1); put(shortNames[i], i + 1); put(english[i].lowercase(Locale.ROOT), i + 1) }
        put("列王记上", 11); put("列王记下", 12); put("启示録", 66); put("诗", 19)
    }
}

class TextNormalizer(private val traditionalMap: Map<Char, Char> = emptyMap()) {
    fun forSemantic(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)
        .map { traditionalMap[it] ?: it }.joinToString("")
    fun script(text: String): String = forSemantic(text).lowercase(Locale.ROOT)
    fun normalize(text: String): String = script(text).filter { it.isLetterOrDigit() }
    fun normalizeLatin(text: String): String = Normalizer.normalize(script(text), Normalizer.Form.NFKD)
        .replace("æ","ae").replace("œ","oe").filter { it.isLetterOrDigit() }
    fun normalizeArabic(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKD)
        .filter { it.isLetterOrDigit() && it != 'ـ' && it != 'ۥ' && it != 'ۦ' }.map { when (it) { 'ٱ', 'أ', 'إ', 'آ' -> 'ا'; 'ى' -> 'ي'; else -> it } }.joinToString("")
}

data class ReferenceQuery(val book: Int, val chapter: Int, val start: Int?, val end: Int?)
object ReferenceParser {
    private val pattern = Regex("^(.+?)\\s*(\\d+)\\s*(?:(?::|章)\\s*(\\d+)\\s*(?:[-–—]\\s*(\\d+))?\\s*(?:节)?)?\\s*(?:章)?$")
    fun parse(raw: String, normalizer: TextNormalizer, scripture: Scripture = Scripture.BIBLE, version: BibleVersion? = null): ReferenceQuery? {
        if(version?.isClassical == true) return Classics.parse(raw,normalizer,version)
        if(version == BibleVersion.VULGATE) return Vulgate.parse(raw,normalizer)
        if (scripture == Scripture.QURAN) return Quran.parse(raw, normalizer)
        if (scripture == Scripture.ANALECTS) return Analects.parse(raw, normalizer)
        if(scripture != Scripture.BIBLE) return null
        val m = pattern.matchEntire(normalizer.script(raw.trim())) ?: return null
        val name = m.groupValues[1].filterNot { it.isWhitespace() }
        val book = Books.aliases().entries.firstOrNull {
            normalizer.script(it.key).filterNot(Char::isWhitespace) == name
        }?.value ?: return null
        val chapter = m.groupValues[2].toIntOrNull() ?: return null
        val start = m.groupValues[3].takeIf { it.isNotEmpty() }?.toIntOrNull()
        val end = m.groupValues[4].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: start
        return ReferenceQuery(book, chapter, start, end)
    }
}
