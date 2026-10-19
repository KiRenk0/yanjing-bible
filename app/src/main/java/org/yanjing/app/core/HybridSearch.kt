package org.yanjing.app.core

import kotlinx.coroutines.CancellationException

/** Reciprocal rank fusion combines unlike scores without treating them as probabilities. */
class HybridSearch(
    private val normalizer: TextNormalizer,
    private val semantic: suspend (String, SearchEngine, Testament, Int) -> SearchResponse
) {
    suspend fun search(raw: String, modes: Set<SearchMode>, engine: SearchEngine, testament: Testament, limit: Int = 60): SearchResponse {
        if (raw.isBlank()) return SearchResponse(emptyList(), 0)
        if (raw.length > 200) return SearchResponse(emptyList(), 0, "请输入 200 字以内的内容。")
        if (normalizer.normalize(raw).isEmpty()) return SearchResponse(emptyList(), 0, "请输入文字、关键词或经文出处。")
        if (modes.isEmpty()) return SearchResponse(emptyList(), 0, "请至少选择一种搜索模式。")
        if (ReferenceParser.parse(raw, normalizer, engine.version.scripture) != null) return engine.search(raw, SearchMode.KEYWORD, testament, limit)
        val responses = mutableListOf<Pair<SearchMode, SearchResponse>>()
        val failures = mutableListOf<String>()
        // Run lighter searches first; the CPU model is shared and serialized by its repository.
        for (mode in modes.sortedBy { it == SearchMode.SEMANTIC }) {
            try {
                val response = if (mode == SearchMode.SEMANTIC) semantic(raw, engine, testament, limit)
                    else engine.search(raw, mode, testament, limit)
                responses += mode to response
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: LinkageError) { failures += "${mode.title}的本地组件无法加载" }
            catch (_: Exception) { failures += "${mode.title}暂时不可用" }
        }
        if (responses.isEmpty()) return SearchResponse(emptyList(), 0, failures.joinToString("；") + "，请重试。")
        if (responses.size == 1) {
            val (mode, response) = responses.single()
            return response.copy(hits = response.hits.map { it.copy(matchedModes = setOf(mode)) },
                message = if (failures.isEmpty()) response.message else failures.joinToString("；") + "。当前结果来自${mode.title}。")
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
                hit.reason !in setOf("常用表达关联", "原文包含关键词")) return@forEachIndexed
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
        return SearchResponse(hits, fused.size, failures.takeIf { it.isNotEmpty() }?.joinToString("；")?.plus("，已合并其他模式的结果。")
            ?: if (hits.isEmpty()) semanticResponse?.message else null)
    }
}
