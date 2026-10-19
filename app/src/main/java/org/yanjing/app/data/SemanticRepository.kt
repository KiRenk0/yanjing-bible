package org.yanjing.app.data

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.yanjing.app.core.*
import org.yanjing.app.core.semantic.*

class SemanticRepository(context: Context, private val normalizer: TextNormalizer,
    private val encoder: SemanticEncoder = RemoteSemanticEncoder(context)) {
    private val app = context.applicationContext
    private val mutex = Mutex()
    private data class Retrieval(val index: SemanticIndex, val ranker: SemanticRanker)
    private val indexes = mutableMapOf<BibleVersion, Retrieval>()
    private val prefix = "为这个句子生成表示以用于检索相关文章："

    suspend fun search(raw: String, engine: SearchEngine, testament: Testament, limit: Int): SearchResponse = mutex.withLock {
        val canonicalVersion = when(engine.version.scripture) { Scripture.BIBLE -> BibleVersion.CUVS; Scripture.QURAN -> BibleVersion.QURAN_ZH; else -> engine.version }
        val retrieval = indexes[canonicalVersion] ?: withContext(Dispatchers.IO) {
            val asset = "semantic/" + when(canonicalVersion) { BibleVersion.CUVS -> "verses.i8"; BibleVersion.QURAN_ZH -> "quran.i8"; else -> "${canonicalVersion.id}.i8" }
            val verses = if (engine.version == canonicalVersion) engine.verses else BibleRepository(app).load(canonicalVersion).verses
            val groups = app.assets.open("semantic/concepts.txt").bufferedReader().useLines { lines ->
                lines.filter { it.isNotBlank() && !it.startsWith('#') }.map { it.split('|') }.toList()
            }
            Retrieval(SemanticIndex(app.assets.open(asset)), SemanticRanker(verses, normalizer, groups, ExpressionLexicon.forVersion(canonicalVersion)))
                .also {
                    // Keep one corpus's lexical postings resident on memory-constrained phones.
                    indexes.clear()
                    indexes[canonicalVersion] = it
                }
        }
        withContext(Dispatchers.Default) {
            ensureActive()
            val vector = encoder.encode(prefix + normalizer.forSemantic(raw))
            ensureActive()
            val job = currentCoroutineContext().job
            val seeds = retrieval.ranker.seeds(raw, checkpoint = { job.ensureActive() })
            val candidates = retrieval.index.nearest(vector, maxOf(120,limit),
                accepts = { testament.accepts(it.book) && engine.verse(it) != null }, checkpoint = { job.ensureActive() }, include = seeds)
            val ranked = retrieval.ranker.rank(raw,candidates,limit)
            val hits = ranked.flatMap { candidate ->
                engine.versesForSemanticKey(candidate.key).map { SearchHit(it, candidate.score, "句意相近", setOf(SearchMode.SEMANTIC)) }
            }.distinctBy { it.verse.key }.take(limit)
            SearchResponse(hits, hits.size, if (hits.isEmpty()) "未找到足够相关的原文，请换一个更具体的说法。" else null)
        }
    }
    fun close() {
        // Cleanup waits off the main thread for an in-flight native inference to finish.
        val cleanup = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        cleanup.launch { try { mutex.withLock { encoder.close(); indexes.clear() } } finally { cleanup.cancel() } }
    }
}
