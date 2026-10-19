package org.yanjing.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.yanjing.app.diagnostics.Diagnostics
import org.yanjing.app.diagnostics.StartupRecovery

/** Uses platform Views so diagnostics remain reachable if Compose cannot start. */
class StartupActivity : Activity() {
    private lateinit var recovery: StartupRecovery
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recovery = StartupRecovery(this)
        if (intent.action == "org.yanjing.app.RECOVERY" || recovery.unfinished()) showRecovery() else openReader()
    }
    private fun openReader() {
        recovery.begin()
        startActivity(Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }
    private fun showRecovery() {
        val padding = (24 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding,padding,padding,padding)
        }
        fun text(value: String, size: Float) {
            content.addView(TextView(this).apply { text=value; textSize=size; setPadding(0,0,0,padding) })
        }
        fun button(label: String, action: () -> Unit) {
            content.addView(Button(this).apply { text=label; setOnClickListener { action() } })
        }
        text("${getString(R.string.app_name)} · 启动恢复",24f)
        text("上次启动未完成。可以重试，或关闭动态配色和自动检查更新后进入。收藏、历史和阅读进度会保留。",17f)
        button("重试启动") { openReader() }
        button("兼容启动") { recovery.enableCompatibility(); openReader() }
        button("导出本机诊断") {
            try { startActivity(Diagnostics.shareIntent(this)) }
            catch (_: Exception) { Toast.makeText(this,"暂时无法导出，请稍后重试。",Toast.LENGTH_LONG).show() }
        }
        text("诊断仅包含设备信息、异常堆栈和系统退出记录；由你选择是否分享。",14f)
        setContentView(ScrollView(this).apply { addView(content) })
    }
}
