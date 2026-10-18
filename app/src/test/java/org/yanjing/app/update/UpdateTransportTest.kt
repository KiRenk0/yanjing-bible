package org.yanjing.app.update

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class UpdateTransportTest {
    private val release = ReleaseInfo(3,"1.2.0","https://github.com/KiRenk0/yanjing-bible/releases/download/v1.2.0/yanjing-1.2.0.apk",1_000_000,"a".repeat(64),"更新",26)
    private class Response(val status: Int, val body: ByteArray, val headers: Map<String,String> = emptyMap()) : HttpURLConnection(URL("https://example.org/")) {
        override fun connect() { }; override fun disconnect() { }; override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getInputStream() = body.inputStream()
        override fun getHeaderField(name: String) = headers[name]
        override fun getContentLengthLong() = body.size.toLong()
    }

    @Test fun policyRestrictsMetadataToThisRepositoryAndSecureMirrors() {
        assertEquals(release, UpdatePolicy.validate(release))
        val bad = listOf(release.copy(apkUrl=release.apkUrl.replace("KiRenk0","attacker")),release.copy(apkUrl=release.apkUrl+"?redirect=1"),
            release.copy(apkUrl=release.apkUrl.replace("https:","http:")), release.copy(apkUrl=release.apkUrl.replace("github.com/","github.com:443/")),
            release.copy(versionName="2.0.0"),release.copy(size=Long.MAX_VALUE),release.copy(sha256="bad"))
        bad.forEach { assertTrue(runCatching { UpdatePolicy.validate(it) }.isFailure) }
        assertEquals(3,UpdatePolicy.urls(release.apkUrl,DownloadRoute.AUTO).size)
        assertTrue(UpdatePolicy.urls(release.apkUrl,DownloadRoute.MIRROR_FIRST).first().startsWith("https://gh-proxy.com/"))
        assertEquals(listOf(release.apkUrl),UpdatePolicy.urls(release.apkUrl,DownloadRoute.DIRECT))
        assertTrue(runCatching { UpdatePolicy.urls("https://example.org/app.apk",DownloadRoute.AUTO) }.isFailure)
    }

    @Test fun interruptedDownloadsResumeAndCheckFinalLength() = runBlocking {
        val file = File.createTempFile("updater-", ".part")
        try {
            file.writeBytes(ByteArray(200_000) { 1 })
            val response = Response(206,ByteArray(800_000) { 2 },mapOf("Content-Range" to "bytes 200000-999999/1000000"))
            UpdateTransport { response }.download(release.apkUrl,release,file) { }
            assertEquals("bytes=200000-",response.getRequestProperty("Range"))
            assertEquals(release.size,file.length()); assertEquals(1,file.readBytes()[199999].toInt()); assertEquals(2,file.readBytes()[200000].toInt())
            file.writeBytes(ByteArray(100))
            assertTrue(runCatching { UpdateTransport { Response(206,ByteArray(100),mapOf("Content-Range" to "bytes 0-99/100")) }.download(release.apkUrl,release,file) { } }.isFailure)
            assertTrue(runCatching { UpdateTransport { Response(200,ByteArray(10)) }.download(release.apkUrl,release,file) { } }.isFailure)
        } finally { file.delete() }
    }

    @Test fun serverIgnoringRangeRestartsAndManifestHasStrictBounds() = runBlocking {
        val file=File.createTempFile("updater-", ".part")
        try {
            file.writeBytes(ByteArray(200)); val bytes=ByteArray(1_000_000) { 3 }
            UpdateTransport { Response(200,bytes) }.download(release.apkUrl,release,file) { }
            assertArrayEquals(bytes,file.readBytes())
            assertEquals("{}",UpdateTransport { Response(200,"{}".toByteArray()) }.manifest(UpdatePolicy.MANIFEST_URL))
            assertTrue(runCatching { UpdateTransport { Response(200,ByteArray(65537)) }.manifest(UpdatePolicy.MANIFEST_URL) }.isFailure)
            assertTrue(runCatching { UpdateTransport { Response(302,byteArrayOf(),mapOf("Location" to "http://example.org/insecure")) }.manifest(UpdatePolicy.MANIFEST_URL) }.isFailure)
        } finally { file.delete() }
    }
}
