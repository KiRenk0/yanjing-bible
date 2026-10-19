package org.yanjing.app.core

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.yanjing.app.core.semantic.*

class SemanticRankerTest {
    private val normalizer = TextNormalizer()
    private val groups = File("src/main/assets/semantic/concepts.txt").readLines()
        .filter { it.isNotBlank() && !it.startsWith('#') }.map { it.split('|') }
    private fun verse(chapter: Int, text: String) = Verse(VerseKey(68,chapter,1),text,normalizer.normalize(text))

    @Test fun semanticRecallCanScoreLexicalSeedsOutsideTheDenseTopK() {
        val index = SemanticIndex(File("src/main/assets/semantic/analects.i8").inputStream())
        val key = VerseKey(68,20,3)
        val query = FloatArray(512) { if (it == 0) 1f else 0f }
        val hits = index.nearest(query,1,include=setOf(key))
        assertTrue(hits.any { it.key == key }); assertTrue(hits.size <= 2)
        assertEquals(hits.size,hits.map { it.key }.distinct().size)
        assertTrue(index.nearest(query,1,accepts={ it.chapter == 1 },include=setOf(key)).none { it.key == key })
    }

    @Test fun synonymEvidenceResolvesAnAmbiguousMatch() {
        val useful = verse(1,"地位较低的人也可以向他请教，虚心学习。")
        val distracting = verse(2,"年轻人急于取得职位，却不愿学习。")
        val ranker = SemanticRanker(listOf(useful,distracting),normalizer,groups)
        val candidates = listOf(SemanticCandidate(distracting.key,0.61f),SemanticCandidate(useful.key,0.59f))
        assertEquals(useful.key,ranker.rank("向比我地位低的人求教",candidates).first().key)
        assertTrue(useful.key in ranker.seeds("向比我地位低的人求教"))
    }

    @Test fun weakCandidatesAndDistantTailsAreSuppressedWithoutFabricatingVerses() {
        val strong = verse(1,"学习的时候思考问题，复习知识。")
        val weak = verse(2,"朋友从很远的地方来到这里。")
        val ranker = SemanticRanker(listOf(strong,weak),normalizer,groups)
        assertTrue(ranker.rank("配置蓝牙耳机",listOf(SemanticCandidate(weak.key,0.4f))).isEmpty())
        val hits = ranker.rank("读书也要思考",listOf(SemanticCandidate(strong.key,0.8f),SemanticCandidate(weak.key,0.5f)))
        assertEquals(listOf(strong.key),hits.map { it.key })
        assertTrue(ranker.rank("读书",listOf(SemanticCandidate(VerseKey(68,3,1),0.99f))).isEmpty())
    }

    @Test fun fusionUsesSemanticStrengthAndDoesNotReintroduceRejectedWeakMatches() = runBlocking {
        val strong = verse(1,"认真学习和思考，尊重朋友。")
        val weak = verse(2,"交往朋友的时候需要认真。")
        val engine = SearchEngine(listOf(strong,weak),normalizer,BibleVersion.ANALECTS)
        val hybrid = HybridSearch(normalizer) { _,_,_,_ -> SearchResponse(listOf(SearchHit(strong,0.92,"句意相近")),1) }
        val result = hybrid.search("认真尊重朋友",setOf(SearchMode.SEMANTIC,SearchMode.EXPRESSION),engine,Testament.ALL)
        assertEquals(listOf(strong.key),result.hits.map { it.verse.key })
        val empty = HybridSearch(normalizer) { _,_,_,_ -> SearchResponse(emptyList(),0,"未找到足够相关的原文") }
        assertTrue(empty.search("认真尊重朋友",setOf(SearchMode.SEMANTIC,SearchMode.EXPRESSION),engine,Testament.ALL).hits.isEmpty())
        val unavailable = HybridSearch(normalizer) { _,_,_,_ -> error("native unavailable") }
        assertTrue(unavailable.search("认真尊重朋友",setOf(SearchMode.SEMANTIC,SearchMode.EXPRESSION),engine,Testament.ALL).hits.isNotEmpty())
    }
}
