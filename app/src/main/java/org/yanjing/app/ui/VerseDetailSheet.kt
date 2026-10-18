@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.yanjing.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.yanjing.app.*
import org.yanjing.app.R
import org.yanjing.app.core.*

@Composable
fun VerseDetailSheet(detail: DetailState, state: AppState, model: AppViewModel,
                     save: (Verse, BibleVersion) -> Unit, copy: (Verse, BibleVersion) -> Unit, onRead: () -> Unit) {
    var tab by remember(detail.key) { mutableIntStateOf(0) }
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = model::closeDetail, sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(detail.key.reference, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = model::closeDetail) { Symbol(R.drawable.sym_close, "关闭经文详情") }
            }
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("版本对照") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("上下文") })
            }
            when {
                detail.loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                detail.error != null -> EmptyState(R.drawable.sym_info, "加载失败", detail.error)
                else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (tab == 0) {
                        item { Text("同一出处的不同文本。部分版本分节方式可能不同。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        items(BibleVersion.entries.size, key = { BibleVersion.entries[it].id }) { i ->
                            val version = BibleVersion.entries[i]
                            val verse = detail.comparisons[version]
                            OutlinedCard {
                                Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(version.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                                        if (verse != null) IconButton(onClick = { save(verse, version) }) {
                                            val saved = state.saved.any { it.verse.key == verse.key && it.version == version }
                                            Symbol(if (saved) R.drawable.sym_bookmark_filled else R.drawable.sym_bookmark, if (saved) "取消收藏${version.title}" else "收藏${version.title}")
                                        }
                                    }
                                    if (verse != null && verse.key != detail.key) Text("此版合并于 ${verse.key.reference}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                                    SelectionContainer { Text(verse?.text ?: "此版本没有相同节号，请核对上下文。", style = MaterialTheme.typography.bodyLarge.copy(fontSize = state.fontSize.sp, lineHeight = (state.fontSize * 1.65f).sp), modifier = Modifier.padding(end = 8.dp)) }
                                    if (verse != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        TextButton(onClick = { shareVerse(context, verse, version) }) { Symbol(R.drawable.sym_share); Spacer(Modifier.width(8.dp)); Text("分享") }
                                        TextButton(onClick = { copy(verse, version) }) { Symbol(R.drawable.sym_content_copy); Spacer(Modifier.width(8.dp)); Text("复制") }
                                    }
                                }
                            }
                        }
                    } else {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(detail.version.title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                                TextButton(onClick = onRead) { Text("阅读整章"); Symbol(R.drawable.sym_arrow_forward) }
                            }
                        }
                        val verses = detail.context.filter { it.key.verse in (detail.key.verse - 3)..(detail.key.verse + 3) }
                        items(verses.size, key = { verses[it].key.encoded }) { i ->
                            val verse = verses[i]
                            val selected = verse.key == detail.key
                            Surface(shape = MaterialTheme.shapes.medium, color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("${verse.key.chapter}:${verse.key.verse}${if (selected) " · 当前经文" else ""}", style = MaterialTheme.typography.labelLarge)
                                    SelectionContainer { Text(verse.text, style = MaterialTheme.typography.bodyLarge.copy(fontSize = state.fontSize.sp, lineHeight = (state.fontSize * 1.65f).sp)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
