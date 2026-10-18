package org.yanjing.app.diagnostics

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.yanjing.app.AppViewModel
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36])
class DiagnosticsTest {
    @Test fun localReportContainsCrashEvidenceAndExcludesQueryStorage() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("yanjing",Context.MODE_PRIVATE).edit().putString("history","private-query-marker").commit()
        Diagnostics.record(app,Thread.currentThread(),IllegalStateException("synthetic-failure-marker"))
        val report=Diagnostics.report(app)
        assertTrue(report.contains("synthetic-failure-marker"))
        assertTrue(report.contains("ONNX Runtime=1.25.1"))
        assertFalse(report.contains("private-query-marker"))
        assertTrue(File(app.noBackupFilesDir,"diagnostics/crash-${android.os.Process.myPid()}.txt")
            .readText().contains("synthetic-failure-marker"))
    }
    @Test fun invalidStoredFontCannotCrashTextLayout() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("updates",Context.MODE_PRIVATE).edit().putBoolean("autoCheck",false).commit()
        app.getSharedPreferences("yanjing",Context.MODE_PRIVATE).edit().putString("font","NaN").commit()
        val model=AppViewModel(app)
        val store=androidx.lifecycle.ViewModelStore().apply { put("model",model) }
        assertEquals(18f,model.state.value.fontSize)
        model.fontSize(Float.POSITIVE_INFINITY)
        assertEquals(18f,model.state.value.fontSize)
        model.fontSize(1000f)
        assertEquals(28f,model.state.value.fontSize)
        store.clear()
    }
}
