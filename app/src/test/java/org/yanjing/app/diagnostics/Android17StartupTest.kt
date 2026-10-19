package org.yanjing.app.diagnostics

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.yanjing.app.AppViewModel
import org.yanjing.app.MainActivity

/** Espresso's input injector still reflects on an API removed in 37. Render
 * startup directly, with real resources and automatic update checking enabled. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37], qualifiers = "zh-rCN-w412dp-h915dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Android17StartupTest {
    @Test fun freshLaunchRendersWithDynamicColorsAndLoadsOfflineCorpus() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("yanjing", Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences("updates", Context.MODE_PRIVATE).edit().clear().commit()
        Robolectric.buildActivity(MainActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val model = ViewModelProvider(activity)[AppViewModel::class.java]
            val root = activity.window.decorView
            val deadline = System.nanoTime() + 30_000_000_000L
            do {
                shadowOf(Looper.getMainLooper()).idle()
                root.measure(View.MeasureSpec.makeMeasureSpec(824, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1830, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, 824, 1830)
                if (!model.state.value.loading) break
                Thread.sleep(10)
            } while (System.nanoTime() < deadline)
            val bitmap = Bitmap.createBitmap(824,1830,Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bitmap))
            assertFalse(model.state.value.loading)
            assertNull(model.state.value.error)
            assertTrue(model.state.value.dynamicColor)
            assertTrue(model.state.value.chapter.isNotEmpty())
            model.updates.start()
            assertNotEquals(org.yanjing.app.update.UpdatePhase.ERROR,
                org.yanjing.app.update.UpdateStore(app).read().phase)
            bitmap.recycle()
        }
    }
}
