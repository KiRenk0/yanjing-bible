package org.yanjing.app.core

import kotlin.math.ln
import kotlin.math.min

/** Offline character n-gram retrieval, with explicit, reviewable expression expansions. */
class SearchEngine(val verses: List<Verse>, private val normalizer: TextNormalizer, val version: BibleVersion = BibleVersion.CUVS) {
    private val byKey = verses.associateBy { it.key }
    private val canonical = verses.flatMap { verse -> verse.canonicalKeys.map { it to verse } }.groupBy({ it.first },{ it.second })
    private val retrievalText = verses.associate { it.key to (it.normalized + normalizer.normalize(it.searchGloss)) }
    private val charFrequency: Map<Char, Int> = buildMap {
        retrievalText.values.forEach { text -> text.toSet().forEach { put(it, (get(it) ?: 0) + 1) } }
    }
    private val stop = "的了是我你他她它们在有和与及就也都这那吗呢吧啊呀不着把被个地得为以而其之乎者也".toSet()

    fun verse(key: VerseKey): Verse? = byKey[key] ?: byKey[VerseMappings.resolve(version, key)] ?: canonical[key]?.firstOrNull()
    fun versesForSemanticKey(key: VerseKey): List<Verse> = canonical[key] ?: listOfNotNull(verse(key))
    fun chapter(book: Int, chapter: Int): List<Verse> = verses.filter { it.key.book == book && it.key.chapter == chapter }
    fun chapters(book: Int): Int = verses.asSequence().filter { it.key.book == book }.maxOfOrNull { it.key.chapter } ?: 0

    fun search(raw: String, mode: SearchMode = SearchMode.EXPRESSION, testament: Testament = Testament.ALL, limit: Int = 60): SearchResponse {
        if (raw.isBlank()) return SearchResponse(emptyList(), 0)
        if (raw.length > 200) return SearchResponse(emptyList(), 0, "请输入 200 字以内的内容。")
        ReferenceParser.parse(raw, normalizer, version.scripture, version)?.let { reference ->
            val chapter = chapter(reference.book, reference.chapter)
            if (chapter.isEmpty() || reference.chapter <= 0 ||
                (reference.start != null && (reference.start <= 0 || reference.end!! < reference.start ||
                    verse(VerseKey(reference.book, reference.chapter, reference.start)) == null || verse(VerseKey(reference.book, reference.chapter, reference.end)) == null))) {
                return SearchResponse(emptyList(), 0, "此版本中没有这个章节范围，请检查章号和节号。")
            }
            val selected = if (reference.start == null) chapter else (reference.start..reference.end!!)
                .mapNotNull { verse(VerseKey(reference.book, reference.chapter, it)) }.distinctBy { it.key }
            val merged = reference.start != null && (reference.start..reference.end!!).any {
                val key = VerseKey(reference.book, reference.chapter, it)
                VerseMappings.resolve(version, key) != key
            }
            val result = selected.filter { testament.accepts(it.key.book) }
                .map { SearchHit(it, 100.0, if (merged) "出处定位 · 此版合并分节" else "出处定位") }
            return SearchResponse(result.take(limit), result.size)
        }
        val query = if (version.isRtl) normalizer.normalizeArabic(raw) else if(version==BibleVersion.VULGATE) normalizer.normalizeLatin(raw) else normalizer.normalize(raw)
        if (query.isEmpty()) return SearchResponse(emptyList(), 0, "请输入文字、关键词或经文出处。")
        if (mode == SearchMode.SEMANTIC) return SearchResponse(emptyList(), 0, "句意搜索需要语义检索组件。")
        if (mode == SearchMode.KEYWORD) {
            val result = verses.asSequence().filter { testament.accepts(it.key.book) && it.normalized.contains(query) }
                .map { SearchHit(it, 100.0, "原文包含关键词") }.toList()
            return SearchResponse(result.take(limit), result.size)
        }
        val expanded = mutableMapOf<VerseKey, Double>()
        ExpressionLexicon.forVersion(version).forEach { group ->
            val match = group.phrases.maxOf { phrase ->
                val p = normalizer.normalize(phrase)
                when {
                    query == p -> 130.0
                    query.contains(p) && p.length >= 2 -> 100.0 + min(p.length, 12)
                    p.contains(query) && query.length >= 3 -> 65.0
                    else -> 0.0
                }
            }
            if (match > 0) group.references.forEachIndexed { i, requested ->
                versesForSemanticKey(requested).forEach { actual ->
                    expanded[actual.key] = maxOf(expanded[actual.key] ?: 0.0, match - i * 1.5)
                }
            }
        }
        val grams = query.windowed(2).distinct()
        val chars = query.toSet().filter { it !in stop }.ifEmpty { query.toSet().toList() }
        val weights = chars.associateWith { ln(1.0 + verses.size.toDouble() / (1 + (charFrequency[it] ?: 0))) }
        val totalWeight = weights.values.sum().coerceAtLeast(1.0)
        val results = verses.asSequence().filter { testament.accepts(it.key.book) }.mapNotNull { verse ->
            val text = retrievalText.getValue(verse.key)
            val expansion = expanded[verse.key] ?: 0.0
            val exact = verse.normalized.contains(query)
            val matching = chars.filter { text.contains(it) }
            val coverage = matching.sumOf { weights[it] ?: 0.0 } / totalWeight
            val gramCoverage = if (grams.isEmpty()) 0.0 else grams.count { text.contains(it) }.toDouble() / grams.size
            if (expansion == 0.0 && !exact && (matching.size < min(2, chars.size) || coverage < 0.45 || (gramCoverage == 0.0 && coverage < 0.85))) return@mapNotNull null
            val score = expansion + (if (exact) 80.0 else 0.0) + 22 * coverage + 35 * gramCoverage +
                4.0 * query.length / text.length.coerceAtLeast(query.length)
            SearchHit(verse, score, when {
                expansion > 0 -> "常用表达关联"
                exact -> "原文包含关键词"
                verse.searchGloss.isNotEmpty() -> "原文与检索释义用词相近"
                else -> "用词相近"
            })
        }.sortedWith(compareByDescending<SearchHit> { it.score }.thenBy { it.verse.key.book }.thenBy { it.verse.key.chapter }.thenBy { it.verse.key.verse }).toList()
        return SearchResponse(results.take(limit), results.size)
    }
}
