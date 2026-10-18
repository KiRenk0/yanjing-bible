package org.yanjing.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.yanjing.app.core.*
import org.yanjing.app.data.*
import org.yanjing.app.update.UpdateManager

enum class Appearance(val title: String) { SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色") }
data class DetailState(val key: VerseKey, val version: BibleVersion, val comparisons: Map<BibleVersion, Verse?> = emptyMap(), val context: List<Verse> = emptyList(), val loading: Boolean = true, val error: String? = null)
data class AppState(
    val version: BibleVersion = BibleVersion.CUVS,
    val query: String = "", val modes: Set<SearchMode> = setOf(SearchMode.SEMANTIC, SearchMode.EXPRESSION),
    val testament: Testament = Testament.ALL,
    val response: SearchResponse = SearchResponse(emptyList(), 0),
    val loading: Boolean = true, val searching: Boolean = false, val error: String? = null,
    val saved: List<SavedVerse> = emptyList(), val history: List<String> = emptyList(),
    val readerBook: Int = 43, val readerChapter: Int = 1, val chapterCount: Int = 21,
    val chapter: List<Verse> = emptyList(), val detail: DetailState? = null,
    val appearance: Appearance = Appearance.SYSTEM, val dynamicColor: Boolean = true,
    val fontSize: Float = 18f, val counts: Map<String, Int> = emptyMap()
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BibleRepository(application)
    private val semanticDelegate = lazy { SemanticRepository(application, repository.normalizer) }
    private val semantic by semanticDelegate
    val updates = UpdateManager(application)
    private val mutable = MutableStateFlow(AppState())
    val state = mutable.asStateFlow()
    private var engine: SearchEngine? = null
    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private var detailJob: Job? = null

    init {
        val version = BibleVersion.entries.firstOrNull { it.id == repository.setting("version", "cuvs") } ?: BibleVersion.CUVS
        val appearance = Appearance.entries.firstOrNull { it.name == repository.setting("appearance", "SYSTEM") } ?: Appearance.SYSTEM
        mutable.update { it.copy(version = version, appearance = appearance,
            modes = repository.setting("searchModes", "SEMANTIC,EXPRESSION").split(',').mapNotNull { value -> SearchMode.entries.firstOrNull { it.name == value } }.toSet().ifEmpty { setOf(SearchMode.SEMANTIC, SearchMode.EXPRESSION) },
            dynamicColor = repository.setting("dynamic", "true").toBoolean(),
            fontSize = repository.setting("font", "18").toFloatOrNull()?.coerceIn(16f, 28f) ?: 18f,
            readerBook = repository.setting("readerBook", "43").toIntOrNull()?.takeIf(version::acceptsBook) ?: version.defaultBook,
            readerChapter = repository.setting("readerChapter", "1").toIntOrNull()?.coerceAtLeast(1) ?: 1) }
        loadVersion(version)
        updates.start()
    }

    fun loadVersion(version: BibleVersion) {
        searchJob?.cancel(); loadJob?.cancel(); closeDetail(); engine = null
        val previous = mutable.value
        val changingScripture = previous.version.scripture != version.scripture
        if (changingScripture) {
            repository.setting("readerBook-${previous.version.scripture.name}", previous.readerBook.toString(), true)
            repository.setting("readerChapter-${previous.version.scripture.name}", previous.readerChapter.toString(), true)
        }
        val book = if (changingScripture) repository.setting("readerBook-${version.scripture.name}", version.defaultBook.toString()).toIntOrNull()?.takeIf(version::acceptsBook) ?: version.defaultBook
            else previous.readerBook.takeIf(version::acceptsBook) ?: version.defaultBook
        val chapter = if (changingScripture) repository.setting("readerChapter-${version.scripture.name}", "1").toIntOrNull()?.coerceAtLeast(1) ?: 1 else previous.readerChapter
        mutable.update { it.copy(version = version, readerBook = book, readerChapter = chapter, testament = if (changingScripture) Testament.ALL else it.testament,
            query = if (changingScripture) "" else it.query, loading = true, searching = false, error = null, response = SearchResponse(emptyList(),0), chapter = emptyList()) }
        repository.setting("version", version.id, true)
        repository.setting("version-${version.scripture.name}", version.id, true)
        loadJob = viewModelScope.launch {
            try {
                val loaded = repository.load(version)
                val saved = withContext(Dispatchers.IO) { repository.saved() }
                val counts = withContext(Dispatchers.IO) { repository.counts() }
                ensureActive()
                engine = loaded
                val s = mutable.value
                val count = loaded.chapters(s.readerBook)
                val chapter = s.readerChapter.coerceIn(1, count.coerceAtLeast(1))
                mutable.update { it.copy(loading = false, saved = saved, history = repository.history(), counts = counts,
                    chapterCount = count, readerChapter = chapter, chapter = loaded.chapter(s.readerBook, chapter)) }
                repository.setting("readerBook", s.readerBook.toString(), true)
                repository.setting("readerChapter", chapter.toString(), true)
                searchNow(false)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(loading = false, error = "经文库加载失败，请重试。") } }
        }
    }
    fun query(value: String) {
        mutable.update { it.copy(query = value.take(200)) }
        searchNow(true)
    }
    fun scripture(value: Scripture) {
        if (value == mutable.value.version.scripture) return
        val fallback = if (value == Scripture.QURAN) BibleVersion.QURAN_ZH else BibleVersion.CUVS
        val remembered = repository.setting("version-${value.name}", fallback.id)
        loadVersion(BibleVersion.entries.firstOrNull { it.id == remembered && it.scripture == value } ?: fallback)
    }
    fun modes(value: Set<SearchMode>) {
        if (value.isEmpty()) return
        repository.setting("searchModes", value.sortedBy { it.ordinal }.joinToString(",") { it.name }, true)
        mutable.update { it.copy(modes = value.toSet()) }; searchNow(false)
    }
    fun testament(value: Testament) { mutable.update { it.copy(testament = value) }; searchNow(false) }
    fun submit() {
        val q = mutable.value.query.trim()
        if (q.isNotBlank()) {
            val history = (listOf(q) + mutable.value.history).distinct().take(12)
            repository.setHistory(history)
            mutable.update { it.copy(history = history) }
        }
        searchNow(false)
    }
    private fun searchNow(debounce: Boolean) {
        searchJob?.cancel()
        val currentEngine = engine ?: return
        val snapshot = mutable.value
        if (snapshot.query.isBlank()) {
            mutable.update { it.copy(response = SearchResponse(emptyList(),0), searching = false) }
            return
        }
        mutable.update { it.copy(searching = true) }
        searchJob = viewModelScope.launch {
            try {
                if (debounce) delay(240)
                val response = withContext(Dispatchers.Default) {
                    HybridSearch(repository.normalizer, semantic::search).search(snapshot.query, snapshot.modes, currentEngine, snapshot.testament)
                }
                ensureActive()
                mutable.update { it.copy(response = response, searching = false) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(searching = false, response = SearchResponse(emptyList(),0,"检索失败，请重试。")) } }
        }
    }
    fun clearHistory() { repository.setHistory(emptyList()); mutable.update { it.copy(history = emptyList()) } }
    fun toggleSave(verse: Verse, version: BibleVersion = mutable.value.version) {
        val item = SavedVerse(version, verse)
        val saved = if (mutable.value.saved.any { it.id == item.id }) mutable.value.saved.filterNot { it.id == item.id }
        else listOf(item) + mutable.value.saved
        repository.save(saved)
        mutable.update { it.copy(saved = saved) }
    }
    fun read(book: Int, chapter: Int) {
        if (!mutable.value.version.acceptsBook(book)) return
        val currentEngine = engine
        if (currentEngine == null) {
            mutable.update { it.copy(readerBook = book, readerChapter = chapter.coerceAtLeast(1)) }
            repository.setting("readerBook", book.toString(), true)
            repository.setting("readerChapter", chapter.coerceAtLeast(1).toString(), true)
            return
        }
        val count = currentEngine.chapters(book)
        val selectedChapter = chapter.coerceIn(1, count.coerceAtLeast(1))
        mutable.update { it.copy(readerBook = book, readerChapter = selectedChapter, chapterCount = count, chapter = currentEngine.chapter(book, selectedChapter)) }
        repository.setting("readerBook", book.toString(), true)
        repository.setting("readerChapter", selectedChapter.toString(), true)
    }
    fun openDetail(verse: Verse, version: BibleVersion = mutable.value.version) {
        detailJob?.cancel()
        mutable.update { it.copy(detail = DetailState(verse.key, version)) }
        detailJob = viewModelScope.launch {
            try {
                val comparisons = repository.comparisons(verse.key)
                val context = repository.context(version, verse.key)
                ensureActive()
                mutable.update { it.copy(detail = DetailState(verse.key, version, comparisons, context, false)) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(detail = it.detail?.copy(loading = false, error = "暂时无法加载，请关闭后重试。")) } }
        }
    }
    fun closeDetail() { detailJob?.cancel(); mutable.update { it.copy(detail = null) } }
    fun appearance(value: Appearance) { repository.setting("appearance", value.name, true); mutable.update { it.copy(appearance = value) } }
    fun dynamicColor(value: Boolean) { repository.setting("dynamic", value.toString(), true); mutable.update { it.copy(dynamicColor = value) } }
    fun fontSize(value: Float) { repository.setting("font", value.toString(), true); mutable.update { it.copy(fontSize = value) } }
    suspend fun legalText(): String = withContext(Dispatchers.IO) { repository.notices() + "\n\n" + repository.licenses() }
    override fun onCleared() {
        if (semanticDelegate.isInitialized()) semantic.close()
        updates.close()
        super.onCleared()
    }
}
