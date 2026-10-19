package org.yanjing.app.core

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test
import org.yanjing.app.core.semantic.*

/** Real model + shipped indexes + production ranker; judgments do not feed runtime ranking. */
class RetrievalEvaluationTest {
    private data class Case(val split: String, val corpus: String, val query: String, val expected: Set<VerseKey>)
    private data class Baseline(val rank: Int?, val hits: Int)
    private data class Corpus(val index: SemanticIndex, val ranker: SemanticRanker, val keys: Set<VerseKey>)

    @Test fun representativeQueriesImproveAgainstTheFrozenPreviousRelease() {
        val assets = File("src/main/assets")
        val normalizer = TextNormalizer(File(assets,"t2s.tsv").readLines().associate { it.split('\t').let { f -> f[0][0] to f[1][0] } })
        val groups = File(assets,"semantic/concepts.txt").readLines().filter { it.isNotBlank() && !it.startsWith('#') }.map { it.split('|') }
        val versions = mapOf("bible" to BibleVersion.CUVS,"quran" to BibleVersion.QURAN_ZH,"analects" to BibleVersion.ANALECTS)
        val corpora = versions.mapValues { (kind,version) ->
            val verses = File(assets,"bibles/${version.id}.tsv").readLines().filter { it.isNotBlank() && !it.startsWith('#') }.map { line ->
                val f = line.split('\t'); Verse(VerseKey(f[0].toInt(),f[1].toInt(),f[2].toInt()),f[3],f[4],searchGloss=f.getOrElse(5) { "" })
            }
            Corpus(SemanticIndex(File(assets,"semantic/${if (kind == "bible") "verses" else kind}.i8").inputStream()),
                SemanticRanker(verses,normalizer,groups,ExpressionLexicon.forVersion(version)),verses.map { it.key }.toSet())
        }
        val baselines = File("../data/semantic-baseline.tsv").readLines().filter { it.isNotBlank() && !it.startsWith('#') }.associate { line ->
            val f = line.split('\t'); "${f[0]}\t${f[1]}\t${f[2]}" to Baseline(f[3].toIntOrNull(),f[4].toInt())
        }
        val baselineHash = File("../data/semantic-baseline.tsv").readLines().first { it.startsWith("# datasetSha256=") }.substringAfter('=')
        val datasetHash = MessageDigest.getInstance("SHA-256").digest(File("../data/semantic-evaluation.tsv").readBytes()).joinToString("") { "%02x".format(it) }
        assertEquals("Recompute the previous-release baseline when judgments change",baselineHash,datasetHash)
        val cases = File("../data/semantic-evaluation.tsv").readLines().filter { it.isNotBlank() && !it.startsWith('#') }.flatMap { line ->
            val f = line.split('\t')
            val expected = if (f[3] == "-") emptySet() else f[3].split('|').map { requireNotNull(VerseKey.decode(it)) }.toSet()
            (if (f[1] == "all") versions.keys else setOf(f[1])).map { Case(f[0],it,f[2],expected) }
        }
        assertEquals(91,cases.size); assertEquals(cases.size,baselines.size)
        var before1 = 0; var before5 = 0; var after1 = 0; var after5 = 0; var beforeEmpty = 0; var afterEmpty = 0
        val report = StringBuilder("# split\tcorpus\tquery\tbaseline-rank\tnew-rank\tbaseline-hits\tnew-hits\tnew-top5\n")
        val tokenizer = WordPieceTokenizer(File(assets,"semantic/vocab.txt").readLines())
        SentenceEmbedder(File(assets,"semantic/bge-small-zh-int8.onnx").absolutePath,tokenizer).use { model ->
            cases.forEach { case ->
                val corpus = corpora.getValue(case.corpus)
                assertTrue("Invalid judgment: $case",corpus.keys.containsAll(case.expected))
                val baseline = baselines.getValue("${case.split}\t${case.corpus}\t${case.query}")
                val vector = model.encode("为这个句子生成表示以用于检索相关文章：" + normalizer.forSemantic(case.query))
                val candidates = corpus.index.nearest(vector,120,include=corpus.ranker.seeds(case.query))
                val hits = corpus.ranker.rank(case.query,candidates)
                val rank = hits.indexOfFirst { it.key in case.expected }.takeIf { it >= 0 }?.plus(1)
                assertTrue(hits.all { it.key in corpus.keys })
                assertEquals(hits.size,hits.map { it.key }.distinct().size)
                if (case.expected.isNotEmpty()) {
                    if (baseline.rank == 1) before1++
                    if (baseline.rank != null && baseline.rank <= 5) before5++
                    if (rank == 1) after1++
                    if (rank != null && rank <= 5) after5++
                } else {
                    if (baseline.hits == 0) beforeEmpty++
                    if (hits.isEmpty()) afterEmpty++
                }
                report.append(listOf(case.split,case.corpus,case.query,baseline.rank ?: "-",rank ?: "-",baseline.hits,hits.size,
                    hits.take(5).joinToString("|") { it.key.encoded }.ifEmpty { "-" }).joinToString("\t")).append('\n')
            }
        }
        val output = File("build/reports/semantic-evaluation.tsv").apply { parentFile?.mkdirs(); writeText(report.toString()) }
        val summary = "Top1 $before1 -> $after1, Top5 $before5 -> $after5, unrelated abstentions $beforeEmpty -> $afterEmpty; ${output.absolutePath}"
        println(summary)
        assertTrue(summary,after1 >= before1)
        assertTrue(summary,after5 >= before5 + 4)
        assertTrue(summary,afterEmpty >= beforeEmpty)
    }
}
