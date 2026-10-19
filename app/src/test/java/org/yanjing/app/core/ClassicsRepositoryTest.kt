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
@Config(sdk=[37])
class ClassicsRepositoryTest {
    @Test fun switchingBooksKeepsSemanticIndexesIsolatedAndLatinUsesMappedOriginals() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val repository=BibleRepository(context)
        val service=org.robolectric.Robolectric.buildService(SemanticService::class.java).create()
        val binder=org.yanjing.app.semantic.ISemanticEncoder.Stub.asInterface(service.get().onBind(android.content.Intent()))
        val semantic=SemanticRepository(context,repository.normalizer,object:SemanticEncoder { override suspend fun encode(text: String)=binder.encode(text) })
        try {
            val tao=repository.load(BibleVersion.TAO_TE_CHING)
            val water=semantic.search("水让万物得到好处却不与它们争斗",tao,Testament.ALL,5)
            assertTrue(water.hits.toString(),water.hits.any { it.verse.key==VerseKey(74,8,1) })
            val heart=repository.load(BibleVersion.HEART_SUTRA)
            val peace=semantic.search("心中没有牵挂障碍便不再害怕",heart,Testament.ALL,5)
            assertTrue(peace.hits.isNotEmpty());assertTrue(peace.hits.all { it.verse.key.book==72 })
            val latin=repository.load(BibleVersion.VULGATE)
            val psalm=semantic.search("耶和华像牧人照顾我，我什么都不缺",latin,Testament.ALL,5)
            assertTrue(psalm.hits.toString(),psalm.hits.any { it.verse.key==VerseKey(121,22,1)&&it.verse.text.contains("Dominus regit me") })
            assertTrue(psalm.hits.all { it.verse.key.book in 101..173 })
            val again=semantic.search("水让万物得到好处却不与它们争斗",tao,Testament.ALL,5)
            assertTrue(again.hits.any { it.verse.key==VerseKey(74,8,1) })
        } finally { semantic.close();service.destroy() }
    }

    @Test fun favoritesComparisonsAndExportsKeepTheRightWorkAndLatinNumbering() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>();val repository=BibleRepository(context)
        val selected=listOf(BibleVersion.GREAT_LEARNING,BibleVersion.MENCIUS,BibleVersion.HEART_SUTRA,BibleVersion.DIAMOND_SUTRA,BibleVersion.TAO_TE_CHING,BibleVersion.ZHUANGZI)
            .map { version -> SavedVerse(version,repository.load(version).verses.first()) }
        val latin=repository.load(BibleVersion.VULGATE).verse(VerseKey(19,23,1))!!
        repository.save(selected+SavedVerse(BibleVersion.VULGATE,latin))
        assertEquals((selected+SavedVerse(BibleVersion.VULGATE,latin)).map { it.id },repository.saved().map { it.id })
        for(item in selected) {
            assertEquals(setOf(item.version),repository.comparisons(item.verse.key).keys)
            assertTrue(exportedVerse(item.verse,item.version).startsWith(item.verse.text))
        }
        val comparisons=repository.comparisons(latin.key)
        assertEquals(VerseKey(19,23,1),comparisons[BibleVersion.CUVS]!!.key)
        assertEquals(latin.key,comparisons[BibleVersion.VULGATE]!!.key)
        assertTrue(comparisons[BibleVersion.CUVS]!!.text.contains("牧者"))
        val unmatched=repository.comparisons(VerseKey(117,1,1))
        assertNotNull(unmatched[BibleVersion.VULGATE]);assertNull(unmatched[BibleVersion.CUVS])
        assertTrue(exportedVerse(latin,BibleVersion.VULGATE).contains("22:1"))
    }
}
