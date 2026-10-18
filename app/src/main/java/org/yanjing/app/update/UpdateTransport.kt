package org.yanjing.app.update

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/** No credentials or search text are transmitted. Mirror files must pass the APK verifier. */
class UpdateTransport(private val connectionFactory: (String) -> HttpURLConnection = { URI(it).toURL().openConnection() as HttpURLConnection }) {
    private fun open(url: String, offset: Long = 0): HttpURLConnection {
        var target = url
        repeat(8) {
            require(URI(target).scheme == "https") { "更新连接必须使用 HTTPS" }
            val connection = connectionFactory(target)
            connection.connectTimeout = 8000; connection.readTimeout = 20000; connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "YanJing-Android")
            connection.setRequestProperty("Accept-Encoding", "identity")
            if (offset > 0) connection.setRequestProperty("Range", "bytes=$offset-")
            val status = connection.responseCode
            if (status in listOf(301,302,303,307,308)) {
                val location = connection.getHeaderField("Location") ?: throw IOException("重定向缺少地址")
                val next = URI(target).resolve(location).toString()
                connection.disconnect(); target = next
            } else return connection
        }
        throw IOException("重定向次数过多")
    }
    suspend fun manifest(url: String): String {
        val connection = open(url)
        try {
            if (connection.responseCode != 200) throw IOException("HTTP ${connection.responseCode}")
            val output = java.io.ByteArrayOutputStream()
            connection.inputStream.use { input -> val buffer = ByteArray(4096)
                while (true) { currentCoroutineContext().ensureActive(); val n = input.read(buffer); if (n < 0) break
                    if (output.size() + n > 65536) throw IOException("更新信息过大")
                    output.write(buffer, 0, n)
                }
            }
            return output.toByteArray().toString(Charsets.UTF_8)
        } finally { connection.disconnect() }
    }
    suspend fun download(url: String, release: ReleaseInfo, part: File, progress: (Int) -> Unit) {
        if (part.length() > release.size) part.delete()
        if (part.length() == release.size) return
        var offset = part.length()
        val connection = open(url, offset)
        try {
            when (connection.responseCode) {
                200 -> offset = 0
                206 -> if (!connection.getHeaderField("Content-Range").orEmpty().startsWith("bytes $offset-")) throw IOException("断点范围不一致")
                else -> throw IOException("HTTP ${connection.responseCode}")
            }
            if (connection.contentLengthLong > release.size - offset) throw IOException("下载长度异常")
            var received = offset; var lastProgress = 0L
            connection.inputStream.use { input -> FileOutputStream(part, offset > 0).use { output ->
                val buffer = ByteArray(65536)
                while (true) { currentCoroutineContext().ensureActive(); val count = input.read(buffer); if (count < 0) break
                    received += count
                    if (received > release.size) throw IOException("下载超过声明大小")
                    output.write(buffer, 0, count)
                    if (System.currentTimeMillis() - lastProgress > 750) { progress((received * 100 / release.size).toInt()); lastProgress = System.currentTimeMillis() }
                }
                output.fd.sync()
            } }
            if (received != release.size) throw IOException("下载尚未完成")
        } finally { connection.disconnect() }
    }
}
