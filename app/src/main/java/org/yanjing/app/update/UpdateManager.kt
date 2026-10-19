package org.yanjing.app.update

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.work.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.TimeUnit
import org.yanjing.app.diagnostics.Diagnostics

class UpdateManager(context: Context, private val workManager: (Context) -> WorkManager = { WorkManager.getInstance(it) }) : AutoCloseable {
    private val app = context.applicationContext
    private val store = UpdateStore(app)
    private val mutable = MutableStateFlow(store.read())
    val state = mutable.asStateFlow()
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> mutable.value = store.read() }
    init { store.preferences.registerOnSharedPreferenceChangeListener(listener) }

    private fun safelySchedule(action: () -> Unit) {
        try { action() }
        catch (failure: Exception) { schedulingFailed(failure) }
        catch (failure: LinkageError) { schedulingFailed(failure) }
    }
    private fun schedulingFailed(failure: Throwable) {
        // Background updates are optional; scheduler/ROM errors must not close the reader.
        runCatching { Diagnostics.record(app, Thread.currentThread(), failure) }
        store.status(UpdatePhase.ERROR, "更新组件暂时不可用，可稍后重试或前往 GitHub 下载。")
    }
    fun start() = safelySchedule {
        if (state.value.autoCheck) {
            val request = PeriodicWorkRequestBuilder<CheckUpdateWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            workManager(app).enqueueUniquePeriodicWork("update-periodic", ExistingPeriodicWorkPolicy.KEEP, request)
            if (System.currentTimeMillis() - state.value.lastChecked > TimeUnit.HOURS.toMillis(6)) check()
        }
    }
    fun check() {
        if (state.value.phase in setOf(UpdatePhase.DOWNLOADING, UpdatePhase.READY)) return
        safelySchedule {
            store.status(UpdatePhase.CHECKING, "正在检查 GitHub Release…")
            val request = OneTimeWorkRequestBuilder<CheckUpdateWorker>().setInputData(workDataOf("manual" to true))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            workManager(app).enqueueUniqueWork("update-check", ExistingWorkPolicy.KEEP, request)
        }
    }
    fun download() { safelySchedule { enqueueDownload(app, automatic = false) } }
    fun dismiss() { store.dismiss() }
    fun option(key: String, value: Boolean) {
        require(key in setOf("autoCheck", "autoDownload", "wifiOnly"))
        store.preferences.edit().putBoolean(key, value).apply()
        safelySchedule {
            if (key == "autoCheck") {
                if (value) start() else workManager(app).cancelUniqueWork("update-periodic")
            }
            if (key == "autoDownload" || key == "wifiOnly") {
                workManager(app).cancelAllWorkByTag("update-automatic-download")
                if (state.value.autoDownload && state.value.release != null && state.value.phase != UpdatePhase.READY) enqueueDownload(app, true)
            }
        }
    }
    fun route(value: DownloadRoute) { store.preferences.edit().putString("route", value.name).apply() }
    /** Invoked after verification off the main thread; installation always uses Android's UI. */
    fun installIntent(): Intent {
        val release = requireNotNull(store.read().release)
        if (!app.packageManager.canRequestPackageInstalls()) {
            return Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
        }
        val file = apkFile(app, release)
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.updates", file)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    fun verifyReady() { val release = requireNotNull(store.read().release); ApkVerifier.verify(app, apkFile(app, release), release) }
    override fun close() { store.preferences.unregisterOnSharedPreferenceChangeListener(listener) }

    companion object {
        internal fun directory(context: Context) = File(context.filesDir, "updates").apply { mkdirs() }
        internal fun apkFile(context: Context, release: ReleaseInfo) = File(directory(context), "yanjing-${release.versionCode}.apk")
        internal fun enqueueDownload(context: Context, automatic: Boolean) {
            val store = UpdateStore(context); val state = store.read()
            if (state.release == null || state.phase == UpdatePhase.READY) return
            val request = OneTimeWorkRequestBuilder<DownloadUpdateWorker>().setInputData(workDataOf("automatic" to automatic))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(if (automatic && state.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .apply { if (automatic) addTag("update-automatic-download") }.build()
            store.status(UpdatePhase.AVAILABLE, if (automatic && state.wifiOnly) "发现新版，等待 Wi-Fi 自动下载" else "发现新版，等待网络下载")
            WorkManager.getInstance(context).enqueueUniqueWork("update-download", if (automatic) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE, request)
        }
    }
}
