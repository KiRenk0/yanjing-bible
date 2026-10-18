package org.yanjing.app.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

object ApkVerifier {
    @Suppress("DEPRECATION")
    fun verify(context: Context, apk: File, release: ReleaseInfo) {
        require(apk.isFile && apk.length() == release.size && UpdatePolicy.sha256(apk) == release.sha256) { "安装包完整性校验失败" }
        val manager = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = requireNotNull(manager.getPackageArchiveInfo(apk.absolutePath, flags)) { "不是有效的 Android 安装包" }
        val installed = manager.getPackageInfo(context.packageName, flags)
        val code = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        require(archive.packageName == context.packageName && code == release.versionCode && archive.versionName == release.versionName) { "安装包身份与更新信息不一致" }
        val currentCode = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode else installed.versionCode.toLong()
        require(code > currentCode) { "该版本无需安装" }
        fun certificates(info: PackageInfo): Set<String> {
            val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
            return signatures.orEmpty().map { signature -> java.security.MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
        }
        val expected = certificates(installed)
        require(expected.isNotEmpty() && certificates(archive) == expected) { "安装包签名与当前应用不同，已拒绝安装" }
    }
}
