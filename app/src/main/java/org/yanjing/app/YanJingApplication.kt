package org.yanjing.app

import android.app.Application
import android.content.Context
import org.yanjing.app.diagnostics.Diagnostics
import androidx.work.Configuration

class YanJingApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, failure ->
            runCatching { Diagnostics.record(this,thread,failure) }
            runCatching {
                if (android.os.Build.VERSION.SDK_INT >= 28 && getProcessName() == packageName)
                    org.yanjing.app.diagnostics.StartupRecovery(this).begin()
            }
            if (previous != null) previous.uncaughtException(thread,failure)
            else { android.os.Process.killProcess(android.os.Process.myPid()) }
        }
    }
}
