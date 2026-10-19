package org.yanjing.app.core

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class SearchEngineTest {
    companion object {
        private val assets = File("src/main/assets")
        private val mappings = File(assets, "t2s.tsv").readLines().map { it.split('\t').let { v -> v[0][0] to v[1][0] } }.toMap()
        private val normalizer = TextNormalizer(mappings)
        private fun read(version: String) = File(assets, "bibles/$version.tsv").useLines { lines -> lines.map { line ->
            val f = line.split('\t', limit = 5)
            Verse(VerseKey(f[0].toInt(), f[1].toInt(), f[2].toInt()), f[3], f[4])
        }.toList() }
        private val cuvs by lazy { SearchEngine(read("cuvs"), normalizer) }
        private val cuvt by lazy { SearchEngine(read("cuvt"), normalizer) }
        private val kjv by lazy { SearchEngine(read("kjv"), normalizer) }
    }

    @Test fun imageExamplesFindRealVersesNearTop() {
        val examples = mapOf(
            "可以给我水喝吗" to "43:4:7", "你们这里有什么吃的没有" to "42:24:41",
            "你也想去吗" to "43:6:67", "你说吧" to "11:2:14", "你们来吃早饭" to "43:21:12",
            "你上哪里去" to "12:5:25", "你手里拿的是什么" to "2:4:2", "你叫什么名字" to "7:13:17",
            "我不给你草" to "2:5:10", "他们不知道自己在做什么" to "42:23:34"
        )
        examples.forEach { (query, key) ->
            val result = cuvs.search(query)
            assertTrue("$query should find $key in top 3, got ${result.hits.take(3).map { it.verse.key.encoded }}", result.hits.take(3).any { it.verse.key.encoded == key })
            assertTrue(result.hits.all { cuvs.verse(it.verse.key)?.text == it.verse.text })
        }
    }

    @Test fun allBundledCorporaHave66Books1189ChaptersAndUniqueKeys() {
        BibleVersion.entries.filter { it.scripture == Scripture.BIBLE }.forEach { version ->
            val verses = read(version.id)
            assertTrue(verses.size > 30000)
            assertEquals(66, verses.map { it.key.book }.distinct().size)
            assertEquals(1189, verses.map { it.key.book to it.key.chapter }.distinct().size)
            assertEquals("duplicate keys in ${version.id}", verses.size, verses.map { it.key }.toSet().size)
            assertTrue(verses.all { it.text.isNotBlank() && it.text != "a" && !it.text.contains(Regex("<[^>]*>")) && it.normalized.isNotBlank() })
        }
    }

    @Test fun everyExpressionReferenceExistsInAllCorpora() {
        val keys = ExpressionLexicon.groups.flatMap { it.references }.toSet()
        BibleVersion.entries.filter { it.scripture == Scripture.BIBLE }.forEach { version ->
            val present = read(version.id).map { it.key }.toSet()
            val resolved = keys.map { VerseMappings.resolve(version, it) }.toSet()
            assertTrue("missing expression references in ${version.id}: ${resolved - present}", present.containsAll(resolved))
        }
    }

    @Test fun wenliMergedVersesKeepTheirRealSourceReference() {
        val wenli = SearchEngine(read("wenli"), normalizer, BibleVersion.WENLI)
        val hit = wenli.search("太 6:34").hits.single()
        assertEquals(VerseKey(40,6,33), hit.verse.key)
        assertTrue(hit.reason.contains("合并分节"))
        assertTrue(hit.verse.text.contains("明日"))
        assertEquals(1, wenli.search("太6:33-34").total)
        assertEquals(VerseKey(40,6,33), wenli.search("别为明天担心").hits.first().verse.key)
    }

    @Test fun cuvVerseBridgesResolveToRealTextAndDeduplicateRanges() {
        assertEquals(69, cuvVerseBridges.size)
        listOf(BibleVersion.CUVS, BibleVersion.CUVT).forEach { version ->
            val engine = SearchEngine(read(version.id), normalizer, version)
            cuvVerseBridges.forEach { (from, to) ->
                assertEquals(to, engine.verse(from)?.key)
                val hit = engine.search("${Books.names[from.book - 1]} ${from.chapter}:${from.verse}").hits.single()
                assertEquals(to, hit.verse.key)
                assertTrue(hit.reason.contains("合并分节"))
            }
            assertEquals(1, engine.search("民数记1:20-21").total)
        }
    }

    @Test fun supportsChineseShortAndEnglishReferencesAndRanges() {
        listOf("约 3:16-18", "約翰福音３：１６－１８", "John 3:16–18", "约翰福音3章16-18节").forEach { query ->
            val result = cuvs.search(query)
            assertEquals(query, listOf(16,17,18), result.hits.map { it.verse.key.verse })
            assertTrue(result.hits.all { it.reason == "出处定位" })
        }
        assertEquals(6, cuvs.search("诗篇 23").total)
        assertEquals(1, cuvs.search("1 John 1:9").total)
        assertEquals(66, ReferenceParser.parse("启 1:1", normalizer)?.book)
    }

    @Test fun invalidReferencesNeverReturnUnrelatedVerses() {
        listOf("约 0:1", "约 99:1", "约 3:999", "约 3:18-16", "约 3:16-999").forEach { query ->
            val result = cuvs.search(query)
            assertTrue(query, result.hits.isEmpty())
            assertNotNull(query, result.message)
        }
    }

    @Test fun traditionalAndSimplifiedQueriesHaveSameResults() {
        assertEquals(cuvs.search("不要忧虑").hits.map { it.verse.key }, cuvs.search("不要憂慮").hits.map { it.verse.key })
        assertEquals(cuvt.search("喝水").hits.map { it.verse.key }, cuvs.search("喝水").hits.map { it.verse.key })
        assertEquals("43:4:7", cuvt.search("能給我水喝嗎").hits.first().verse.key.encoded)
    }

    @Test fun exactSearchDoesNotExpandExpressions() {
        assertTrue(cuvs.search("我想喝水", SearchMode.KEYWORD).hits.isEmpty())
        val result = cuvs.search("给我水喝", SearchMode.KEYWORD)
        assertTrue(result.hits.isNotEmpty())
        assertTrue(result.hits.all { it.verse.normalized.contains("给我水喝") })
    }

    @Test fun filtersAreAppliedToDirectReferencesAndExpressionMatches() {
        assertTrue(cuvs.search("喝水", testament = Testament.OLD).hits.all { it.verse.key.book < 40 })
        assertTrue(cuvs.search("不要害怕", testament = Testament.NEW).hits.all { it.verse.key.book >= 40 })
        assertEquals(0, cuvs.search("John 3:16", testament = Testament.OLD).total)
    }

    @Test fun emptyUnmatchedAndLongQueriesAreHandled() {
        assertTrue(cuvs.search("").hits.isEmpty())
        assertTrue(cuvs.search("  ").hits.isEmpty())
        assertTrue(cuvs.search("！？").hits.isEmpty())
        assertTrue(cuvs.search("zzzzxyq987").hits.isEmpty())
        assertNotNull(cuvs.search("水".repeat(201)).message)
    }

    @Test fun resultLimitsKeepTheTrueTotal() {
        val result = cuvs.search("神", SearchMode.KEYWORD, limit = 7)
        assertEquals(7, result.hits.size)
        assertTrue(result.total > 100)
    }

    @Test fun englishSearchIsCaseInsensitiveAndChineseExpressionsWorkAcrossVersions() {
        assertEquals(kjv.search("GOD SO LOVED", SearchMode.KEYWORD).hits, kjv.search("god so loved", SearchMode.KEYWORD).hits)
        assertEquals("43:4:7", kjv.search("能给我水喝吗").hits.first().verse.key.encoded)
    }

    @Test fun chapterReadingAndPersistenceKeysStayWithinCorpus() {
        assertEquals(21, cuvs.chapters(43))
        assertEquals(51, cuvs.chapter(43, 1).size)
        assertEquals(VerseKey(43,3,16), VerseKey.decode("43:3:16"))
        assertNull(VerseKey.decode("69:1:1"))
        assertNull(VerseKey.decode("43:0:1"))
        assertNull(VerseKey.decode("not-a-reference"))
    }
}
