package org.yanjing.app.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.yanjing.app.R
import org.yanjing.app.core.*
import org.yanjing.app.data.BibleRepository
import org.yanjing.app.data.SavedVerse
import org.yanjing.app.i18n.I18n
import org.yanjing.app.i18n.tr
import org.yanjing.app.update.*
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35, 36], qualifiers = "en-rUS")
class LocalizationTest {
    @Test fun languagesChangeInterfaceAndReferencesWithoutChangingSavedOriginals() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = BibleRepository(context)
        val verse = repository.load(BibleVersion.CUVS).verse(VerseKey(43, 3, 16))!!
        repository.save(listOf(SavedVerse(BibleVersion.CUVS, verse)))
        repository.setHistory(listOf("约 3:16"))
        val store = UpdateStore(context)
        val notes = mapOf("en" to "English notes", "zh-CN" to "简中说明", "zh-TW" to "繁中說明")
        store.release(ReleaseInfo(14, "1.8.0", "https://github.com/KiRenk0/yanjing-bible/releases/download/v1.8.0/yanjing-1.8.0.apk", 1_000_000, "0".repeat(64), "fallback", 26, notes))
        try {
            I18n.setLanguage(context, "en")
            assertEquals("Settings", tr(R.string.ui_df3d58c7d8))
            assertEquals("Devotions", tr(R.string.devotions_title))
            assertEquals("John 3:16", verse.key.reference)
            assertEquals(notes["en"], store.read().release!!.displayedNotes(context))
            store.status(UpdatePhase.ERROR, "English update status")
            I18n.setLanguage(context, "zh-CN")
            assertEquals("设置", tr(R.string.ui_df3d58c7d8))
            assertEquals("读经", tr(R.string.devotions_title))
            assertEquals("约翰福音 3:16", verse.key.reference)
            assertEquals(notes["zh-CN"], store.read().release!!.displayedNotes(context))
            assertNotEquals("English update status", store.read().message)
            I18n.setLanguage(context, "zh-TW")
            assertEquals("設置", tr(R.string.ui_df3d58c7d8))
            assertEquals("讀經", tr(R.string.devotions_title))
            assertEquals("約翰福音 3:16", verse.key.reference)
            assertEquals(notes["zh-TW"], store.read().release!!.displayedNotes(context))
            assertEquals(verse.text, repository.saved().single().verse.text)
            assertEquals(BibleVersion.CUVS, repository.saved().single().version)
            assertEquals(listOf("约 3:16"), repository.history())
            assertEquals("zh-TW", I18n.choice(context))
            assertEquals("設置", I18n.wrap(context).getString(R.string.ui_df3d58c7d8))
        } finally { I18n.setLanguage(context, "system") }
    }
    @Test fun englishClassicReferencesCanBePastedBackIntoSearch() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        try {
            I18n.setLanguage(context, "en")
            val normalizer = BibleRepository(context).normalizer
            val key = VerseKey(76, 25, 1)
            val parsed = ReferenceParser.parse(key.reference, normalizer, Scripture.BUDDHISM, BibleVersion.LOTUS_SUTRA)
            assertEquals(ReferenceQuery(76,25,1,1), parsed)
            val analects = VerseKey(68,2,15)
            assertEquals(ReferenceQuery(68,2,15,15), ReferenceParser.parse(analects.reference, normalizer, Scripture.ANALECTS, BibleVersion.ANALECTS))
        } finally { I18n.setLanguage(context, "system") }
    }
}
