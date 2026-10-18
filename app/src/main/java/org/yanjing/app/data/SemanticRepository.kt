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
    private val indexes = mutableMapOf<Scripture, SemanticIndex>()
    private val prefix = "为这个句子生成表示以用于检索相关文章："

    suspend fun search(raw: String, engine: SearchEngine, testament: Testament, limit: Int): SearchResponse = mutex.withLock {
        val index = indexes[engine.version.scripture] ?: withContext(Dispatchers.IO) {
            SemanticIndex(app.assets.open(if (engine.version.isQuran) "semantic/quran.i8" else "semantic/verses.i8"))
                .also { indexes[engine.version.scripture] = it }
        }
        withContext(Dispatchers.Default) {
            ensureActive()
            val vector = encoder.encode(prefix + normalizer.forSemantic(raw))
            ensureActive()
            val job = currentCoroutineContext().job
            val candidates = index.nearest(vector, limit,
                accepts = { testament.accepts(it.book) && engine.verse(it) != null }, checkpoint = { job.ensureActive() })
            // An empirical floor suppresses unrelated matches; it is not a probability.
            val hits = candidates.filter { it.cosine >= 0.5f }.mapNotNull { candidate ->
                engine.verse(candidate.key)?.let { SearchHit(it, candidate.cosine.toDouble(), "句意相近", setOf(SearchMode.SEMANTIC)) }
            }.distinctBy { it.verse.key }
            SearchResponse(hits, hits.size)
        }
    }
    fun close() {
        // Cleanup waits off the main thread for an in-flight native inference to finish.
        val cleanup = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        cleanup.launch { try { mutex.withLock { encoder.close(); indexes.clear() } } finally { cleanup.cancel() } }
    }
}
