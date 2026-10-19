package org.yanjing.app.core

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.yanjing.app.core.semantic.*

class ClassicsAndLatinSearchTest {
    private val assets=File("src/main/assets")
    private val normalizer=TextNormalizer(File(assets,"t2s.tsv").readLines().associate { it.split('\t').let { p->p[0][0] to p[1][0] } })
    private val latinMappings by lazy { File(assets,"latin/mapping.tsv").readLines().filterNot { it.startsWith('#')||it.isBlank() }.associate {
        val f=it.split('\t'); VerseKey.decode(f[0])!! to f[1].split('|').map { key->VerseKey.decode(key)!! }
    } }
    private fun engine(version: BibleVersion): SearchEngine = SearchEngine(File(assets,"bibles/${version.id}.tsv").readLines().map {
        val p=it.split('\t'); val key=VerseKey(p[0].toInt(),p[1].toInt(),p[2].toInt())
        Verse(key,p[3],p[4],searchGloss=p.getOrElse(5){""},canonicalKeys=if(version==BibleVersion.VULGATE)latinMappings[key].orEmpty() else emptyList())
    },normalizer,version)

    @Test fun completeWorksKeepDistinctContinuousOriginalParagraphsAndRoundTripReferences() {
        assertEquals(9,Classics.works.size)
        assertEquals(2456,Classics.works.sumOf { it.verseCounts.sum() })
        Classics.works.forEach { work ->
            val corpus=engine(work.version)
            val expected=work.verseCounts.flatMapIndexed { i,n->(1..n).map { VerseKey(work.book,i+1,it) } }
            assertEquals(expected,corpus.verses.map { it.key })
            assertEquals(listOf(work.version),work.version.peers)
            corpus.verses.forEach { verse ->
                assertEquals(verse.key,VerseKey.decode(verse.key.encoded))
                assertEquals(verse.key.reference,ReferenceParser.parse(verse.key.reference,normalizer,work.version.scripture,work.version)?.let { VerseKey(it.book,it.chapter,it.start!!).reference })
                assertEquals(normalizer.normalize(verse.text),verse.normalized)
            }
        }
        assertEquals(ReferenceQuery(74,8,null,null),Classics.parse("《道德經》第八章",normalizer,BibleVersion.TAO_TE_CHING))
        assertEquals(ReferenceQuery(75,1,1,2),Classics.parse("逍遥游1-2",normalizer,BibleVersion.ZHUANGZI))
        assertNull(VerseKey.decode("69:2:1"));assertNull(VerseKey.decode("74:82:1"));assertNull(VerseKey.decode("77:1:1"))
    }

    @Test fun expressionHintsFindOriginalsWhileKeywordSearchRemainsOriginalOnly() {
        val tao=engine(BibleVersion.TAO_TE_CHING)
        val query="像水一样帮助别人而不争"
        assertTrue(tao.search(query,SearchMode.EXPRESSION).hits.any { it.verse.key.chapter==8 && it.verse.text.contains("上善若水") })
        assertTrue(tao.search(query,SearchMode.KEYWORD).hits.isEmpty())
        assertTrue(tao.search("上善若水",SearchMode.KEYWORD).hits.any { it.verse.key==VerseKey(74,8,1) })
    }

    @Test fun lotusReferencesSupportFullTitleTraditionalNamesAndChapterUnits() {
        val version = BibleVersion.LOTUS_SUTRA
        val corpus = engine(version)
        listOf("法华经 25:1", "《妙法蓮華經》第二十五品第一段", "法華經２５：１", "普门品1", "观世音菩萨普门品 25:1").forEach { query ->
            assertEquals(query, ReferenceQuery(76,25,1,1), Classics.parse(query,normalizer,version))
            assertEquals(query, VerseKey(76,25,1), corpus.search(query,SearchMode.KEYWORD).hits.single().verse.key)
        }
        listOf("法华经第十六品", "如來壽量品", "妙法莲华经·如来寿量品第十六").forEach { query ->
            assertEquals(query, ReferenceQuery(76,16,null,null), Classics.parse(query,normalizer,version))
        }
        assertEquals(ReferenceQuery(73,32,1,1),Classics.parse("金刚经第三十二分第一段",normalizer,BibleVersion.DIAMOND_SUTRA))
        assertEquals(Scripture.BUDDHISM,VerseKey(76,25,1).scripture)
        assertEquals("第 25 品 · 观世音菩萨普门品",Classics.forVersion(version)!!.chapterTitle(25))
        listOf("法华经 29:1","法华经 25:0","法华经 25:2-1","法华经 25:999").forEach { query ->
            assertTrue(query,corpus.search(query,SearchMode.KEYWORD).hits.isEmpty())
        }
        assertNull(Classics.parse("法华经 25:99999999999999999999",normalizer,version))
        assertNull(Classics.parse("法华经 25:1-99999999999999999999",normalizer,version))
        assertNull(VerseKey.decode("76:29:1"))
        assertNull(VerseKey.decode("76:25:999"))
    }

    @Test fun lotusOriginalSearchKeepsHintsSeparateAndIncludesFinalChapter() {
        val corpus=engine(BibleVersion.LOTUS_SUTRA)
        val hit=corpus.search("我不敢轻于汝等",SearchMode.KEYWORD).hits.first()
        assertEquals(20,hit.verse.key.chapter)
        assertTrue(hit.verse.text.contains("我不敢輕於汝等"))
        assertTrue(hit.verse.searchGloss.contains("尊重每一个人"))
        assertTrue(corpus.search("尊重每一个人不轻视别人",SearchMode.KEYWORD).hits.isEmpty())
        assertTrue(corpus.search("尊重每一个人不轻视别人",SearchMode.EXPRESSION).hits.any { it.verse.key.chapter==20 })
        assertEquals(504,corpus.verses.size)
        assertTrue(corpus.verses.first().text.startsWith("如是我聞"))
        assertTrue(corpus.verses.last().text.contains("皆大歡喜，受持佛語，作禮而去"))
    }

    @Test fun modernParaphrasesRetrieveOriginalsWithActualModelAndProductionRanking() = runBlocking {
        val cases=listOf(
            Triple(BibleVersion.GREAT_LEARNING,"不在人前的时候也不要欺骗自己","所謂誠其意者"),
            Triple(BibleVersion.DOCTRINE_MEAN,"与大家友好相处但不盲从潮流","君子和而不流"),
            Triple(BibleVersion.MENCIUS,"即使受到权势压迫也不改变自己的志向","富貴不能淫"),
            Triple(BibleVersion.HEART_SUTRA,"心中没有牵挂障碍便不再害怕","心無罣礙"),
            Triple(BibleVersion.DIAMOND_SUTRA,"眼前的一切变化很快就像露珠和闪光","如夢幻泡影"),
            Triple(BibleVersion.TAO_TE_CHING,"水让万物得到好处却不与它们争斗","上善若水"),
            Triple(BibleVersion.ZHUANGZI,"人的寿命有尽头但可以学的知识没有尽头","吾生也有涯"),
            Triple(BibleVersion.LOTUS_SUTRA,"不要看低任何人，每个人都有成佛的可能","我不敢輕於汝等"),
            Triple(BibleVersion.LOTUS_SUTRA,"面对不同根性的人需要采取合适的方法来教导","無數方便，引導眾生令離諸著")
        )
        SentenceEmbedder(File(assets,"semantic/bge-small-zh-int8.onnx").absolutePath,WordPieceTokenizer(File(assets,"semantic/vocab.txt").readLines())).use { model ->
            val concepts=File(assets,"semantic/concepts.txt").readLines().filter { it.isNotBlank()&&!it.startsWith('#') }.map { it.split('|') }
            cases.forEach { (version,query,anchor) ->
                val corpus=engine(version)
                val expected=corpus.verses.filter { normalizer.normalize(it.text).contains(normalizer.normalize(anchor)) }.map { it.key }.toSet()
                assertTrue(expected.isNotEmpty())
                val index=SemanticIndex(File(assets,"semantic/${version.id}.i8").inputStream())
                val ranker=SemanticRanker(corpus.verses,normalizer,concepts,ExpressionLexicon.forVersion(version))
                val vector=model.encode("为这个句子生成表示以用于检索相关文章："+normalizer.forSemantic(query))
                val candidates=index.nearest(vector,120,include=ranker.seeds(query))
                val hits=ranker.rank(query,candidates,5)
                assertTrue("$version $query: $hits",hits.any { it.key in expected })
                assertTrue(hits.all { version.acceptsBook(it.key.book) })
            }
        }
    }

    @Test fun latinAllSeventyThreeBooksPreserveNumberingAndMappedChineseReferences() {
        val latin=engine(BibleVersion.VULGATE)
        assertEquals(35809,latin.verses.size);assertEquals(73,latin.verses.map { it.key.book }.distinct().size)
        latin.verses.forEach { assertEquals(it.key,VerseKey.decode(it.key.encoded));assertEquals(normalizer.normalizeLatin(it.text),it.normalized) }
        val psalm=latin.verse(VerseKey(19,23,1))!!
        assertEquals(VerseKey(121,22,1),psalm.key);assertTrue(psalm.text.contains("Dominus regit me"))
        assertEquals(VerseKey(132,4,1),latin.verse(VerseKey(27,4,4))!!.key)
        assertTrue(latin.verse(VerseKey(27,4,4))!!.text.startsWith("Ego Nabuchodonosor"))
        assertTrue(latin.verses.first { it.key==VerseKey(132,3,24) }.canonicalKeys.isEmpty())
        assertEquals(VerseKey(150,3,16),latin.verse(VerseKey(43,3,16))!!.key)
        assertTrue(latin.search("诗篇 22:1",SearchMode.KEYWORD).hits.single().verse.text.contains("Dominus regit me"))
        assertTrue(latin.search("caelum",SearchMode.KEYWORD).hits.isNotEmpty())
        assertTrue(latin.search("多比传 1:1",SearchMode.KEYWORD).hits.isNotEmpty())
        assertTrue(latin.search("多比传 1:1",SearchMode.KEYWORD,Testament.NEW).hits.isEmpty())
        assertTrue(latin.verses.all { !BibleVersion.CUVS.acceptsBook(it.key.book) })
    }
}
