package org.yanjing.app.ui

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.yanjing.app.MainActivity
import org.yanjing.app.i18n.I18n
import java.io.File
import androidx.compose.ui.graphics.asAndroidBitmap

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35, 36], qualifiers = "zh-rCN-w412dp-h915dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DevotionsFlowTest {
    @get:Rule(order = 0) val reset = object : ExternalResource() {
        override fun before() {
            org.robolectric.util.ReflectionHelpers.setStaticField(ViewModelProvider.AndroidViewModelFactory::class.java, "_instance", null)
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.getSharedPreferences("updates", Context.MODE_PRIVATE).edit().clear().putBoolean("autoCheck", false).commit()
            context.getSharedPreferences("yanjing", Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences("devotions", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun devotionsEntryPrayersCopyAndRosaryProgressAreUsable() {
        compose.onNodeWithText("读经", useUnmergedTree = true).performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("devotions.prayers").fetchSemanticsNodes().isNotEmpty() }
        screenshot("devotions-home")
        compose.onNodeWithTag("devotions.prayers").performClick()
        compose.onNodeWithTag("prayer.our_father").performScrollTo().performClick()
        compose.onNodeWithText("我们在天上的父", substring = true).assertExists()
        compose.onNodeWithTag("devotions.copy").performClick()
        compose.runOnIdle {
            val clipboard = compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            assertTrue(clipboard.primaryClip!!.getItemAt(0).text.toString().startsWith("天主经\n"))
        }
        screenshot("devotions-prayer")
        repeat(2) { compose.onNodeWithTag("devotions.back").performClick() }
        compose.onNodeWithTag("rosary.joyful").performScrollTo().performClick()
        compose.onNodeWithTag("rosary.previous").assertIsNotEnabled()
        repeat(9) { compose.onNodeWithTag("rosary.next").performClick() }
        compose.onNodeWithTag("rosary.repetition").assertTextEquals("圣母经 · 第 1 / 10 遍")
        screenshot("devotions-rosary")
        compose.onNodeWithText("阅读", useUnmergedTree = true).performClick()
        compose.onNodeWithText("读经", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("rosary.joyful").performScrollTo().performClick()
        compose.onNodeWithTag("rosary.repetition").assertTextEquals("圣母经 · 第 1 / 10 遍")
        compose.onNodeWithTag("rosary.previous").performClick()
        compose.onNodeWithTag("rosary.repetition").assertDoesNotExist()
        compose.onNodeWithTag("rosary.restart").performScrollTo().performClick()
        compose.onAllNodesWithText("重新开始").onLast().performClick()
        compose.onNodeWithTag("rosary.progress").assertTextEquals("第 1 / 80 步")
        compose.runOnIdle {
            val repository = org.yanjing.app.data.DevotionRepository(compose.activity)
            repository.position(repository.catalog.sets.first { it.id == "joyful" }, 79)
        }
        compose.onNodeWithText("阅读", useUnmergedTree = true).performClick()
        compose.onNodeWithText("读经", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("rosary.joyful").performScrollTo().performClick()
        compose.onNodeWithTag("rosary.next").performClick()
        compose.onNodeWithTag("rosary.progress").assertTextEquals("本次诵念已完成")
        compose.onNodeWithTag("rosary.next").assertIsNotEnabled()
    }
    private fun screenshot(name: String) {
        val output = File("build/screenshots/$name.png"); output.parentFile?.mkdirs()
        output.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
