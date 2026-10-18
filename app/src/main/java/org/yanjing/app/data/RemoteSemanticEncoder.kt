package org.yanjing.app.data

import android.content.*
import android.os.IBinder
import android.os.RemoteException
import kotlinx.coroutines.*
import org.yanjing.app.semantic.ISemanticEncoder

internal class RemoteSemanticEncoder(context: Context) : SemanticEncoder {
    private val app = context.applicationContext
    private val lock = Any()
    private val ready = CompletableDeferred<ISemanticEncoder>()
    private var bound = false
    private var closed = false
    @Volatile private var failed = false
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            ready.complete(ISemanticEncoder.Stub.asInterface(service))
        }
        override fun onServiceDisconnected(name: ComponentName) = fail()
        override fun onBindingDied(name: ComponentName) = fail()
        override fun onNullBinding(name: ComponentName) = fail()
    }
    private fun fail() {
        failed = true
        ready.completeExceptionally(IllegalStateException("句意搜索进程已停止，请重启应用后重试"))
        // Avoid Android automatically restarting a crashing native service in a loop.
        detach()
    }
    private fun detach() = synchronized(lock) {
        if (bound) {
            bound=false
            // The system may already have removed a dead binding.
            try { app.unbindService(connection) } catch (_: IllegalArgumentException) { }
        }
    }
    override suspend fun encode(text: String): FloatArray {
        synchronized(lock) {
            check(!closed && !failed) { "句意搜索暂时不可用，请重启应用后重试" }
            if (!bound) {
                bound = app.bindService(Intent(app,SemanticService::class.java),connection,Context.BIND_AUTO_CREATE)
                check(bound) { "无法连接句意搜索组件" }
            }
        }
        val remote = withTimeoutOrNull(15_000) { ready.await() } ?: run {
            fail(); throw IllegalStateException("连接句意搜索组件超时")
        }
        return withContext(Dispatchers.IO) {
            check(!failed) { "句意搜索进程已停止" }
            try {
                remote.encode(text).also { vector ->
                    check(vector.size == 512 && vector.all { it.isFinite() }) { "句意搜索组件返回无效结果" }
                }
            } catch (failure: RemoteException) {
                fail(); throw IllegalStateException("句意搜索进程已停止",failure)
            }
        }
    }
    override fun close() { synchronized(lock) { closed=true; detach(); ready.cancel() } }
}
