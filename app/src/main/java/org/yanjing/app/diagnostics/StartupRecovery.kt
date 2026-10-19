package org.yanjing.app.diagnostics

import android.content.Context
import androidx.core.content.edit

/** A separate marker keeps an unfinished Compose startup from causing a launch loop.
 * No scripture, history or favorites are changed by recovery. */
internal class StartupRecovery(context: Context) {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences("startup", Context.MODE_PRIVATE)
    fun unfinished(): Boolean = preferences.getBoolean("pending", false)
    // Persist before entering code that may crash; an asynchronous write may be lost.
    fun begin() { preferences.edit(commit = true) { putBoolean("pending", true) } }
    fun complete() { preferences.edit { putBoolean("pending", false) } }
    fun enableCompatibility() {
        app.getSharedPreferences("yanjing", Context.MODE_PRIVATE).edit(commit = true) { putString("dynamic", "false") }
        app.getSharedPreferences("updates", Context.MODE_PRIVATE).edit(commit = true) { putBoolean("autoCheck", false) }
    }
}
