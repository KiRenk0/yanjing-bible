package org.yanjing.app.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
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
import org.yanjing.app.core.BibleVersion
import org.yanjing.app.core.SearchMode
import androidx.lifecycle.ViewModelProvider
import java.io.File

/** Runs real Compose UI and local assets, without relying on an emulator's hypervisor. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35,36], qualifiers = "zh-rCN-w412dp-h915dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppFlowTest {
    @Test fun newClassicsAndLatinCanSearchReadSaveAndCompare() {
        waitForText("完全离线 · 无需登录")
        compose.onNodeWithText("佛教").performScrollTo().performClick()
        waitForText("1 篇 · 7 段 · 现代汉语搜索文言原文")
        compose.onNodeWithText("放下执着和恐惧").performScrollTo().performClick()
        waitForText("心经·般若波罗蜜多心经 1:4")
        compose.onNodeWithContentDescription("收藏 心经·般若波罗蜜多心经 1:4").performClick()
        compose.onNodeWithText("道家").performScrollTo().performClick()
        compose.onNodeWithContentDescription("清空搜索").performClick()
        waitForText("81 章 · 81 段 · 现代汉语搜索文言原文")
        compose.onNodeWithText("像水一样帮助别人而不争").performScrollTo().performClick()
        waitForText("道德经·第8章 8:1")
        screenshot("20-tao-search")
        compose.onNodeWithContentDescription("选择经典与版本").performClick()
        compose.onNodeWithText("Vulgata Clementina").performScrollTo().performClick()
        compose.runOnIdle { ViewModelProvider(compose.activity)[AppViewModel::class.java].query("诗篇 22:1") }
        waitForText("诗篇〔拉丁文〕 22:1")
        screenshot("21-latin-search")
        compose.onNodeWithText("上下文与对照").performClick()
        waitForText("同一出处的不同文本。部分版本分节方式可能不同。")
        compose.onNodeWithText("耶和华是我的牧者",substring=true).assertExists()
        screenshot("22-latin-comparison")
    }
    @get:Rule(order = 0) val reset = object : ExternalResource() {
        override fun before() {
            // AndroidX's singleton otherwise keeps the previous Robolectric Application.
            org.robolectric.util.ReflectionHelpers.setStaticField(ViewModelProvider.AndroidViewModelFactory::class.java, "_instance", null)
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.getSharedPreferences("updates",Context.MODE_PRIVATE).edit().clear().putBoolean("autoCheck",false).commit()
            context.getSharedPreferences("yanjing",Context.MODE_PRIVATE).edit().clear().commit()
            semanticService = org.robolectric.Robolectric.buildService(org.yanjing.app.data.SemanticService::class.java).create()
            org.robolectric.Shadows.shadowOf(context as android.app.Application).setComponentNameAndServiceForBindService(
                android.content.ComponentName(context, org.yanjing.app.data.SemanticService::class.java),
                semanticService.get().onBind(android.content.Intent()))
        }
        override fun after() { semanticService.destroy() }
    }
    private lateinit var semanticService: org.robolectric.android.controller.ServiceController<org.yanjing.app.data.SemanticService>
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Before fun restoreDefaultModes() {
        compose.runOnIdle { ViewModelProvider(compose.activity)[AppViewModel::class.java].modes(setOf(org.yanjing.app.core.SearchMode.SEMANTIC,org.yanjing.app.core.SearchMode.EXPRESSION)) }
    }

    private fun waitForText(text: String) {
        try { compose.waitUntil(30000) {
            // Scripture headers change before the asynchronous corpus load finishes.
            // Wait for usable content before clicking suggestions or chapter controls.
            // Fetch first so Compose/Robolectric advances the main looper while loading.
            val nodes = compose.onAllNodesWithText(text).fetchSemanticsNodes()
            val state = ViewModelProvider(compose.activity)[AppViewModel::class.java].state.value
            !state.loading && !state.searching && nodes.isNotEmpty()
        } }
        catch (failure: Throwable) {
            val state=ViewModelProvider(compose.activity)[AppViewModel::class.java].state.value
            println("UI wait failed for $text: query=${state.query}, modes=${state.modes}, loading=${state.loading}, searching=${state.searching}, message=${state.response.message}, hits=${state.response.hits.take(5)}")
            throw failure
        }
    }
    private fun screenshot(name: String) {
        val directory = File("build/screenshots").apply { mkdirs() }
        val target = if (name.endsWith("-compare")) compose.onNode(isDialog()) else compose.onRoot()
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

    @Test fun switchingScripturesClassicsAndTranslationsSearchesTheRetainedQuery() {
        waitForText("完全离线 · 无需登录")
        val model = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        compose.runOnIdle { model.modes(setOf(SearchMode.KEYWORD)) }
        compose.onAllNodes(hasSetTextAction()).onLast().performTextInput("天")
        compose.onAllNodes(hasSetTextAction()).onLast().performImeAction()

        fun assertMatches(version: BibleVersion, reference: String) {
            compose.onAllNodes(hasSetTextAction()).onLast().assertTextEquals("天")
            waitForText(reference)
            compose.runOnIdle {
                val state = model.state.value
                org.junit.Assert.assertEquals(version, state.version)
                org.junit.Assert.assertEquals("天", state.query)
                org.junit.Assert.assertEquals(setOf(SearchMode.KEYWORD), state.modes)
                org.junit.Assert.assertTrue(state.response.hits.isNotEmpty())
                org.junit.Assert.assertTrue(state.response.hits.all { version.acceptsBook(it.verse.key.book) && it.verse.text.contains("天") })
                org.junit.Assert.assertEquals(listOf("天"), state.history)
            }
        }

        assertMatches(BibleVersion.CUVS, "创世记 1:1")
        compose.onNodeWithText("古兰经").performClick()
        assertMatches(BibleVersion.QURAN_ZH, "古兰经 2:22")
        compose.onNodeWithText("儒家").performScrollTo().performClick()
        assertMatches(BibleVersion.ANALECTS, "论语·为政 2:4")
        compose.onNodeWithText("大学").performScrollTo().performClick()
        assertMatches(BibleVersion.GREAT_LEARNING, "大学·大学 1:2")
        compose.onNodeWithContentDescription("选择经典与版本").performClick()
        compose.onNodeWithText("和合本·繁體").performScrollTo().performClick()
        assertMatches(BibleVersion.CUVT, "创世记 1:1")

        compose.onNodeWithContentDescription("清空搜索").performClick()
        waitForText("完全离线 · 无需登录")
        compose.onNodeWithText("古兰经").performClick()
        waitForText("114 章 · 6,236 节 · 中阿文本可对照")
        compose.onAllNodes(hasSetTextAction()).onLast().assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.runOnIdle { org.junit.Assert.assertTrue(model.state.value.response.hits.isEmpty()) }
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
        store.release(org.yanjing.app.update.ReleaseInfo((org.yanjing.app.BuildConfig.VERSION_CODE+1).toLong(),"99.0.0",
            "https://github.com/KiRenk0/yanjing-bible/releases/download/v99.0.0/yanjing-99.0.0.apk",
            1_000_000,"a".repeat(64),"改善搜索体验",26))
        store.status(org.yanjing.app.update.UpdatePhase.AVAILABLE,"等待 Wi-Fi 自动下载")
        waitForText("发现新版本 99.0.0")
        compose.onNodeWithText("改善搜索体验").assertIsDisplayed()
        compose.onNodeWithText("稍后").performClick()
        compose.onNodeWithText("设置",useUnmergedTree=true).performClick()
        compose.onNodeWithText("检查更新").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("下载线路").performScrollTo().assertExists()
        store.status(org.yanjing.app.update.UpdatePhase.DOWNLOADING,"正在下载 99.0.0…",37)
        waitForText("37%")
        compose.onNodeWithText("37%").performScrollTo().assertIsDisplayed()
        screenshot("11-update-settings")
    }

    @Test fun analectsModernSearchSaveGlossReadAndSwitchPreserveEachScripture() {
        waitForText("完全离线 · 无需登录")
        compose.runOnIdle { ViewModelProvider(compose.activity)[AppViewModel::class.java].read(43,4) }
        compose.onNodeWithText("儒家").performClick()
        waitForText("20 篇 · 512 章句 · 白话搜索文言原文")
        compose.onNodeWithText("全本圣经").assertDoesNotExist()
        compose.runOnIdle { ViewModelProvider(compose.activity)[AppViewModel::class.java].modes(setOf(org.yanjing.app.core.SearchMode.SEMANTIC)) }
        waitForText("学习要结合思考")
        compose.onNodeWithText("学习要结合思考").performScrollTo().performClick()
        waitForText("论语·为政 2:15")
        screenshot("16-analects-search")
        compose.onNodeWithContentDescription("复制 论语·为政 2:15").performClick()
        compose.runOnIdle {
            val clipboard=compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val copied=clipboard.primaryClip!!.getItemAt(0).text.toString()
            org.junit.Assert.assertTrue(copied.contains("学而不思则罔"))
            org.junit.Assert.assertFalse(copied.contains("只学习不思考"))
        }
        compose.onNodeWithContentDescription("收藏 论语·为政 2:15").performClick()
        compose.onNodeWithText("收藏",useUnmergedTree=true).performClick()
        waitForText("1 条收藏")
        compose.onNodeWithText("上下文与对照").performClick()
        waitForText("现代汉语检索释义")
        compose.onNodeWithText("和合本·繁體").assertDoesNotExist()
        screenshot("17-analects-compare")
        compose.onNodeWithText("上下文").performClick()
        waitForText("2:15 · 当前经文")
        compose.onNodeWithText("阅读整篇").performClick()
        waitForText("第 2 篇 · 为政")
        compose.onNodeWithText("第 2 篇 · 为政").performClick()
        compose.onNode(hasScrollAction() and hasAnyAncestor(isDialog())).performScrollToNode(hasText("第 20 篇 · 尧曰"))
        compose.onNodeWithText("第 20 篇 · 尧曰").performClick()
        waitForText("论语·尧曰")
        screenshot("18-analects-reader")
        compose.onNodeWithText("匹配",useUnmergedTree=true).performClick()
        compose.onNodeWithText("古兰经").performClick()
        waitForText("114 章 · 6,236 节 · 中阿文本可对照")
        compose.onNodeWithText("阅读",useUnmergedTree=true).performClick()
        waitForText("第 1 章 · 法谛海")
        compose.onNodeWithText("匹配",useUnmergedTree=true).performClick()
        compose.onNodeWithText("圣经").performClick()
        compose.onNodeWithContentDescription("清空搜索").performClick()
        waitForText("能给我水喝吗")
        compose.onNodeWithText("阅读",useUnmergedTree=true).performClick()
        waitForText("约翰福音 · 第 4 章")
        compose.onNodeWithText("匹配",useUnmergedTree=true).performClick()
        compose.onNodeWithText("儒家").performClick()
        waitForText("20 篇 · 512 章句 · 白话搜索文言原文")
        compose.onNodeWithText("阅读",useUnmergedTree=true).performClick()
        waitForText("第 20 篇 · 尧曰")
        compose.onNodeWithText("收藏",useUnmergedTree=true).performClick()
        waitForText("论语·为政 2:15")
    }

    @Test fun quranSearchSaveCompareReadAndSwitchBackPreserveTheRightScripture() {
        waitForText("完全离线 · 无需登录")
        compose.runOnIdle { ViewModelProvider(compose.activity)[AppViewModel::class.java].read(43,4) }
        compose.onNodeWithText("古兰经").performClick()
        waitForText("114 章 · 6,236 节 · 中阿文本可对照")
        waitForText("古兰经 112:1-4")
        compose.onNodeWithText("古兰经 112:1-4").performScrollTo().performClick()
        waitForText("古兰经 112:1")
        screenshot("12-quran-search")
        compose.onNodeWithContentDescription("收藏 古兰经 112:1").performClick()
        compose.onNodeWithText("收藏",useUnmergedTree=true).performClick()
        waitForText("1 条收藏")
        compose.onNodeWithText("上下文与对照").performClick()
        waitForText("古兰经·阿拉伯文")
        compose.onNodeWithText("和合本·繁體").assertDoesNotExist()
        screenshot("13-quran-compare")
        compose.onNodeWithText("上下文").performClick()
        waitForText("112:1 · 当前经文")
        compose.onNodeWithText("阅读整章").performClick()
        waitForText("第 112 章 · 以赫拉斯")
        compose.onNodeWithText("第 112 章 · 以赫拉斯").performClick()
        compose.onNode(hasScrollAction() and hasAnyAncestor(isDialog())).performScrollToNode(hasText("第 114 章 · 拿斯"))
        compose.onNodeWithText("第 114 章 · 拿斯").performClick()
        waitForText("古兰经 114")
        screenshot("14-quran-reader")
        compose.onNode(hasText("古兰经·马坚译本") and hasClickAction()).performClick()
        compose.onNodeWithText("古兰经·阿拉伯文").performScrollTo().performClick()
        waitForText("阿拉伯文：Tanzil Project · Uthmani v1.1 · tanzil.net")
        screenshot("15-quran-arabic")
        compose.onNodeWithText("匹配",useUnmergedTree=true).performClick()
        compose.onNodeWithText("圣经").performClick()
        compose.onNodeWithContentDescription("清空搜索").performClick()
        waitForText("能给我水喝吗")
        compose.onNodeWithText("阅读",useUnmergedTree=true).performClick()
        waitForText("约翰福音 · 第 4 章")
        compose.onNodeWithText("收藏",useUnmergedTree=true).performClick()
        waitForText("古兰经 112:1")
        compose.onNodeWithText("古兰经·马坚译本").assertExists()
    }
}
