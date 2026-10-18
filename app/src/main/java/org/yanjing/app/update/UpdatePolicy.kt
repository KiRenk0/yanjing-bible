package org.yanjing.app.update

import java.net.URI
import java.security.MessageDigest
import java.io.File

enum class DownloadRoute(val title: String) {
    AUTO("自动切换"), MIRROR_FIRST("镜像优先"), DIRECT("GitHub 直连")
}

data class ReleaseInfo(val versionCode: Long, val versionName: String, val apkUrl: String,
    val size: Long, val sha256: String, val notes: String, val minSdk: Int)

object UpdatePolicy {
    const val REPOSITORY = "KiRenk0/yanjing-bible"
    const val REPOSITORY_URL = "https://github.com/$REPOSITORY"
    const val MANIFEST_URL = "$REPOSITORY_URL/releases/latest/download/update.json"
    private val apkPath = Regex("^/$REPOSITORY/releases/download/v[0-9]+\\.[0-9]+\\.[0-9]+/yanjing-[0-9]+\\.[0-9]+\\.[0-9]+\\.apk$")

    fun validate(release: ReleaseInfo): ReleaseInfo {
        val url = URI(release.apkUrl)
        require(url.scheme == "https" && url.host == "github.com" && url.port == -1 && url.userInfo == null && url.query == null && url.fragment == null)
        require(apkPath.matches(url.rawPath)) { "更新地址不属于本项目" }
        require(release.versionCode in 1..2_100_000_000L && release.versionName.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+")))
        require(url.rawPath.endsWith("/yanjing-${release.versionName}.apk") && url.rawPath.contains("/v${release.versionName}/"))
        require(release.size in 1_000_000..300_000_000 && release.sha256.matches(Regex("[a-f0-9]{64}")))
        require(release.minSdk in 26..100 && release.notes.length <= 8000)
        return release
    }

    fun urls(canonical: String, route: DownloadRoute): List<String> {
        require(canonical == MANIFEST_URL || runCatching {
            val uri = URI(canonical)
            uri.scheme == "https" && uri.host == "github.com" && uri.port == -1 && uri.userInfo == null && uri.query == null && uri.fragment == null && apkPath.matches(uri.rawPath)
        }.getOrDefault(false))
        val mirrors = listOf("https://gh-proxy.com/$canonical", "https://ghfast.top/$canonical")
        return when (route) {
            DownloadRoute.AUTO -> listOf(canonical) + mirrors
            DownloadRoute.MIRROR_FIRST -> mirrors + canonical
            DownloadRoute.DIRECT -> listOf(canonical)
        }
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(65536)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
