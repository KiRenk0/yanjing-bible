package org.yanjing.app.data

import android.content.Context
import androidx.core.content.edit
import org.yanjing.app.core.*
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SavedVerse(val version: BibleVersion, val verse: Verse) {
    val id: String get() = "${version.id}/${verse.key.encoded}"
}

class BibleRepository(context: Context) {
    private val assets = context.applicationContext.assets
    private val preferences = context.applicationContext.getSharedPreferences("yanjing", Context.MODE_PRIVATE)
    val normalizer: TextNormalizer by lazy {
        val mapping = assets.open("t2s.tsv").bufferedReader().useLines { lines ->
            lines.mapNotNull { line -> line.split('\t').takeIf { it.size == 2 }?.let { it[0][0] to it[1][0] } }.toMap()
        }
        TextNormalizer(mapping)
    }
    private fun parse(line: String): Verse {
        val fields = line.split('\t', limit = 6)
        require(fields.size in 5..6) { "经文数据格式有误" }
        return Verse(VerseKey(fields[0].toInt(), fields[1].toInt(), fields[2].toInt()), fields[3], fields[4], searchGloss = fields.getOrElse(5) { "" })
    }
    private val latinMappings: Map<VerseKey,List<VerseKey>> by lazy {
        assets.open("latin/mapping.tsv").bufferedReader().useLines { lines -> lines.filter { it.isNotBlank() && !it.startsWith('#') }.map { line ->
            val fields=line.split('\t'); requireNotNull(VerseKey.decode(fields[0])) to fields[1].split('|').mapNotNull(VerseKey::decode)
        }.toMap() }
    }
    suspend fun load(version: BibleVersion): SearchEngine = withContext(Dispatchers.IO) {
        val verses = assets.open("bibles/${version.id}.tsv").bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith('#') }.map(::parse).map { verse ->
                if(version==BibleVersion.VULGATE) verse.copy(canonicalKeys=latinMappings[verse.key].orEmpty()) else verse
            }.toList()
        }
        SearchEngine(verses, normalizer, version)
    }
    suspend fun comparisons(key: VerseKey): Map<BibleVersion, Verse?> = withContext(Dispatchers.IO) {
        BibleVersion.entries.filter { if(Classics.forBook(key.book) != null) it.acceptsBook(key.book) else it.scripture == key.scripture }.associateWith { version ->
            val canonical = if(key.book in 101..173) latinMappings[key]?.firstOrNull() else key
            val requested = if(version==BibleVersion.VULGATE) {
                if(key.book in 101..173) key else latinMappings.entries.firstOrNull { key in it.value }?.key
            } else canonical
            if(requested==null) return@associateWith null
            val actual = VerseMappings.resolve(version, requested)
            val prefix = "${actual.book}\t${actual.chapter}\t${actual.verse}\t"
            assets.open("bibles/${version.id}.tsv").bufferedReader().useLines { lines ->
                lines.firstOrNull { it.startsWith(prefix) }?.let(::parse)
            }
        }
    }
    suspend fun context(version: BibleVersion, key: VerseKey): List<Verse> = withContext(Dispatchers.IO) {
        val prefix = "${key.book}\t${key.chapter}\t"
        assets.open("bibles/${version.id}.tsv").bufferedReader().useLines { lines ->
            lines.filter { it.startsWith(prefix) }.map(::parse).toList()
        }
    }
    fun saved(): List<SavedVerse> = runCatching {
        val array = JSONArray(preferences.getString("saved", "[]"))
        (0 until array.length()).mapNotNull { index ->
            runCatching {
                val item = array.getJSONObject(index)
                val version = BibleVersion.entries.first { it.id == item.getString("version") }
                val key = requireNotNull(VerseKey.decode(item.getString("key")))
                require(version.acceptsBook(key.book))
                val text = item.getString("text")
                SavedVerse(version, Verse(key, text, if (version.isRtl) normalizer.normalizeArabic(text) else normalizer.normalize(text)))
            }.getOrNull()
        }
    }.getOrDefault(emptyList())
    fun save(items: List<SavedVerse>) {
        val array = JSONArray()
        items.forEach { array.put(JSONObject().put("version", it.version.id).put("key", it.verse.key.encoded).put("text", it.verse.text)) }
        preferences.edit { putString("saved", array.toString()) }
    }
    fun history(): List<String> = runCatching {
        val array = JSONArray(preferences.getString("history", "[]"))
        (0 until array.length()).map { array.getString(it) }
    }.getOrDefault(emptyList())
    fun setHistory(items: List<String>) { preferences.edit { putString("history", JSONArray(items).toString()) } }
    fun setting(key: String, default: String) = preferences.getString(key, default) ?: default
    fun setting(key: String, value: String, persist: Boolean = true) { if (persist) preferences.edit { putString(key, value) } }
    fun notices(): String = assets.open("NOTICE.md").bufferedReader().use { it.readText() }
    fun licenses(): String = listOf("scrollmapper-LICENSE.txt", "opencc-js-LICENSE.txt", "Apache-2.0.txt", "bge-LICENSE.txt", "onnxruntime-LICENSE.txt", "onnxruntime-ThirdPartyNotices.txt", "Tanzil-NOTICE.txt", "QuranEnc-TERMS.txt", "chinese-poetry-LICENSE.txt", "banghuazhao-LICENSE.txt", "hooosberg-LICENSE.txt", "GPL-2.0.txt", "CBETA-NOTICE.txt").joinToString("\n\n") { name ->
        assets.open(name).bufferedReader().use { it.readText() }
    }
    fun counts(): Map<String, Int> {
        val json = JSONObject(assets.open("data-manifest.json").bufferedReader().use { it.readText() }).getJSONArray("versions")
        return (0 until json.length()).associate { json.getJSONObject(it).let { o -> o.getString("id") to o.getInt("verses") } } +
            mapOf(BibleVersion.QURAN_ZH.id to 6236, BibleVersion.QURAN_AR.id to 6236, BibleVersion.VULGATE.id to Vulgate.verses) + Classics.works.associate { it.version.id to it.verseCounts.sum() }
    }
}
