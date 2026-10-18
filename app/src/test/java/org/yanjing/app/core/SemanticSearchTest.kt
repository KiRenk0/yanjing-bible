package org.yanjing.app.core

import java.io.File
import kotlin.math.sqrt
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.yanjing.app.core.semantic.*

class SemanticSearchTest {
    private val assets = File("src/main/assets")
    private fun tokenizer() = WordPieceTokenizer(File(assets, "semantic/vocab.txt").readLines())
    private fun engine(): SearchEngine = SearchEngine(File(assets,"bibles/cuvs.tsv").readLines().map { line ->
        val f = line.split('\t'); Verse(VerseKey(f[0].toInt(),f[1].toInt(),f[2].toInt()),f[3],f[4])
    }, TextNormalizer())

    @Test fun androidTokenizerMatchesPinnedHuggingFaceReference() {
        val samples = listOf(
            "为这个句子生成表示以用于检索相关文章：我忙得喘不过气，想歇一会儿" to longArrayOf(101,711,6821,702,1368,2094,4495,2768,6134,4850,809,4500,754,3466,5164,4685,1068,3152,4995,8038,2769,2564,2533,1596,679,6814,3698,8024,2682,3623,671,833,1036,102),
            "我想要喝水" to longArrayOf(101,2769,2682,6206,1600,3717,102),
            "ＡＢＣ  Café\t你好，世界！" to longArrayOf(101,100,100,872,1962,8024,686,4518,8013,102),
            "[CLS]中文[SEP]" to longArrayOf(101,101,704,3152,102,102),
            "help me 123🙂𠮷" to longArrayOf(101,10892,8450,100,100,102)
        )
        val tokenizer = tokenizer()
        samples.forEach { (text, ids) -> assertArrayEquals(text, ids, tokenizer.encode(text)) }
        val truncated = tokenizer.encode("水".repeat(600))
        assertEquals(256, truncated.size); assertEquals(101L, truncated.first()); assertEquals(102L, truncated.last())
    }

    @Test fun realOfflineModelFindsParaphrasesWithoutExpressionDictionary() {
        val index = SemanticIndex(File(assets,"semantic/verses.i8").inputStream())
        assertEquals(31034, index.count)
        SentenceEmbedder(File(assets,"semantic/bge-small-zh-int8.onnx").absolutePath, tokenizer()).use { model ->
            val expected = listOf(
                "做错了就坦白承认，不要掩饰" to VerseKey(20,28,13),
                "有困难的时候朋友才最重要" to VerseKey(20,17,17),
                "说话先过脑子，少发脾气" to VerseKey(20,10,19),
                "我需要一杯水解渴" to VerseKey(7,4,19)
            )
            expected.forEach { (query, key) ->
                val vector = model.encode("为这个句子生成表示以用于检索相关文章：$query")
                assertEquals(1.0, sqrt(vector.sumOf { it.toDouble() * it }), 0.0001)
                val result = index.nearest(vector, 5)
                assertTrue("$query -> ${result.map { it.key }}", result.any { it.key == key })
                assertTrue(index.nearest(vector, 3, accepts = { it.book >= 40 }).all { it.key.book >= 40 })
            }
        }
    }

    @Test fun multiSelectFusionKeepsAllModesAndDeduplicatesRealVerses() = runBlocking {
        val engine = engine(); val shared = requireNotNull(engine.verse(VerseKey(43,4,7)))
        val semantic = HybridSearch(TextNormalizer()) { _, _, _, _ -> SearchResponse(listOf(SearchHit(shared, 0.8,"句意相近")),1) }
        val result = semantic.search("给我水喝", SearchMode.entries.toSet(), engine, Testament.ALL)
        val hit = result.hits.first()
        assertEquals(shared.key, hit.verse.key)
        assertEquals(SearchMode.entries.toSet(), hit.matchedModes)
        assertEquals(result.hits.size, result.hits.map { it.verse.key }.distinct().size)
        assertTrue(semantic.search("我需要水", emptySet(),engine,Testament.ALL).hits.isEmpty())
        val competing = requireNotNull(engine.verse(VerseKey(7,4,19)))
        val displaced = HybridSearch(TextNormalizer()) { _,_,_,_ -> SearchResponse(listOf(SearchHit(competing,0.9,"句意相近")),1) }
        assertEquals(shared.key, displaced.search("能给我水喝吗",setOf(SearchMode.SEMANTIC,SearchMode.EXPRESSION),engine,Testament.ALL).hits.first().verse.key)
    }

    @Test fun unavailableSemanticModeIsReportedAndCancellationPropagates() = runBlocking {
        val engine = engine()
        val unavailable = HybridSearch(TextNormalizer()) { _,_,_,_ -> error("native unavailable") }
        val result = unavailable.search("给我水喝",setOf(SearchMode.SEMANTIC,SearchMode.KEYWORD),engine,Testament.ALL)
        assertTrue(result.hits.isNotEmpty()); assertTrue(result.message!!.contains("句意搜索暂时不可用"))
        assertTrue(result.hits.all { it.matchedModes == setOf(SearchMode.KEYWORD) })
        assertEquals(VerseKey(43,3,16), unavailable.search("约3:16",setOf(SearchMode.SEMANTIC),engine,Testament.ALL).hits.single().verse.key)
        val cancelled = HybridSearch(TextNormalizer()) { _,_,_,_ -> throw CancellationException("new query") }
        try { cancelled.search("喝水",setOf(SearchMode.SEMANTIC),engine,Testament.ALL); fail("must propagate cancellation") }
        catch (_: CancellationException) { }
    }
}
