package org.yanjing.app.update

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[37])
class ArchitectureUpdateTest {
    private val url="https://github.com/KiRenk0/yanjing-bible/releases/download/v99.0.0/yanjing-99.0.0"
    private fun manifest(arm: String=""""apkUrl":"$url-arm64-v8a.apk","size":65000000,"sha256":"${"b".repeat(64)}"""") = """{
        "schema":1,"applicationId":"org.yanjing.app","versionCode":99,"versionName":"99.0.0","minSdk":26,
        "apkUrl":"$url.apk","size":99000000,"sha256":"${"a".repeat(64)}","notes":"test","arm64":{$arm}}
        """
    @Test fun arm64SelectsSmallerArtifactAndOtherAbisRetainUniversal() {
        val arm=releaseFromJson(manifest(),listOf("arm64-v8a","armeabi-v7a"))
        assertTrue(arm.apkUrl.endsWith("-arm64-v8a.apk"));assertEquals(65_000_000L,arm.size)
        assertEquals("b".repeat(64),arm.sha256)
        assertEquals(arm,releaseFromJson(arm.toJson(),listOf("arm64-v8a")))
        for(abi in listOf("armeabi-v7a","x86_64","x86"))assertEquals("$url.apk",releaseFromJson(manifest(),listOf(abi)).apkUrl)
        assertEquals("$url.apk",releaseFromJson(manifest(),emptyList()).apkUrl)
    }
    @Test fun architectureChoiceCannotBypassProjectUrlVersionOrHashValidation() {
        assertThrows(IllegalArgumentException::class.java) { releaseFromJson(manifest().replace("$url-arm64-v8a.apk","https://evil.invalid/a.apk"),listOf("arm64-v8a")) }
        assertThrows(IllegalArgumentException::class.java) { releaseFromJson(manifest().replace("yanjing-99.0.0-arm64","yanjing-98.0.0-arm64"),listOf("arm64-v8a")) }
        assertThrows(IllegalArgumentException::class.java) { releaseFromJson(manifest().replace("b".repeat(64),"invalid"),listOf("arm64-v8a")) }
    }
}
