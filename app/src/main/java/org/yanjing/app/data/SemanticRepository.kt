package org.yanjing.app.data

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.yanjing.app.core.*
import org.yanjing.app.core.semantic.*
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

class SemanticRepository(context: Context, private val normalizer: TextNormalizer) {
    private val app = context.applicationContext
    private val mutex = Mutex()
    private var embedder: SentenceEmbedder? = null
    private val indexes = mutableMapOf<Scripture, SemanticIndex>()
    private val prefix = "为这个句子生成表示以用于检索相关文章："

    suspend fun search(raw: String, engine: SearchEngine, testament: Testament, limit: Int): SearchResponse = mutex.withLock {
        if (embedder == null) withContext(Dispatchers.IO) { initialize() }
        val index = indexes[engine.version.scripture] ?: withContext(Dispatchers.IO) {
            SemanticIndex(app.assets.open(if (engine.version.isQuran) "semantic/quran.i8" else "semantic/verses.i8"))
                .also { indexes[engine.version.scripture] = it }
        }
        withContext(Dispatchers.Default) {
            ensureActive()
            val vector = requireNotNull(embedder).encode(prefix + normalizer.forSemantic(raw))
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
    private fun initialize() {
        val manifest = JSONObject(app.assets.open("semantic/manifest.json").bufferedReader().use { it.readText() })
        val modelName = "bge-small-zh-int8.onnx"
        val digest = manifest.getJSONObject("files").getJSONObject(modelName).getString("sha256")
        val directory = File(app.noBackupFilesDir, "models").apply { mkdirs() }
        val model = File(directory, "bge-${digest.take(16)}.onnx")
        if (!model.isFile || model.length() != manifest.getJSONObject("files").getJSONObject(modelName).getLong("bytes")) {
            val temporary = File(directory, "model.partial")
            app.assets.open("semantic/$modelName").use { input -> temporary.outputStream().use { input.copyTo(it) } }
            val sha = MessageDigest.getInstance("SHA-256")
            temporary.inputStream().use { stream -> val buffer = ByteArray(65536); var read = stream.read(buffer)
                while (read >= 0) { sha.update(buffer,0,read); read = stream.read(buffer) } }
            require(sha.digest().joinToString("") { "%02x".format(it) } == digest) { "语义模型校验失败" }
            check(temporary.renameTo(model)) { "无法保存语义模型" }
        }
        val tokenizer = WordPieceTokenizer(app.assets.open("semantic/vocab.txt").bufferedReader().use { it.readLines() })
        val loadedEmbedder = SentenceEmbedder(model.absolutePath, tokenizer)
        embedder = loadedEmbedder
    }
    fun close() {
        // Cleanup waits off the main thread for an in-flight native inference to finish.
        val cleanup = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        cleanup.launch { try { mutex.withLock { embedder?.close(); embedder = null; indexes.clear() } } finally { cleanup.cancel() } }
    }
}
