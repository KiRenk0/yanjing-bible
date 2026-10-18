package org.yanjing.app.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

internal object ModelFiles {
    // The service serializes calls. Verify even a same-length cached file before native parsing.
    fun prepare(context: Context): File {
        val name = "bge-small-zh-int8.onnx"
        val manifest = JSONObject(context.assets.open("semantic/manifest.json").bufferedReader().use { it.readText() })
        val expected = manifest.getJSONObject("files").getJSONObject(name)
        val digest = expected.getString("sha256")
        val directory = File(context.noBackupFilesDir,"models")
        check(directory.isDirectory || directory.mkdirs()) { "无法创建语义模型目录" }
        val model = File(directory,"bge-${digest.take(16)}.onnx")
        fun valid(file: File) = file.isFile && file.length() == expected.getLong("bytes") && sha256(file) == digest
        if (!valid(model)) {
            val temporary = File(directory,"model.partial")
            try {
                context.assets.open("semantic/$name").use { input -> temporary.outputStream().use { input.copyTo(it) } }
                check(valid(temporary)) { "语义模型校验失败" }
                check(!model.exists() || model.delete()) { "无法替换损坏的语义模型" }
                check(temporary.renameTo(model)) { "无法保存语义模型" }
            } finally { temporary.delete() }
        }
        return model
    }
    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val bytes=ByteArray(65536)
            while (true) { val count=input.read(bytes); if (count<0) break; digest.update(bytes,0,count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
