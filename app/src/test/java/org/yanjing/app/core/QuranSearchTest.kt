package org.yanjing.app.core

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.yanjing.app.core.semantic.*

class QuranSearchTest {
    companion object {
        private val assets=File("src/main/assets")
        private val normalizer=TextNormalizer(File(assets,"t2s.tsv").readLines().map { it.split('\t').let { f -> f[0][0] to f[1][0] } }.toMap())
        private fun read(version:BibleVersion) = File(assets,"bibles/${version.id}.tsv").readLines().filter { it.isNotBlank() && !it.startsWith('#') }.map { line ->
            val f=line.split('\t',limit=5); Verse(VerseKey(f[0].toInt(),f[1].toInt(),f[2].toInt()),f[3],f[4])
        }
        private val chinese by lazy { SearchEngine(read(BibleVersion.QURAN_ZH),normalizer,BibleVersion.QURAN_ZH) }
        private val arabic by lazy { SearchEngine(read(BibleVersion.QURAN_AR),normalizer,BibleVersion.QURAN_AR) }
    }
    @Test fun completeCorporaKeepDistinctKeysAndVerbatimArabic() {
        val expected=Quran.verseCounts.flatMapIndexed { i,n -> (1..n).map { VerseKey(67,i+1,it) } }
        assertEquals(114,Quran.verseCounts.size); assertEquals(6236,expected.size)
        listOf(chinese,arabic).forEach { engine ->
            assertEquals(expected,engine.verses.map { it.key });assertEquals(114,engine.chapters(67))
            assertTrue(engine.verses.all { it.text.isNotBlank() && it.normalized.isNotBlank() })
        }
        val document=javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(assets,"quran/uthmani-v1.1.xml"))
        val nodes=document.getElementsByTagName("aya")
        val original=(0 until nodes.length).map { nodes.item(it).attributes.getNamedItem("text").nodeValue }
        assertEquals(original,arabic.verses.map { it.text })
        assertFalse(arabic.verse(VerseKey(67,112,1))!!.text.startsWith(Quran.basmala))
        assertEquals(Quran.basmala,arabic.verse(VerseKey(67,1,1))!!.text)
        assertTrue(File(assets,"bibles/quran_ar.tsv").readText().contains("CHANGING IT IS NOT ALLOWED"))
        assertTrue(File(assets,"bibles/quran_zh.tsv").readText().contains("v1.0.2-csv.1"))
    }
    @Test fun chapterVerseReferencesSupportChineseNamesAndUnicodeDigits() {
        listOf("2:255","古兰经 2:255","古蘭經２：２５５","Quran 2:255","古兰经第2章第255节","٢:٢٥٥","黄牛章 255").forEach { raw ->
            assertEquals(raw,VerseKey(67,2,255),chinese.search(raw).hits.single().verse.key)
        }
        assertEquals(listOf(5,6),chinese.search("古兰经94:5-6").hits.map { it.verse.key.verse })
        assertEquals(7,chinese.search("开端章").total)
        assertEquals(7,chinese.search("开端章1-7").total)
        assertEquals(6236,chinese.verses.size)
    }
    @Test fun invalidReferencesNeverReturnUnrelatedCandidates() {
        listOf("0:1","115:1","2:287","1:8","2:255-254","2:255-9999","9999999999999:1","2:9999999999999").forEach { query ->
            val response=chinese.search(query);assertTrue(query,response.hits.isEmpty());assertNotNull(query,response.message)
        }
    }
    @Test fun arabicSearchIgnoresVowelsAndChineseExpressionReferencesStayInQuran() {
        val plain=arabic.search("الرحمن",SearchMode.KEYWORD)
        assertTrue(plain.hits.isNotEmpty())
        assertEquals(plain.hits,arabic.search("ٱلرَّحْمَٰن",SearchMode.KEYWORD).hits)
        assertEquals(VerseKey(67,94,5),chinese.search("困难之后会有容易").hits.first().verse.key)
        assertEquals(VerseKey(67,94,5),arabic.search("困难之后会有容易").hits.first().verse.key)
        assertTrue(chinese.search("公平待人").hits.all { it.verse.key.book==67 })
        assertFalse(Testament.NEW.accepts(67)); assertFalse(Testament.OLD.accepts(67))
    }
    @Test fun quranKeysAndBibleFavoritesRemainUnambiguous() {
        assertEquals(VerseKey(67,114,6),VerseKey.decode("67:114:6"))
        assertEquals(VerseKey(43,3,16),VerseKey.decode("43:3:16"))
        assertNull(VerseKey.decode("67:114:7"));assertNull(VerseKey.decode("67:115:1"))
        assertEquals("古兰经 2:255",VerseKey(67,2,255).reference)
        assertEquals("约翰福音 3:16",VerseKey(43,3,16).reference)
        assertEquals(listOf(BibleVersion.QURAN_ZH,BibleVersion.QURAN_AR),BibleVersion.QURAN_ZH.peers)
        assertTrue(BibleVersion.CUVS.peers.none { it.isQuran })
    }
    @Test fun realQuranIndexUsesChineseMeaningAndReturnsOnlyQuranOriginals() {
        val index=SemanticIndex(File(assets,"semantic/quran.i8").inputStream())
        assertTrue(index.count>=6236)
        val tokenizer=WordPieceTokenizer(File(assets,"semantic/vocab.txt").readLines())
        SentenceEmbedder(File(assets,"semantic/bge-small-zh-int8.onnx").absolutePath,tokenizer).use { embedder ->
            val query="对父母说话要有礼貌，不要流露厌烦"
            val vector=embedder.encode("为这个句子生成表示以用于检索相关文章：$query")
            val hits=index.nearest(vector,10)
            assertTrue(hits.all { it.key.book==67 })
            assertTrue(hits.toString(),hits.any { it.key==VerseKey(67,17,23) })
            assertEquals(hits.size,hits.map { it.key }.distinct().size)
            assertEquals(6236,index.nearest(vector,7000).size)
            assertTrue(hits.all { arabic.verse(it.key)!=null && chinese.verse(it.key)!=null })
        }
    }
    @Test fun directQuranLookupBypassesSemanticAndKeepsMultipleModes() = runBlocking {
        val hybrid=HybridSearch(normalizer) { _,_,_,_ -> error("Direct lookup must bypass model") }
        val result=hybrid.search("古兰经 112:1-4",SearchMode.entries.toSet(),chinese,Testament.ALL)
        assertEquals(4,result.total);assertTrue(result.hits.all { it.verse.key.book==67 })
    }
}
