package org.yanjing.app.core

import android.app.Application
import android.content.*
import android.os.*
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.yanjing.app.data.*
import org.yanjing.app.semantic.ISemanticEncoder
import java.io.RandomAccessFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class SemanticServiceTest {
    private class BindingContext(base: Context, private val binder: ISemanticEncoder, private val connect: Boolean=true) : ContextWrapper(base) {
        lateinit var connection: ServiceConnection
        var binds=0
        var unbinds=0
        override fun getApplicationContext(): Context = this
        override fun bindService(intent: Intent, conn: ServiceConnection, flags: Int): Boolean {
            binds++; connection=conn
            if (connect) conn.onServiceConnected(ComponentName(this,SemanticService::class.java),binder.asBinder())
            return true
        }
        override fun unbindService(conn: ServiceConnection) { unbinds++ }
    }
    @Test fun nativeProcessDeathKeepsKeywordSearchAndPreventsCrashLoop() = runBlocking {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val context=BindingContext(app,object : ISemanticEncoder.Stub() {
            override fun encode(text: String): FloatArray = throw DeadObjectException()
        })
        val remote=RemoteSemanticEncoder(context)
        val engine=BibleRepository(app).load(BibleVersion.CUVS)
        val search=HybridSearch(TextNormalizer()) { raw,_,_,_ -> remote.encode(raw); error("unreachable") }
        repeat(2) {
            val result=search.search("给我水喝",setOf(SearchMode.KEYWORD,SearchMode.SEMANTIC),engine,Testament.ALL)
            assertTrue(result.hits.isNotEmpty())
            assertTrue(result.hits.all { it.matchedModes==setOf(SearchMode.KEYWORD) })
            assertTrue(requireNotNull(result.message).contains("句意搜索暂时不可用"))
        }
        assertEquals(1,context.binds);assertEquals(1,context.unbinds)
        remote.close()
        assertEquals(1,context.unbinds)
    }
    @Test fun disconnectDisablesNativeServiceAndReleasesBinding() = runBlocking {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val context=BindingContext(app,object : ISemanticEncoder.Stub() {
            override fun encode(text: String) = FloatArray(512) { if (it==0) 1f else 0f }
        })
        val remote=RemoteSemanticEncoder(context)
        assertEquals(512,remote.encode("中文查询").size)
        context.connection.onServiceDisconnected(ComponentName(context,SemanticService::class.java))
        try { remote.encode("再次查询");fail("A dead native process must not restart in a loop") }
        catch (_: IllegalStateException) { }
        assertEquals(1,context.binds);assertEquals(1,context.unbinds)
        remote.close()
    }
    @Test fun cancelledQueryDoesNotBreakNextServiceConnection() = runBlocking {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val binder=object : ISemanticEncoder.Stub() {
            override fun encode(text: String) = FloatArray(512) { if (it==0) 1f else 0f }
        }
        val context=BindingContext(app,binder,connect=false)
        val remote=RemoteSemanticEncoder(context)
        val first=launch(start=CoroutineStart.UNDISPATCHED) { remote.encode("被取消的查询") }
        first.cancelAndJoin()
        assertTrue(first.isCancelled)
        context.connection.onServiceConnected(ComponentName(context,SemanticService::class.java),binder)
        assertEquals(512,remote.encode("新的查询").size)
        remote.close()
        assertEquals(1,context.binds);assertEquals(1,context.unbinds)
    }
    @Test fun sameLengthCorruptModelIsReplacedBeforeLoading() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val model=ModelFiles.prepare(app)
        val size=model.length()
        RandomAccessFile(model,"rw").use { it.seek(100); val byte=it.readUnsignedByte(); it.seek(100); it.writeByte(byte xor 0xff) }
        assertEquals(size,model.length())
        val repaired=ModelFiles.prepare(app)
        assertEquals(size,repaired.length())
        assertArrayEquals(app.assets.open("semantic/bge-small-zh-int8.onnx").use { it.readBytes() },repaired.readBytes())
    }
    @Test fun serviceIsPrivateAndUsesSeparateProcess() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val info=app.packageManager.getServiceInfo(ComponentName(app,SemanticService::class.java),0)
        assertFalse(info.exported)
        assertEquals(app.packageName+":semantic",info.processName)
    }
}
