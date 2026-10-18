package org.yanjing.app.update

import android.content.Context
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.*
import org.yanjing.app.BuildConfig
import java.io.File

class CheckUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val store = UpdateStore(applicationContext)
        if (!inputData.getBoolean("manual", false) && !store.read().autoCheck) return@withContext Result.success()
        // Checking must never interrupt or hide an ongoing download.
        if (store.read().phase in setOf(UpdatePhase.DOWNLOADING, UpdatePhase.READY)) return@withContext Result.success()
        store.status(UpdatePhase.CHECKING, "正在检查 GitHub Release…")
        for (url in UpdatePolicy.urls(UpdatePolicy.MANIFEST_URL, store.read().route)) {
            try {
                val release = releaseFromJson(UpdateTransport().manifest(url))
                store.checked()
                if (store.read().phase in setOf(UpdatePhase.DOWNLOADING, UpdatePhase.READY)) return@withContext Result.success()
                if (release.versionCode <= BuildConfig.VERSION_CODE) {
                    store.status(UpdatePhase.IDLE, "已是最新版本 ${BuildConfig.VERSION_NAME}")
                } else if (release.minSdk > Build.VERSION.SDK_INT) {
                    store.status(UpdatePhase.IDLE, "新版需要 Android API ${release.minSdk}，当前系统暂不支持")
                } else {
                    store.release(release)
                    store.status(UpdatePhase.AVAILABLE, "发现新版本 ${release.versionName}")
                    if (store.read().autoDownload) UpdateManager.enqueueDownload(applicationContext, true)
                }
                return@withContext Result.success()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Try the next configured route, preserving the same release identity. */ }
        }
        store.status(UpdatePhase.ERROR, "暂时无法检查更新，请换条线路或稍后重试")
        Result.failure()
    }
}

class DownloadUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext; val store = UpdateStore(app)
        val release = store.read().release ?: return@withContext Result.success()
        if (inputData.getBoolean("automatic", false) && !store.read().autoDownload) return@withContext Result.success()
        val target = UpdateManager.apkFile(app, release)
        val part = File(target.parentFile, target.name + ".part")
        // These private files contain only previous updater downloads.
        target.parentFile?.listFiles()?.filter { it.name != target.name && it.name != part.name }?.forEach { if (it.isFile) it.delete() }
        store.status(UpdatePhase.DOWNLOADING, "正在下载 ${release.versionName}…")
        try {
            if (target.isFile) {
                runCatching { ApkVerifier.verify(app, target, release) }.onSuccess {
                    store.status(UpdatePhase.READY, "更新已下载并通过签名校验", 100)
                    return@withContext Result.success()
                }
                target.delete()
            }
            for (url in UpdatePolicy.urls(release.apkUrl, store.read().route)) {
                try {
                    UpdateTransport().download(url, release, part) { progress -> store.status(UpdatePhase.DOWNLOADING, "正在下载 ${release.versionName}…", progress) }
                    currentCoroutineContext().ensureActive()
                    try { ApkVerifier.verify(app, part, release) } catch (failure: Exception) { part.delete(); throw failure }
                    check(part.renameTo(target)) { "无法保存更新" }
                    store.status(UpdatePhase.READY, "更新已下载并通过签名校验", 100)
                    return@withContext Result.success()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* Resume the same, hash-checked artifact via the next route. */ }
            }
            store.status(UpdatePhase.ERROR, if (runAttemptCount < 2) "下载中断，等待网络后自动重试" else "下载未完成，可切换线路后重试")
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        } catch (cancelled: CancellationException) {
            store.status(UpdatePhase.AVAILABLE, "下载已暂停，联网后可继续", (part.length() * 100 / release.size).toInt())
            throw cancelled
        }
    }
}
