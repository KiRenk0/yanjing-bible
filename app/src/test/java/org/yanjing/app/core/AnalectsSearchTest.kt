package org.yanjing.app.core

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.yanjing.app.core.semantic.*

class AnalectsSearchTest {
    companion object {
        private val assets = File("src/main/assets")
        private val normalizer = TextNormalizer(File(assets,"t2s.tsv").readLines().associate { it.split('\t').let { f -> f[0][0] to f[1][0] } })
        private val engine by lazy {
            SearchEngine(File(assets,"bibles/analects.tsv").readLines().map { line ->
                val f = line.split('\t'); Verse(VerseKey(f[0].toInt(),f[1].toInt(),f[2].toInt()),f[3],f[4],searchGloss=f[5])
            }, normalizer, BibleVersion.ANALECTS)
        }
    }

    @Test fun completeCorpusAndGlossesHaveContinuousIndependentKeys() {
        val expected = Analects.verseCounts.flatMapIndexed { i,n -> (1..n).map { VerseKey(68,i+1,it) } }
        assertEquals(512,expected.size); assertEquals(expected,engine.verses.map { it.key })
        assertEquals(20,engine.chapters(68))
        assertTrue(engine.verses.all { it.searchGloss.isNotBlank() && it.normalized == normalizer.normalize(it.text) })
        assertTrue(engine.verse(VerseKey(68,1,1))!!.text.startsWith("子曰：“学而时习之"))
        assertTrue(engine.verse(VerseKey(68,20,3))!!.text.contains("不知命"))
        assertEquals(listOf(BibleVersion.ANALECTS),BibleVersion.ANALECTS.peers)
        assertFalse(BibleVersion.CUVS.acceptsBook(68)); assertFalse(BibleVersion.QURAN_ZH.acceptsBook(68))
        assertFalse(BibleVersion.ANALECTS.acceptsBook(43)); assertFalse(BibleVersion.ANALECTS.acceptsBook(67))
    }

    @Test fun referencesSupportNamedChaptersRangesTraditionalAndChineseNumerals() {
        listOf("论语 1:1", "論語１：１", "学而1", "学而篇第一章", "论语第一篇第一章", "《论语》学而1", "论语·学而 1:1").forEach { query ->
            assertEquals(query,VerseKey(68,1,1),engine.search(query).hits.single().verse.key)
        }
        assertEquals(16,engine.search("学而篇").total)
        assertEquals(listOf(1,2,3),engine.search("学而1-3").hits.map { it.verse.key.verse })
        assertEquals(VerseKey(68,15,24),engine.search("论语第十五篇第二十四章").hits.single().verse.key)
        assertEquals(VerseKey(68,15,24),engine.search("衛靈公24").hits.single().verse.key)
        assertEquals(3,engine.search("尧曰篇").total)
        engine.verses.forEach { assertEquals(it.key,engine.search(it.key.reference).hits.single().verse.key) }
    }

    @Test fun invalidReferenceRangesAndSavedKeysAreRejected() {
        listOf("0:1", "21:1", "1:17", "20:4", "2:0", "2:3-1", "2:1-99999", "999999999999:1", "2:999999999999").forEach { query ->
            val response = engine.search(query)
            assertTrue(query,response.hits.isEmpty()); assertNotNull(query,response.message)
        }
        assertEquals(VerseKey(68,20,3),VerseKey.decode("68:20:3"))
        listOf("68:0:1","68:21:1","68:20:4","77:1:1").forEach { assertNull(it,VerseKey.decode(it)) }
        assertEquals(Scripture.ANALECTS,VerseKey(68,1,1).scripture)
    }

    @Test fun modernExpressionsFindOriginalsWhileKeywordSearchOnlySearchesOriginal() {
        val key = VerseKey(68,2,15)
        assertEquals(key,engine.search("学习要结合思考").hits.first().verse.key)
        assertTrue(engine.search("只学习不思考会迷惑").hits.any { it.verse.key == key })
        assertTrue(engine.search("只学习不思考会迷惑",SearchMode.KEYWORD).hits.isEmpty())
        assertEquals(key,engine.search("學而不思則罔",SearchMode.KEYWORD).hits.single().verse.key)
        // Both chapters quote the same maxim; either is a correct original passage.
        assertTrue(engine.search("自己不愿意的事不要强加给别人").hits.first().verse.key in setOf(VerseKey(68,15,24),VerseKey(68,12,2)))
        assertTrue(engine.search("能给我水喝吗").hits.all { it.verse.key.book == 68 })
    }

    @Test fun modelFindsUnlistedModernParaphrasesAndDeduplicatesOriginals() {
        val index = SemanticIndex(File(assets,"semantic/analects.i8").inputStream())
        assertTrue(index.count > 1024)
        val tokenizer = WordPieceTokenizer(File(assets,"semantic/vocab.txt").readLines())
        SentenceEmbedder(File(assets,"semantic/bge-small-zh-int8.onnx").absolutePath,tokenizer).use { model ->
            val cases = listOf(
                "不能把自己讨厌的待遇加到别人身上" to VerseKey(68,15,24),
                "读书时要动脑筋，不能光背下来" to VerseKey(68,2,15),
                "对方没理解我，我也不该发火" to VerseKey(68,1,1),
                "地位比我低的人也值得我去求教" to VerseKey(68,5,15),
                "有了过失却始终不肯纠正才真的错了" to VerseKey(68,15,30),
                "想尽快成功反而办不成，小便宜会坏了大事" to VerseKey(68,13,17),
                "每天检查自己有没有尽责、守约和复习功课" to VerseKey(68,1,4),
                "别让怒火殃及无辜，也别再犯已经犯过的错" to VerseKey(68,6,3),
                "天气严寒才能发现松树柏树的坚韧" to VerseKey(68,9,28),
                "意见有差别仍然可以友好相处" to VerseKey(68,13,23),
                "不能因为嫌弃发言的人就忽略他说对的内容" to VerseKey(68,15,23),
                "真正的孝心还包括发自内心敬重双亲" to VerseKey(68,2,7)
            )
            val failures = mutableListOf<String>()
            cases.forEach { (query,key) ->
                assertFalse(ExpressionLexicon.forVersion(BibleVersion.ANALECTS).any { query in it.phrases })
                val vector = model.encode("为这个句子生成表示以用于检索相关文章：$query")
                val hits = index.nearest(vector,5)
                if (hits.none { it.key == key && it.cosine >= 0.5f }) failures += "$query -> $hits"
                assertTrue(hits.all { it.key.book == 68 && engine.verse(it.key) != null })
                assertEquals(hits.size,hits.map { it.key }.distinct().size)
            }
            assertTrue(failures.joinToString("\n"),failures.isEmpty())
            assertEquals(512,index.nearest(model.encode("学习"),1000).size)
        }
    }

    @Test fun referencesBypassModelAndUnavailableModelKeepsModernExpressionSearch() = runBlocking {
        val hybrid = HybridSearch(normalizer) { _,_,_,_ -> error("unavailable") }
        assertEquals(VerseKey(68,2,15),hybrid.search("为政15",setOf(SearchMode.SEMANTIC),engine,Testament.ALL).hits.single().verse.key)
        val fallback = hybrid.search("学习要结合思考",setOf(SearchMode.SEMANTIC,SearchMode.EXPRESSION),engine,Testament.ALL)
        assertEquals(VerseKey(68,2,15),fallback.hits.first().verse.key)
        assertTrue(fallback.message!!.contains("句意搜索暂时不可用"))
    }
}
