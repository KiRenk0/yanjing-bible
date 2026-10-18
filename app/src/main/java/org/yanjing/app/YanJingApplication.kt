package org.yanjing.app

import android.app.Application
import android.content.Context
import org.yanjing.app.diagnostics.Diagnostics

class YanJingApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, failure ->
            runCatching { Diagnostics.record(this,thread,failure) }
            if (previous != null) previous.uncaughtException(thread,failure)
            else { android.os.Process.killProcess(android.os.Process.myPid()) }
        }
    }
}
