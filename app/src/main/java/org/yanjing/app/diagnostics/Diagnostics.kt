package org.yanjing.app.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.os.Build
import android.system.Os
import android.system.OsConstants
import androidx.core.content.FileProvider
import org.yanjing.app.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/** Local-only crash evidence. Never reads or uploads scripture queries, history or favorites. */
object Diagnostics {
    private fun directory(context: Context) = File(context.noBackupFilesDir,"diagnostics").apply { mkdirs() }
    internal fun record(context: Context,thread: Thread,failure: Throwable) {
        val stack = StringWriter().also { failure.printStackTrace(PrintWriter(it)) }.toString().take(48_000)
        val process = if (thread.name.isBlank()) "unknown" else thread.name.take(100)
        File(directory(context),"crash-${android.os.Process.myPid()}.txt").writeText(
            "version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\ntime=${System.currentTimeMillis()}\nthread=$process\n$stack")
        directory(context).listFiles()?.sortedByDescending { it.lastModified() }?.drop(4)?.forEach { it.delete() }
    }
    internal fun report(context: Context): String = buildString {
        appendLine("言·经 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}; Android=${Build.VERSION.RELEASE}; API=${Build.VERSION.SDK_INT}")
        appendLine("build=${Build.DISPLAY}; fingerprint=${Build.FINGERPRINT}; targetSdk=${context.applicationInfo.targetSdkVersion}")
        appendLine("ABIs=${Build.SUPPORTED_ABIS.joinToString()}; pageSize=${runCatching { Os.sysconf(OsConstants._SC_PAGESIZE) }.getOrDefault(-1)}")
        appendLine("ONNX Runtime=1.25.1; inferenceProcess=:semantic")
        if (Build.VERSION.SDK_INT >= 30) runCatching {
            context.getSystemService(ActivityManager::class.java).getHistoricalProcessExitReasons(context.packageName,0,8).forEach {
                appendLine("exit: time=${it.timestamp}; process=${it.processName}; reason=${it.reason}; status=${it.status}; description=${it.description}")
            }
        }.onFailure { appendLine("System exit records unavailable: ${it.javaClass.simpleName}") }
        directory(context).listFiles()?.sortedByDescending { it.lastModified() }?.take(4)?.forEach {
            appendLine("\n${it.name}\n${it.readText().take(50_000)}")
        }
    }
    fun shareIntent(context: Context): Intent {
        val directory = File(context.cacheDir,"diagnostics").apply { mkdirs() }
        val report = File(directory,"yanjing-diagnostics.txt").apply { writeText(report(context)) }
        val uri = FileProvider.getUriForFile(context,"${context.packageName}.updates",report)
        return Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM,uri).apply { clipData=ClipData.newRawUri("本机诊断",uri) }
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),"导出本机诊断")
    }
}
