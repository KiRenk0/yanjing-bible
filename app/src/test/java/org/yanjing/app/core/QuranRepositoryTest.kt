package org.yanjing.app.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.yanjing.app.data.*
import org.yanjing.app.ui.exportedVerse

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class QuranRepositoryTest {
    @Test fun semanticRepositorySwitchesIndexesAndBridgesChineseMeaningToArabic() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val repository=BibleRepository(context)
        val service=org.robolectric.Robolectric.buildService(SemanticService::class.java).create()
        val binder=org.yanjing.app.semantic.ISemanticEncoder.Stub.asInterface(service.get().onBind(android.content.Intent()))
        val semantic=SemanticRepository(context,repository.normalizer,object : SemanticEncoder {
            override suspend fun encode(text: String) = binder.encode(text)
        })
        try {
            val arabic=repository.load(BibleVersion.QURAN_AR)
            val result=semantic.search("对父母说话要有礼貌，不要流露厌烦",arabic,Testament.ALL,10)
            assertTrue(result.hits.any { it.verse.key==VerseKey(67,17,23) })
            assertTrue(result.hits.all { it.verse.key.book==67 && it.verse.text==arabic.verse(it.verse.key)?.text })
            val bible=repository.load(BibleVersion.CUVS)
            val biblical=semantic.search("做错了就坦白承认，不要掩饰",bible,Testament.ALL,10)
            assertTrue(biblical.hits.any { it.verse.key==VerseKey(20,28,13) })
            assertTrue(biblical.hits.all { it.verse.key.book in 1..66 })
            val analects = repository.load(BibleVersion.ANALECTS)
            val classical = semantic.search("读书时要动脑筋，不能光背下来",analects,Testament.ALL,5)
            assertTrue(classical.hits.toString(),classical.hits.any { it.verse.key == VerseKey(68,2,15) })
            assertTrue(classical.hits.all { it.verse.key.book == 68 && it.verse.text == analects.verse(it.verse.key)?.text })
            val restored = semantic.search("对父母说话要有礼貌，不要流露厌烦",arabic,Testament.ALL,10)
            assertTrue(restored.hits.any { it.verse.key == VerseKey(67,17,23) })
        } finally { semantic.close(); service.destroy() }
    }
    @Test fun comparisonsFavoritesAndExportsRetainScriptureAndAttribution() = runBlocking {
        val repository=BibleRepository(ApplicationProvider.getApplicationContext<Context>())
        val quran=requireNotNull(repository.load(BibleVersion.QURAN_ZH).verse(VerseKey(67,112,1)))
        val bible=requireNotNull(repository.load(BibleVersion.CUVS).verse(VerseKey(43,3,16)))
        val analects=requireNotNull(repository.load(BibleVersion.ANALECTS).verse(VerseKey(68,2,15)))
        repository.save(listOf(SavedVerse(BibleVersion.CUVS,bible),SavedVerse(BibleVersion.QURAN_ZH,quran),SavedVerse(BibleVersion.ANALECTS,analects)))
        assertEquals(setOf("cuvs/43:3:16","quran_zh/67:112:1","analects/68:2:15"),repository.saved().map { it.id }.toSet())
        assertEquals(mapOf(BibleVersion.ANALECTS to analects),repository.comparisons(analects.key))
        assertEquals(24,repository.context(BibleVersion.ANALECTS,analects.key).size)
        val export=exportedVerse(analects,BibleVersion.ANALECTS)
        assertTrue(export.contains(analects.text) && export.contains("论语·为政 2:15"))
        assertFalse(export.contains(analects.searchGloss))
        assertEquals(BibleVersion.QURAN_ZH.peers.toSet(),repository.comparisons(quran.key).keys)
        assertEquals(BibleVersion.CUVS.peers.toSet(),repository.comparisons(bible.key).keys)
        val copy=exportedVerse(quran,BibleVersion.QURAN_ZH)
        assertTrue(copy.contains("古兰经 112:1") && copy.contains("QuranEnc.com v1.0.2"))
        assertNull(Quran.chapterPrelude(9));assertNull(Quran.chapterPrelude(1))
        assertNotEquals(Quran.chapterPrelude(95),Quran.chapterPrelude(94))
    }
}
