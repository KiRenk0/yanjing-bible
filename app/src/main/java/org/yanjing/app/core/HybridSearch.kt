package org.yanjing.app.core

import org.yanjing.app.i18n.tr
import org.yanjing.app.R

import kotlinx.coroutines.CancellationException

/** Reciprocal rank fusion combines unlike scores without treating them as probabilities. */
class HybridSearch(
    private val normalizer: TextNormalizer,
    private val semantic: suspend (String, SearchEngine, Testament, Int) -> SearchResponse
) {
    suspend fun search(raw: String, modes: Set<SearchMode>, engine: SearchEngine, testament: Testament, limit: Int = 60): SearchResponse {
        if (raw.isBlank()) return SearchResponse(emptyList(), 0)
        if (raw.length > 200) return SearchResponse(emptyList(), 0, tr(R.string.ui_7bc94376a0))
        if (normalizer.normalize(raw).isEmpty()) return SearchResponse(emptyList(), 0, tr(R.string.ui_36231598c1))
        if (modes.isEmpty()) return SearchResponse(emptyList(), 0, tr(R.string.ui_afeebd4e36))
        if (ReferenceParser.parse(raw, normalizer, engine.version.scripture, engine.version) != null) return engine.search(raw, SearchMode.KEYWORD, testament, limit)
        val responses = mutableListOf<Pair<SearchMode, SearchResponse>>()
        val failures = mutableListOf<String>()
        // Run lighter searches first; the CPU model is shared and serialized by its repository.
        for (mode in modes.sortedBy { it == SearchMode.SEMANTIC }) {
            try {
                val response = if (mode == SearchMode.SEMANTIC) semantic(raw, engine, testament, limit)
                    else engine.search(raw, mode, testament, limit)
                responses += mode to response
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: LinkageError) { failures += tr(R.string.ui_42f9253143, mode.title) }
            catch (_: Exception) { failures += tr(R.string.ui_abb884d68e, mode.title) }
        }
        if (responses.isEmpty()) return SearchResponse(emptyList(), 0, failures.joinToString("；") + tr(R.string.ui_48df8db6cd))
        if (responses.size == 1) {
            val (mode, response) = responses.single()
            return response.copy(hits = response.hits.map { it.copy(matchedModes = setOf(mode)) },
                message = if (failures.isEmpty()) response.message else failures.joinToString("；") + tr(R.string.ui_6a03e11949, mode.title))
        }
        val exactExpressionKeys = ExpressionLexicon.forVersion(engine.version).filter { group -> group.phrases.any { normalizer.normalize(it) == normalizer.normalize(raw) } }
            .flatMap { it.references }.map { VerseMappings.resolve(engine.version, it) }.toSet()
        val semanticResponse = responses.firstOrNull { it.first == SearchMode.SEMANTIC }?.second
        val semanticKeys = semanticResponse?.hits?.map { it.verse.key }?.toSet()
        data class Fused(val verse: Verse, var score: Double, val modes: MutableSet<SearchMode>, var priority: Int = 0, var semanticScore: Double? = null)
        val fused = linkedMapOf<VerseKey, Fused>()
        for ((mode, response) in responses) response.hits.forEachIndexed { rank, hit ->
            // A weak expression overlap must not reintroduce candidates rejected by semantic search.
            // If the native service failed, the independent expression fallback remains available.
            if (mode == SearchMode.EXPRESSION && semanticKeys != null && hit.verse.key !in semanticKeys &&
                hit.reason !in setOf(tr(R.string.ui_2ded27fb5c), tr(R.string.ui_e51850cd4b))) return@forEachIndexed
            val item = fused.getOrPut(hit.verse.key) { Fused(hit.verse, 0.0, linkedSetOf()) }
            val weight = when (mode) { SearchMode.SEMANTIC -> 1.2; SearchMode.KEYWORD -> 1.1; else -> 1.0 }
            item.score += weight / (40.0 + rank + 1)
            item.modes += mode
            if (mode == SearchMode.SEMANTIC) item.semanticScore = hit.score
            item.priority = maxOf(item.priority, when {
                mode == SearchMode.KEYWORD -> 2
                mode == SearchMode.EXPRESSION && hit.verse.key in exactExpressionKeys -> 1
                else -> 0
            })
        }
        fun combined(item: Fused) = if (semanticResponse == null) item.score else (item.semanticScore ?: 0.0) + 0.02 * item.score
        val hits = fused.values.sortedWith(compareByDescending<Fused> { it.priority }.thenByDescending(::combined)).take(limit).map { item ->
            SearchHit(item.verse, combined(item), item.modes.sortedBy { it.ordinal }.joinToString(" · ") { it.title }, item.modes.toSet())
        }
        return SearchResponse(hits, fused.size, failures.takeIf { it.isNotEmpty() }?.joinToString("；")?.plus(tr(R.string.ui_5adfc173a2))
            ?: if (hits.isEmpty()) semanticResponse?.message else null)
    }
}
