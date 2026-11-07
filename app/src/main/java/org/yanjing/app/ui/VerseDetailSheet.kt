@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.yanjing.app.ui

import org.yanjing.app.i18n.tr

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
                IconButton(onClick = model::closeDetail) { Symbol(R.drawable.sym_close, tr(R.string.ui_ab752a7650)) }
            }
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(if (detail.version.isClassical) tr(R.string.ui_d30cd3b64f) else tr(R.string.ui_18f35c09c9)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(tr(R.string.ui_6fc38ef360)) })
            }
            when {
                detail.loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                detail.error != null -> EmptyState(R.drawable.sym_info, tr(R.string.ui_d1d044826a), detail.error)
                else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (tab == 0) {
                        item { Text(if (detail.version.isClassical) tr(R.string.ui_bc8c5659e0) else tr(R.string.ui_ad84f54382), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        items(detail.version.peers.size, key = { detail.version.peers[it].id }) { i ->
                            val version = detail.version.peers[i]
                            val verse = detail.comparisons[version]
                            OutlinedCard {
                                Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(version.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                                        if (verse != null) IconButton(onClick = { save(verse, version) }) {
                                            val saved = state.saved.any { it.verse.key == verse.key && it.version == version }
                                            Symbol(if (saved) R.drawable.sym_bookmark_filled else R.drawable.sym_bookmark, if (saved) tr(R.string.ui_6f04186a09, version.title) else tr(R.string.ui_6069913efc, version.title))
                                        }
                                    }
                                    if (verse != null && verse.key != detail.key) Text(tr(R.string.ui_c56e3871a7, verse.key.reference), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                                    SelectionContainer { Text(verse?.text ?: tr(R.string.ui_abb0fd4d63), style = verseTextStyle(version,state.fontSize), modifier = Modifier.fillMaxWidth().padding(end = 8.dp)) }
                                    if (!verse?.searchGloss.isNullOrEmpty()) {
                                        Text(tr(R.string.ui_116add13fa), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                                        Text(verse.searchGloss, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp))
                                    }
                                    CorpusSource(version)
                                    if (verse != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        TextButton(onClick = { shareVerse(context, verse, version) }) { Symbol(R.drawable.sym_share); Spacer(Modifier.width(8.dp)); Text(tr(R.string.ui_7e564575eb)) }
                                        TextButton(onClick = { copy(verse, version) }) { Symbol(R.drawable.sym_content_copy); Spacer(Modifier.width(8.dp)); Text(tr(R.string.ui_63d90d9773)) }
                                    }
                                }
                            }
                        }
                    } else {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(detail.version.title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                                TextButton(onClick = onRead) { Text(if (detail.version.isClassical) tr(R.string.ui_b6d0d028d7) else tr(R.string.ui_3012027875)); Symbol(R.drawable.sym_arrow_forward) }
                            }
                        }
                        val verses = detail.context.filter { it.key.verse in (detail.key.verse - 3)..(detail.key.verse + 3) }
                        items(verses.size, key = { verses[it].key.encoded }) { i ->
                            val verse = verses[i]
                            val selected = verse.key == detail.key
                            Surface(shape = MaterialTheme.shapes.medium, color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(tr(R.string.ui_2a6bb329fb, verse.key.chapter, verse.key.verse, if (selected) tr(R.string.ui_073efc46ad) else ""), style = MaterialTheme.typography.labelLarge)
                                    SelectionContainer { Text(verse.text, style = verseTextStyle(detail.version,state.fontSize), modifier = Modifier.fillMaxWidth()) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
