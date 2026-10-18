package org.yanjing.app.data

import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.*
import org.yanjing.app.core.semantic.SentenceEmbedder
import org.yanjing.app.core.semantic.WordPieceTokenizer
import org.yanjing.app.semantic.ISemanticEncoder

/** A native signal kills this process, leaving search, reading and updates in the UI alive. */
class SemanticService : Service() {
    private val lock = Any()
    private var encoder: SentenceEmbedder? = null
    private val binder = object : ISemanticEncoder.Stub() {
        override fun encode(text: String): FloatArray = synchronized(lock) {
            require(text.length in 1..256) { "检索文本长度有误" }
            try {
                val loaded = encoder ?: SentenceEmbedder(ModelFiles.prepare(this@SemanticService).absolutePath,
                    WordPieceTokenizer(assets.open("semantic/vocab.txt").bufferedReader().use { it.readLines() }))
                    .also { encoder = it }
                loaded.encode(text)
            } catch (_: LinkageError) { throw IllegalStateException("设备无法加载句意搜索组件") }
            catch (_: Exception) { throw IllegalStateException("句意模型暂时无法运行") }
        }
    }
    override fun onBind(intent: Intent): IBinder = binder
    override fun onDestroy() {
        // Never block the service main thread while an in-flight native call holds the lock.
        val cleanup = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        cleanup.launch { try { synchronized(lock) { encoder?.close(); encoder=null } } finally { cleanup.cancel() } }
        super.onDestroy()
    }
}
