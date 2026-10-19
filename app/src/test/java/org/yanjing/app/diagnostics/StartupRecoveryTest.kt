package org.yanjing.app.diagnostics

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.yanjing.app.MainActivity
import org.yanjing.app.StartupActivity
import org.yanjing.app.ui.compatibleColorScheme
import org.yanjing.app.update.UpdateManager
import org.yanjing.app.update.UpdatePhase
import org.yanjing.app.update.UpdateStore

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36,37])
class StartupRecoveryTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private fun children(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { children(view.getChildAt(it)) } else emptyList()

    @Test fun unfinishedLaunchOpensPlatformRecoveryAndPreservesUserData() {
        val settings = app.getSharedPreferences("yanjing", Context.MODE_PRIVATE)
        settings.edit().clear().putString("saved","saved-marker").putString("history","history-marker")
            .putString("readerChapter","12").putString("dynamic","true").commit()
        val recovery = StartupRecovery(app)
        recovery.begin()
        Robolectric.buildActivity(StartupActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            assertFalse(activity.isFinishing)
            assertNull(shadowOf(activity).nextStartedActivity)
            children(activity.window.decorView).filterIsInstance<Button>().single { it.text == "兼容启动" }.performClick()
            assertEquals(MainActivity::class.java.name, shadowOf(activity).nextStartedActivity.component!!.className)
            assertEquals("false", settings.getString("dynamic", ""))
            assertFalse(app.getSharedPreferences("updates",Context.MODE_PRIVATE).getBoolean("autoCheck",true))
            assertEquals("saved-marker",settings.getString("saved",""))
            assertEquals("history-marker",settings.getString("history",""))
            assertEquals("12",settings.getString("readerChapter",""))
            assertTrue(recovery.unfinished())
            recovery.complete()
            assertFalse(recovery.unfinished())
        }
    }

    @Test fun healthyLaunchGoesStraightToReader() {
        StartupRecovery(app).complete()
        Robolectric.buildActivity(StartupActivity::class.java).setup().use { controller ->
            assertTrue(controller.get().isFinishing)
            assertEquals(MainActivity::class.java.name, shadowOf(controller.get()).nextStartedActivity.component!!.className)
        }
    }

    @Test fun launcherShortcutAlwaysReachesDiagnosticsWithoutStartingCompose() {
        StartupRecovery(app).complete()
        Robolectric.buildActivity(StartupActivity::class.java,
            Intent(app,StartupActivity::class.java).setAction("org.yanjing.app.RECOVERY")).setup().use { controller ->
            val activity = controller.get()
            assertFalse(activity.isFinishing)
            assertNull(shadowOf(activity).nextStartedActivity)
            assertTrue(children(activity.window.decorView).filterIsInstance<Button>().single { it.text == "导出本机诊断" }.isEnabled)
            // FileProvider compares POSIX paths; a Windows Robolectric host cannot
            // validate its URI grants. Report content is covered by DiagnosticsTest.
        }
    }

    @Test fun schedulerInitializationFailureIsReportedWithoutClosingTheApp() {
        app.getSharedPreferences("updates",Context.MODE_PRIVATE).edit().clear().putBoolean("autoCheck",true).commit()
        UpdateManager(app) { throw IllegalStateException("synthetic scheduler failure") }.use { manager ->
            manager.start()
            assertEquals(UpdatePhase.ERROR,UpdateStore(app).read().phase)
            assertTrue(Diagnostics.report(app).contains("synthetic scheduler failure"))
            manager.check()
            assertEquals(UpdatePhase.ERROR,UpdateStore(app).read().phase)
            manager.option("autoCheck",false)
            assertEquals(UpdatePhase.ERROR,UpdateStore(app).read().phase)
        }
    }

    @Test fun vendorPaletteFailureFallsBackAndUnrelatedFailuresRemainVisible() {
        val expected = compatibleColorScheme(false,false) { error("must not load") }
        val fallback = compatibleColorScheme(false,true) { throw Resources.NotFoundException("system palette") }
        assertEquals(expected.primary,fallback.primary)
        assertThrows(IllegalStateException::class.java) { compatibleColorScheme(false,true) { error("programming error") } }
    }
}
