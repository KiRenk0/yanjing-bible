package org.yanjing.app.update

import org.yanjing.app.i18n.tr
import org.yanjing.app.R

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
        store.status(UpdatePhase.CHECKING, tr(R.string.ui_3d7de4bc0a))
        for (url in UpdatePolicy.urls(UpdatePolicy.MANIFEST_URL, store.read().route)) {
            try {
                val release = releaseFromJson(UpdateTransport().manifest(url))
                store.checked()
                if (store.read().phase in setOf(UpdatePhase.DOWNLOADING, UpdatePhase.READY)) return@withContext Result.success()
                if (release.versionCode <= BuildConfig.VERSION_CODE) {
                    store.status(UpdatePhase.IDLE, tr(R.string.ui_b783743f82, BuildConfig.VERSION_NAME))
                } else if (release.minSdk > Build.VERSION.SDK_INT) {
                    store.status(UpdatePhase.IDLE, tr(R.string.ui_65c34b0c40, release.minSdk))
                } else {
                    store.release(release)
                    store.status(UpdatePhase.AVAILABLE, tr(R.string.ui_c8de69e34e, release.versionName))
                    if (store.read().autoDownload) UpdateManager.enqueueDownload(applicationContext, true)
                }
                return@withContext Result.success()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Try the next configured route, preserving the same release identity. */ }
        }
        store.status(UpdatePhase.ERROR, tr(R.string.ui_5df389145a))
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
        store.status(UpdatePhase.DOWNLOADING, tr(R.string.ui_9fbfea7c70, release.versionName))
        try {
            if (target.isFile) {
                runCatching { ApkVerifier.verify(app, target, release) }.onSuccess {
                    store.status(UpdatePhase.READY, tr(R.string.ui_5619ce7c4a), 100)
                    return@withContext Result.success()
                }
                target.delete()
            }
            for (url in UpdatePolicy.urls(release.apkUrl, store.read().route)) {
                try {
                    UpdateTransport().download(url, release, part) { progress -> store.status(UpdatePhase.DOWNLOADING, tr(R.string.ui_9fbfea7c70, release.versionName), progress) }
                    currentCoroutineContext().ensureActive()
                    try { ApkVerifier.verify(app, part, release) } catch (failure: Exception) { part.delete(); throw failure }
                    check(part.renameTo(target)) { tr(R.string.ui_bf4a2d3eff) }
                    store.status(UpdatePhase.READY, tr(R.string.ui_5619ce7c4a), 100)
                    return@withContext Result.success()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* Resume the same, hash-checked artifact via the next route. */ }
            }
            store.status(UpdatePhase.ERROR, if (runAttemptCount < 2) tr(R.string.ui_e47e41d111) else tr(R.string.ui_7697849586))
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        } catch (cancelled: CancellationException) {
            store.status(UpdatePhase.AVAILABLE, tr(R.string.ui_a1688b2a54), (part.length() * 100 / release.size).toInt())
            throw cancelled
        }
    }
}
