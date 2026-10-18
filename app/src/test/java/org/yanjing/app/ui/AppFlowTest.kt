package org.yanjing.app.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.junit.rules.ExternalResource
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.yanjing.app.MainActivity
import org.yanjing.app.AppViewModel
import androidx.lifecycle.ViewModelProvider
import java.io.File

/** Runs real Compose UI and local assets, without relying on an emulator's hypervisor. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w412dp-h915dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppFlowTest {
    @get:Rule(order = 0) val reset = object : ExternalResource() {
        override fun before() {
            // AndroidX's singleton otherwise keeps the previous Robolectric Application.
            org.robolectric.util.ReflectionHelpers.setStaticField(ViewModelProvider.AndroidViewModelFactory::class.java, "_instance", null)
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.getSharedPreferences("updates",Context.MODE_PRIVATE).edit().clear().putBoolean("autoCheck",false).commit()
            context.getSharedPreferences("yanjing",Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Before fun restoreDefaultModes() {
        compose.runOnIdle { ViewModelProvider(compose.activity)[AppViewModel::class.java].modes(setOf(org.yanjing.app.core.SearchMode.SEMANTIC,org.yanjing.app.core.SearchMode.EXPRESSION)) }
    }

    private fun waitForText(text: String) {
        try { compose.waitUntil(30000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
        catch (failure: Throwable) {
            val state=ViewModelProvider(compose.activity)[AppViewModel::class.java].state.value
            println("UI wait failed for $text: query=${state.query}, modes=${state.modes}, loading=${state.loading}, searching=${state.searching}, message=${state.response.message}, hits=${state.response.hits.take(5)}")
            throw failure
        }
    }
    private fun screenshot(name: String) {
        val directory = File("build/screenshots").apply { mkdirs() }
        val target = if (name == "04-compare") compose.onNode(isDialog()) else compose.onRoot()
        target.captureToImage().asAndroidBitmap().let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun searchSaveCompareReadAndChangeTheme() {
        waitForText("完全离线 · 无需登录")
        screenshot("01-home")
        compose.onNodeWithText("能给我水喝吗").performScrollTo().performClick()
        waitForText("约翰福音 4:7")
        screenshot("02-match")
        compose.onNodeWithContentDescription("收藏 约翰福音 4:7").performClick()
        compose.onNodeWithText("收藏", useUnmergedTree = true).performClick()
        waitForText("1 条收藏")
        compose.onNodeWithText("约翰福音 4:7").assertIsDisplayed()
        screenshot("03-saved")
        compose.onNodeWithText("上下文与对照").performClick()
        waitForText("同一出处的不同文本。部分版本分节方式可能不同。")
        compose.onNodeWithText("和合本·繁體").assertExists()
        screenshot("04-compare")
        compose.onNodeWithText("上下文").performClick()
        waitForText("4:7 · 当前经文")
        compose.onNodeWithText("阅读整章").performClick()
        waitForText("约翰福音 · 第 4 章")
        screenshot("05-reader")
        compose.onNodeWithText("设置", useUnmergedTree = true).performClick()
        compose.onNodeWithText("深色").performClick()
        screenshot("06-dark-settings")
        compose.onNodeWithText("跟随系统").performClick()
    }

    @Test
    @Config(sdk = [35], qualifiers = "zh-rCN-w840dp-h1000dp-xhdpi")
    fun tabletUsesTheNavigationRailAndReadsChapters() {
        waitForText("完全离线 · 无需登录")
        screenshot("07-tablet-home")
        compose.onNodeWithText("阅读").performClick()
        waitForText("约翰福音 · 第 1 章")
        screenshot("08-tablet-reader")
    }

    @Test fun searchModesAreMultiSelectableAccessibleAndPersisted() {
        waitForText("完全离线 · 无需登录")
        compose.onNodeWithContentDescription("搜索设置").performClick()
        waitForText("可多选，同时查找并合并重复经文。")
        compose.onNodeWithText("句意搜索").assertIsOn()
        compose.onNodeWithText("日常表达").assertIsOn()
        compose.onNodeWithText("原文搜索").performClick().assertIsOn()
        compose.onNode(isDialog()).captureToImage().asAndroidBitmap().let { bitmap ->
            File("build/screenshots/09-search-modes.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
        compose.onNodeWithText("应用").performClick()
        waitForText("句意搜索 + 日常表达 + 原文搜索")
        compose.onNodeWithContentDescription("搜索设置").performClick()
        compose.onNodeWithText("句意搜索").performClick()
        compose.onNodeWithText("日常表达").performClick()
        compose.onNodeWithText("原文搜索").performClick()
        compose.onNodeWithText("应用").assertIsNotEnabled()
        compose.onNodeWithText("句意搜索").performClick()
        compose.onNodeWithText("应用").performClick()
        waitForText("句意搜索")
        compose.waitForIdle()
        val prefs = compose.activity.applicationContext.getSharedPreferences("yanjing", Context.MODE_PRIVATE)
        compose.runOnIdle { org.junit.Assert.assertEquals("SEMANTIC", prefs.getString("searchModes", "")) }
        compose.onNodeWithText("能给我水喝吗").performScrollTo().performClick()
        waitForText("士师记 4:19")
        screenshot("10-semantic-results")
    }

    @Test fun releasePromptAndDownloadProgressAreVisibleInSettings() {
        waitForText("完全离线 · 无需登录")
        val store = org.yanjing.app.update.UpdateStore(compose.activity.applicationContext)
        store.release(org.yanjing.app.update.ReleaseInfo(3,"1.2.0",
            "https://github.com/KiRenk0/yanjing-bible/releases/download/v1.2.0/yanjing-1.2.0.apk",
            1_000_000,"a".repeat(64),"改善搜索体验",26))
        store.status(org.yanjing.app.update.UpdatePhase.AVAILABLE,"等待 Wi-Fi 自动下载")
        waitForText("发现新版本 1.2.0")
        compose.onNodeWithText("改善搜索体验").assertIsDisplayed()
        compose.onNodeWithText("稍后").performClick()
        compose.onNodeWithText("设置",useUnmergedTree=true).performClick()
        compose.onNodeWithText("检查更新").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("下载线路").performScrollTo().assertExists()
        store.status(org.yanjing.app.update.UpdatePhase.DOWNLOADING,"正在下载 1.2.0…",37)
        waitForText("37%")
        compose.onNodeWithText("37%").performScrollTo().assertIsDisplayed()
        screenshot("11-update-settings")
    }
}
