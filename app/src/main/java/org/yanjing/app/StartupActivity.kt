package org.yanjing.app

import org.yanjing.app.i18n.tr
import org.yanjing.app.R

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
    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(org.yanjing.app.i18n.I18n.wrap(base))
    }
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
        text(tr(R.string.ui_2ab1425887, getString(R.string.app_name)),24f)
        text(tr(R.string.ui_2bb608ca4c),17f)
        button(tr(R.string.ui_47d5cd3e98)) { openReader() }
        button(tr(R.string.ui_466dc18f7d)) { recovery.enableCompatibility(); openReader() }
        button(tr(R.string.ui_a1fa288e54)) {
            try { startActivity(Diagnostics.shareIntent(this)) }
            catch (_: Exception) { Toast.makeText(this,tr(R.string.ui_6cf56c400b),Toast.LENGTH_LONG).show() }
        }
        text(tr(R.string.ui_db987f1de5),14f)
        setContentView(ScrollView(this).apply { addView(content) })
    }
}
