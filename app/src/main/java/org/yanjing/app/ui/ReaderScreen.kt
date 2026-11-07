@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.yanjing.app.ui

import org.yanjing.app.i18n.tr

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.yanjing.app.*
import org.yanjing.app.R
import org.yanjing.app.core.*

@Composable
fun ReaderScreen(state: AppState, model: AppViewModel) {
    var picker by remember { mutableStateOf(false) }
    val scroll = rememberLazyListState()
    LaunchedEffect(state.readerBook, state.readerChapter, state.version) { scroll.scrollToItem(0) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { model.read(state.readerBook, state.readerChapter - 1) }, enabled = !state.loading && state.readerChapter > 1) { Symbol(R.drawable.sym_arrow_back, if (state.version.isClassical) tr(R.string.ui_5e50d51846) else tr(R.string.ui_393f2a5120)) }
            FilledTonalButton(onClick = { picker = true }, modifier = Modifier.weight(1f), enabled = !state.loading) {
                Text(readerChapterTitle(state))
                Spacer(Modifier.width(8.dp)); Symbol(R.drawable.sym_expand_more)
            }
            IconButton(onClick = { model.read(state.readerBook, state.readerChapter + 1) }, enabled = !state.loading && state.readerChapter < state.chapterCount) { Symbol(R.drawable.sym_arrow_forward, if (state.version.isClassical) tr(R.string.ui_673ee5a5a4) else tr(R.string.ui_9443994bc3)) }
        }
        if (state.loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else if (state.error != null) EmptyState(R.drawable.sym_info, tr(R.string.ui_573afa897f), state.error, action = { Button(onClick = { model.loadVersion(state.version) }) { Text(tr(R.string.ui_b8784c8dd5)) } })
        else LazyColumn(state = scroll, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item {
                Text(state.version.title, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp))
                Text(if(state.version.isAnalects) tr(R.string.ui_c8886dc3da, Analects.chapterName(state.readerChapter)) else if(state.version.isQuran) tr(R.string.ui_d8fe21c999, state.readerChapter) else readerChapterTitle(state), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp))
                CorpusSource(state.version)
                if (state.version.isRtl) Quran.chapterPrelude(state.readerChapter)?.let { prelude ->
                    Text(prelude, style = verseTextStyle(state.version,state.fontSize), textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
                }
            }
            items(state.chapter.size, key = { state.chapter[it].key.encoded }) { i ->
                val verse = state.chapter[i]
                val saved = state.saved.any { it.verse.key == verse.key && it.version == state.version }
                Surface(onClick = { model.openDetail(verse) }, shape = MaterialTheme.shapes.medium,
                    color = if (saved) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(verse.key.verse.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.widthIn(min = 20.dp).padding(top = 5.dp))
                        Text(verse.text, style = verseTextStyle(state.version,state.fontSize,1.8f), modifier = Modifier.weight(1f))
                    }
                }
            }
            item { Text(if (state.version.isClassical) tr(R.string.ui_ca8b90f98d) else tr(R.string.ui_29d3c74283), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp)) }
        }
    }
    if (picker) {
        if (state.version.isAnalects) AnalectsChapterPicker(state.readerChapter, { picker = false }) { model.read(Analects.BOOK,it) }
        else if(state.version.isClassical) ClassicChapterPicker(Classics.forVersion(state.version)!!,state.readerChapter,{picker=false}) { model.read(state.version.defaultBook,it) }
        else if (state.version.isQuran) QuranChapterPicker(state.readerChapter, { picker = false }) { model.read(Quran.BOOK,it) }
        else ChapterPicker(state, { picker = false }, model::read)
    }
}

private fun readerChapterTitle(state: AppState): String = when {
    state.version.isClassical -> Classics.forVersion(state.version)!!.chapterTitle(state.readerChapter)
    state.version.isQuran -> Quran.chapterTitle(state.readerChapter)
    state.version==BibleVersion.VULGATE -> tr(R.string.ui_a8b275439c, Vulgate.bookName(state.readerBook), state.readerChapter)
    else -> tr(R.string.ui_94f19500d5, Books.names[state.readerBook-1], state.readerChapter)
}

@Composable
private fun ClassicChapterPicker(work: ClassicWork,current: Int,dismiss: () -> Unit,select: (Int) -> Unit) {
    AlertDialog(onDismissRequest=dismiss,title={Text(tr(R.string.ui_bd541337af, work.title))},text={
        LazyColumn(Modifier.heightIn(max=440.dp)) {
            items(work.names.size) { i -> ListItem(onClick={select(i+1);dismiss()},content={Text(work.chapterTitle(i+1))},
                supportingContent={Text(tr(R.string.ui_3efc94a541, work.verseCounts[i]))},trailingContent={if(current==i+1)Symbol(R.drawable.sym_check)}) }
        }
    },confirmButton={TextButton(onClick=dismiss){Text(tr(R.string.ui_2cd0f3be87))}})
}

@Composable
private fun AnalectsChapterPicker(current: Int, dismiss: () -> Unit, select: (Int) -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(tr(R.string.ui_99c63124e6)) }, text = {
        LazyColumn(Modifier.heightIn(max = 440.dp)) {
            items(Analects.names.size, key = { it }) { i ->
                ListItem(onClick = { select(i + 1); dismiss() },
                    content = { Text(Analects.chapterTitle(i + 1)) }, supportingContent = { Text(tr(R.string.ui_222425afab, Analects.verseCounts[i])) },
                    trailingContent = { if (current == i + 1) Symbol(R.drawable.sym_check) })
            }
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text(tr(R.string.ui_2cd0f3be87)) } })
}

@Composable
private fun QuranChapterPicker(current: Int, dismiss: () -> Unit, select: (Int) -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(tr(R.string.ui_9b4d803f44)) }, text = {
        LazyColumn(Modifier.heightIn(max = 440.dp)) {
            items(114, key = { it }) { i ->
                ListItem(onClick = { select(i + 1); dismiss() },
                    content = { Text(Quran.chapterTitle(i + 1)) }, supportingContent = { Text(tr(R.string.ui_4c1e9455db, Quran.verseCounts[i])) },
                    trailingContent = { if (current == i + 1) Symbol(R.drawable.sym_check) })
            }
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text(tr(R.string.ui_2cd0f3be87)) } })
}

@Composable
private fun ChapterPicker(state: AppState, dismiss: () -> Unit, select: (Int, Int) -> Unit) {
    var book by remember { mutableIntStateOf(state.readerBook) }
    val latin=state.version==BibleVersion.VULGATE
    var section by remember { mutableIntStateOf(if ((latin && book<147)||(!latin&&book<40)) 0 else 1) }
    // Canonical chapter counts, used only for the picker. The reader checks the actual local corpus.
    val chapterCounts = listOf(50,40,27,36,34,24,21,4,31,24,22,25,29,36,10,13,10,42,150,31,12,8,66,52,5,48,12,14,3,9,1,4,7,3,3,3,2,14,4,28,16,24,21,28,16,16,13,6,6,4,4,5,3,6,4,3,1,13,5,5,3,5,1,1,1,22)
    AlertDialog(onDismissRequest = dismiss, title = { Text(tr(R.string.ui_1a138c26f1)) },
        text = {
            Column(Modifier.heightIn(max = 500.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(tr(R.string.ui_f9c7acfecb), tr(R.string.ui_a18281e062)).forEachIndexed { i, label -> SegmentedButton(selected = section == i, onClick = { section = i }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) } }
                }
                Row(Modifier.padding(top = 12.dp).heightIn(max = 380.dp)) {
                    val books = if(latin) { if(section==0)(101..146).toList() else(147..173).toList() } else if (section == 0) (1..39).toList() else (40..66).toList()
                    LazyColumn(Modifier.weight(1.2f)) {
                        items(books.size) { i ->
                            val b = books[i]
                            Surface(onClick = { book = b }, shape = MaterialTheme.shapes.medium,
                                color = if (book == b) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
                                Text(if(latin)Vulgate.bookName(b) else Books.names[b - 1], style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp))
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    LazyColumn(Modifier.weight(1f)) {
                        item { Text(if(latin)Vulgate.bookName(book) else Books.shortNames[book - 1], style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(12.dp)) }
                        items(if(latin)Vulgate.chapterCount(book) else chapterCounts[book - 1]) { i ->
                            Surface(onClick = { select(book, i + 1); dismiss() }, shape = MaterialTheme.shapes.medium) {
                                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(tr(R.string.ui_e0bb0e98a0, i + 1), modifier = Modifier.weight(1f))
                                    if (book == state.readerBook && i + 1 == state.readerChapter) Symbol(R.drawable.sym_check)
                                }
                            }
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = dismiss) { Text(tr(R.string.ui_2cd0f3be87)) } })
}
