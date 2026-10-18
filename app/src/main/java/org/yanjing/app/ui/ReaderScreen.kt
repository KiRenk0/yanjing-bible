@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.yanjing.app.ui

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
            IconButton(onClick = { model.read(state.readerBook, state.readerChapter - 1) }, enabled = !state.loading && state.readerChapter > 1) { Symbol(R.drawable.sym_arrow_back, "上一章") }
            FilledTonalButton(onClick = { picker = true }, modifier = Modifier.weight(1f), enabled = !state.loading) {
                Text(if (state.version.isQuran) Quran.chapterTitle(state.readerChapter) else "${Books.names[state.readerBook - 1]} · 第 ${state.readerChapter} 章")
                Spacer(Modifier.width(8.dp)); Symbol(R.drawable.sym_expand_more)
            }
            IconButton(onClick = { model.read(state.readerBook, state.readerChapter + 1) }, enabled = !state.loading && state.readerChapter < state.chapterCount) { Symbol(R.drawable.sym_arrow_forward, "下一章") }
        }
        if (state.loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else if (state.error != null) EmptyState(R.drawable.sym_info, "无法加载经文", state.error, action = { Button(onClick = { model.loadVersion(state.version) }) { Text("重试") } })
        else LazyColumn(state = scroll, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item {
                Text(state.version.title, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp))
                Text(if (state.version.isQuran) "古兰经 ${state.readerChapter}" else "${Books.names[state.readerBook - 1]} ${state.readerChapter}", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp))
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
            item { Text("点击任意一节，可收藏、复制并对照其他版本。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp)) }
        }
    }
    if (picker) {
        if (state.version.isQuran) QuranChapterPicker(state.readerChapter, { picker = false }) { model.read(Quran.BOOK,it) }
        else ChapterPicker(state, { picker = false }, model::read)
    }
}

@Composable
private fun QuranChapterPicker(current: Int, dismiss: () -> Unit, select: (Int) -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text("选择古兰经章节") }, text = {
        LazyColumn(Modifier.heightIn(max = 440.dp)) {
            items(114, key = { it }) { i ->
                ListItem(onClick = { select(i + 1); dismiss() },
                    content = { Text(Quran.chapterTitle(i + 1)) }, supportingContent = { Text("${Quran.verseCounts[i]} 节") },
                    trailingContent = { if (current == i + 1) Symbol(R.drawable.sym_check) })
            }
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text("取消") } })
}

@Composable
private fun ChapterPicker(state: AppState, dismiss: () -> Unit, select: (Int, Int) -> Unit) {
    var book by remember { mutableIntStateOf(state.readerBook) }
    var section by remember { mutableIntStateOf(if (book < 40) 0 else 1) }
    // Canonical chapter counts, used only for the picker. The reader checks the actual local corpus.
    val chapterCounts = listOf(50,40,27,36,34,24,21,4,31,24,22,25,29,36,10,13,10,42,150,31,12,8,66,52,5,48,12,14,3,9,1,4,7,3,3,3,2,14,4,28,16,24,21,28,16,16,13,6,6,4,4,5,3,6,4,3,1,13,5,5,3,5,1,1,1,22)
    AlertDialog(onDismissRequest = dismiss, title = { Text("选择书卷与章节") },
        text = {
            Column(Modifier.heightIn(max = 500.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("旧约", "新约").forEachIndexed { i, label -> SegmentedButton(selected = section == i, onClick = { section = i }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) } }
                }
                Row(Modifier.padding(top = 12.dp).heightIn(max = 380.dp)) {
                    val books = if (section == 0) (1..39).toList() else (40..66).toList()
                    LazyColumn(Modifier.weight(1.2f)) {
                        items(books.size) { i ->
                            val b = books[i]
                            Surface(onClick = { book = b }, shape = MaterialTheme.shapes.medium,
                                color = if (book == b) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
                                Text(Books.names[b - 1], style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp))
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    LazyColumn(Modifier.weight(1f)) {
                        item { Text(Books.shortNames[book - 1], style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(12.dp)) }
                        items(chapterCounts[book - 1]) { i ->
                            Surface(onClick = { select(book, i + 1); dismiss() }, shape = MaterialTheme.shapes.medium) {
                                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("第 ${i + 1} 章", modifier = Modifier.weight(1f))
                                    if (book == state.readerBook && i + 1 == state.readerChapter) Symbol(R.drawable.sym_check)
                                }
                            }
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = dismiss) { Text("取消") } })
}
