package org.yanjing.app.update

import android.content.Context
import org.json.JSONObject
import org.yanjing.app.BuildConfig

enum class UpdatePhase { IDLE, CHECKING, AVAILABLE, DOWNLOADING, READY, ERROR }
data class UpdateState(val phase: UpdatePhase = UpdatePhase.IDLE, val release: ReleaseInfo? = null,
    val progress: Int = 0, val message: String = "", val autoCheck: Boolean = true,
    val autoDownload: Boolean = true, val wifiOnly: Boolean = true,
    val route: DownloadRoute = DownloadRoute.AUTO, val lastChecked: Long = 0, val dismissed: Long = 0)

internal fun releaseFromJson(text: String, supportedAbis: List<String> = android.os.Build.SUPPORTED_ABIS.toList()): ReleaseInfo {
    val json = JSONObject(text)
    require(json.getInt("schema") == 1 && json.getString("applicationId") == BuildConfig.APPLICATION_ID)
    val universal = UpdatePolicy.validate(ReleaseInfo(json.getLong("versionCode"), json.getString("versionName"),
        json.getString("apkUrl"), json.getLong("size"), json.getString("sha256"), json.optString("notes"), json.getInt("minSdk")))
    if(supportedAbis.firstOrNull() != "arm64-v8a") return universal
    val arm = json.optJSONObject("arm64") ?: return universal
    val selected = UpdatePolicy.validate(universal.copy(apkUrl=arm.getString("apkUrl"),size=arm.getLong("size"),sha256=arm.getString("sha256")))
    require(selected.apkUrl.endsWith("-arm64-v8a.apk"))
    return selected
}
internal fun ReleaseInfo.toJson(): String = JSONObject().put("schema", 1).put("applicationId", BuildConfig.APPLICATION_ID)
    .put("versionCode", versionCode).put("versionName", versionName).put("apkUrl", apkUrl)
    .put("size", size).put("sha256", sha256).put("notes", notes).put("minSdk", minSdk).toString()

class UpdateStore(context: Context) {
    val preferences = context.applicationContext.getSharedPreferences("updates", Context.MODE_PRIVATE)
    fun read(): UpdateState {
        val release = runCatching { releaseFromJson(preferences.getString("release", "") ?: "") }.getOrNull()
            ?.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
        return UpdateState(phase = runCatching { UpdatePhase.valueOf(preferences.getString("phase", "IDLE")!!) }.getOrDefault(UpdatePhase.IDLE)
            .let { if (release == null && it in setOf(UpdatePhase.READY, UpdatePhase.AVAILABLE, UpdatePhase.DOWNLOADING)) UpdatePhase.IDLE else it },
            release = release, progress = preferences.getInt("progress", 0), message = preferences.getString("message", "") ?: "",
            autoCheck = preferences.getBoolean("autoCheck", true), autoDownload = preferences.getBoolean("autoDownload", true),
            wifiOnly = preferences.getBoolean("wifiOnly", true), route = runCatching { DownloadRoute.valueOf(preferences.getString("route", "AUTO")!!) }.getOrDefault(DownloadRoute.AUTO),
            lastChecked = preferences.getLong("lastChecked", 0), dismissed = preferences.getLong("dismissed", 0))
    }
    fun status(phase: UpdatePhase, message: String, progress: Int = 0) {
        preferences.edit().putString("phase", phase.name).putString("message", message).putInt("progress", progress).apply()
    }
    fun release(info: ReleaseInfo) { preferences.edit().putString("release", info.toJson()).apply() }
    fun checked() { preferences.edit().putLong("lastChecked", System.currentTimeMillis()).apply() }
    fun dismiss() { preferences.edit().putLong("dismissed", read().release?.versionCode ?: 0).apply() }
}
