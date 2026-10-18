package org.yanjing.app.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.Signature
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/** Exercises the identity and signer gates using Android's package manager API. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class ApkVerifierTest {
    @Test fun wrongHashPackageVersionOrSignerIsRejected() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val file=File(context.filesDir,"candidate.apk").apply { writeBytes(ByteArray(1_000_000)) }
        val release=ReleaseInfo(3,"1.2.0","https://github.com/KiRenk0/yanjing-bible/releases/download/v1.2.0/yanjing-1.2.0.apk",file.length(),UpdatePolicy.sha256(file),"",26)
        val current=context.packageManager.getPackageInfo(context.packageName,0)
        val cert=Signature(byteArrayOf(1,2,3))
        fun info(name:String=context.packageName, code:Int=3, signer:Signature=cert) = PackageInfo().apply {
            packageName=name; versionCode=code; versionName="1.2.0"
            signingInfo=org.robolectric.util.ReflectionHelpers.callConstructor(android.content.pm.SigningInfo::class.java).also {
                shadowOf(it).setSignatures(arrayOf(signer))
            }
        }
        current.signingInfo=org.robolectric.util.ReflectionHelpers.callConstructor(android.content.pm.SigningInfo::class.java).also { shadowOf(it).setSignatures(arrayOf(cert)) }
        val pm=shadowOf(context.packageManager)
        pm.installPackage(current)
        pm.setPackageArchiveInfo(file.absolutePath,info())
        ApkVerifier.verify(context,file,release)
        assertTrue(runCatching { ApkVerifier.verify(context,file,release.copy(sha256="0".repeat(64))) }.isFailure)
        pm.setPackageArchiveInfo(file.absolutePath,info(name="org.imposter"))
        assertTrue(runCatching { ApkVerifier.verify(context,file,release) }.isFailure)
        pm.setPackageArchiveInfo(file.absolutePath,info(code=4))
        assertTrue(runCatching { ApkVerifier.verify(context,file,release) }.isFailure)
        pm.setPackageArchiveInfo(file.absolutePath,info(signer=Signature(byteArrayOf(9,9,9))))
        assertTrue(runCatching { ApkVerifier.verify(context,file,release) }.isFailure)
    }

    @Test fun metadataIsParsedWithIdentityChecksAndOptionsPersist() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val info=ReleaseInfo(3,"1.2.0","https://github.com/KiRenk0/yanjing-bible/releases/download/v1.2.0/yanjing-1.2.0.apk",1_000_000,"a".repeat(64),"改进",26)
        assertEquals(info,releaseFromJson(info.toJson()))
        assertTrue(runCatching { releaseFromJson(info.toJson().replace("org.yanjing.app","org.imposter")) }.isFailure)
        val store=UpdateStore(context)
        store.release(info); store.status(UpdatePhase.AVAILABLE,"发现新版")
        assertEquals(info,store.read().release); assertTrue(store.read().autoDownload); assertTrue(store.read().wifiOnly)
        store.dismiss(); assertEquals(3L,store.read().dismissed)
    }
}
