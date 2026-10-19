package org.yanjing.app.core.semantic

import org.yanjing.app.core.*
import kotlin.math.ln

data class RankedSemanticCandidate(val key: VerseKey, val score: Double, val cosine: Float)

/** Second-stage evidence over canonical Chinese text, glosses and existing expression hints.
 * No additional model or query-specific verse rules. Scores are ranking values, not probabilities.
 */
class SemanticRanker(verses: List<Verse>, private val normalizer: TextNormalizer, groups: List<List<String>>,
    hints: List<ExpressionGroup> = emptyList()) {
    private val concepts = groups.map { group -> group.map(normalizer::normalize) }
    private val replacements = concepts.flatMap { group -> group.map { it to group.first() } }.toMap()
    private val pattern = Regex(replacements.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) })
    private val stop = "的了是我你他她它们在有和与及就也都这那吗呢吧啊呀不着把被个地得为以而其之乎者也要能会应该不能一个自己别人什么怎么为什么是否时候".toSet()
    private data class Window(val text: String, val concepts: Long)
    private data class Document(val key: VerseKey, val windows: List<Window>)
    private val documents: List<Document>
    private val byKey: Map<VerseKey, Document>
    private val charPostings: Map<Char, IntArray>
    private val conceptPostings: Array<IntArray>
    private val idf: Map<Char, Double>
    private val unseen: Double

    init {
        require(concepts.isNotEmpty() && concepts.size <= 63 && concepts.all { it.isNotEmpty() && it.all(String::isNotEmpty) })
        val phrases = mutableMapOf<VerseKey, MutableList<String>>()
        hints.forEach { group -> group.references.forEach { key -> phrases.getOrPut(key) { mutableListOf() }.addAll(group.phrases) } }
        documents = verses.map { verse ->
            val texts = listOf(verse.text, verse.searchGloss).filter(String::isNotEmpty) + phrases[verse.key].orEmpty()
            val windows = texts.flatMap { text ->
                val normalized = normalizer.normalize(text)
                (normalized.indices step 120).map { i ->
                    val part = normalized.substring(i, minOf(i + 180, normalized.length))
                    Window(fold(part), conceptMask(part))
                }
            }
            Document(verse.key, windows)
        }
        byKey = documents.associateBy { it.key }
        val postingBuilder = mutableMapOf<Char, MutableList<Int>>()
        val conceptBuilder = Array(concepts.size) { mutableListOf<Int>() }
        documents.forEachIndexed { index, document ->
            document.windows.flatMap { it.text.toList() }.toSet().forEach { postingBuilder.getOrPut(it) { mutableListOf() }.add(index) }
            val mask = document.windows.fold(0L) { bits, window -> bits or window.concepts }
            concepts.indices.filter { (mask and (1L shl it)) != 0L }.forEach { conceptBuilder[it].add(index) }
        }
        charPostings = postingBuilder.mapValues { (_, rows) -> rows.toIntArray() }
        conceptPostings = Array(concepts.size) { conceptBuilder[it].toIntArray() }
        idf = charPostings.mapValues { (_, rows) -> ln(1.0 + documents.size.toDouble() / (1 + rows.size)) }
        unseen = ln(1.0 + documents.size)
    }

    private fun fold(text: String) = pattern.replace(text) { replacements.getValue(it.value) }
    private fun conceptMask(text: String): Long = concepts.indices.fold(0L) { bits, i ->
        if (concepts[i].any(text::contains)) bits or (1L shl i) else bits
    }
    private data class Query(val chars: Map<Char,Double>, val grams: Map<String,Double>, val concepts: Long) {
        val charTotal = chars.values.sum().coerceAtLeast(1e-9)
        val gramTotal = grams.values.sum().coerceAtLeast(1e-9)
        val conceptTotal = java.lang.Long.bitCount(concepts).coerceAtLeast(1)
    }
    private fun query(raw: String): Query {
        val normalized = normalizer.normalize(raw)
        val folded = fold(normalized)
        val chars = folded.toSet().filterNot { it in stop }.associateWith { idf[it] ?: unseen }
        val grams = folded.windowed(2).distinct().filter { gram -> gram.any { it !in stop } }
            .associateWith { gram -> gram.sumOf { idf[it] ?: unseen } / 2 }
        return Query(chars, grams, conceptMask(normalized))
    }
    private fun evidence(query: Query, document: Document): Double = document.windows.maxOfOrNull { window ->
        val chars = query.chars.entries.sumOf { (char,weight) -> if (char in window.text) weight else 0.0 } / query.charTotal
        val grams = query.grams.entries.sumOf { (gram,weight) -> if (gram in window.text) weight else 0.0 } / query.gramTotal
        val concepts = java.lang.Long.bitCount(query.concepts and window.concepts).toDouble() / query.conceptTotal
        0.08 * chars + 0.13 * grams + 0.12 * concepts
    } ?: 0.0

    /** Lexical recall supplies candidates that a single embedding can otherwise miss. */
    fun seeds(raw: String, limit: Int = 32, checkpoint: () -> Unit = {}): Set<VerseKey> {
        val query = query(raw)
        val rare = query.chars.keys.filter { it in charPostings }.sortedWith(compareBy<Char> { charPostings.getValue(it).size }.thenBy { it }).take(2)
        val possible = linkedSetOf<Int>()
        rare.forEach { charPostings.getValue(it).forEach { row -> possible.add(row) } }
        concepts.indices.filter { (query.concepts and (1L shl it)) != 0L }.forEach { conceptPostings[it].forEach { row -> possible.add(row) } }
        return possible.mapIndexed { i, row ->
            if (i % 128 == 0) checkpoint()
            documents[row].let { it.key to evidence(query,it) }
        }.sortedWith(compareByDescending<Pair<VerseKey,Double>> { it.second }.thenBy { it.first.encoded })
            .take(limit).filter { it.second >= 0.1 }.map { it.first }.toSet()
    }

    fun rank(raw: String, candidates: List<SemanticCandidate>, limit: Int = 60): List<RankedSemanticCandidate> {
        require(limit > 0)
        val query = query(raw)
        data class Ranked(val candidate: SemanticCandidate, val score: Double)
        val scored = candidates.mapNotNull { candidate -> byKey[candidate.key]?.let {
            // Lexical influence is bounded to 0.165; semantic similarity remains primary.
            Ranked(candidate, candidate.cosine + 0.5 * evidence(query,it))
        } }.sortedWith(compareByDescending<Ranked> { it.score }.thenBy { it.candidate.key.encoded })
        val best = scored.firstOrNull()?.score ?: return emptyList()
        // An absolute floor rejects weak matches; the relative floor removes long, irrelevant tails.
        return scored.filter { it.candidate.cosine >= 0.42f && it.score >= maxOf(0.5, best - 0.15) }
            .take(limit).map { RankedSemanticCandidate(it.candidate.key,it.score,it.candidate.cosine) }
    }
}
