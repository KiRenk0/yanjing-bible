@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.yanjing.app.ui

import org.yanjing.app.i18n.tr

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.yanjing.app.*
import org.yanjing.app.R
import org.yanjing.app.core.*
import kotlinx.coroutines.launch

@Composable
fun MatchScreen(state: AppState, model: AppViewModel, save: (Verse) -> Unit, copy: (Verse) -> Unit) {
    val searchBarState = rememberContainedSearchBarState()
    val textFieldState = rememberTextFieldState(state.query)
    val scope = rememberCoroutineScope()
    var searchSettings by remember { mutableStateOf(false) }
    val suggestions = searchSuggestions(state.version)
    LaunchedEffect(state.query) {
        if (textFieldState.text.toString() != state.query) textFieldState.edit { replace(0, length, state.query); selection = androidx.compose.ui.text.TextRange(length) }
    }
    LaunchedEffect(textFieldState) { snapshotFlow { textFieldState.text.toString() }.collect(model::query) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val collapse: () -> Unit = { scope.launch { searchBarState.animateToCollapsed() }; keyboard?.hide(); focus.clearFocus() }
    val submit: () -> Unit = { model.submit(); collapse() }
    val pick: (String) -> Unit = { model.query(it); model.submit(); collapse() }
    val inputField: @Composable () -> Unit = {
        SearchBarDefaults.InputField(textFieldState = textFieldState, searchBarState = searchBarState,
            onSearch = { model.query(it); submit() },
            placeholder = { Text(if (state.version.isAnalects) tr(R.string.ui_964663269e) else if(state.version.isClassical) tr(R.string.ui_875f2290ac) else if (state.version.isQuran) tr(R.string.ui_174fed9168) else if (SearchMode.SEMANTIC in state.modes) tr(R.string.ui_a84afb0ba0) else tr(R.string.ui_0002c0ee16)) },
            leadingIcon = {
                if (searchBarState.currentValue == SearchBarValue.Expanded) IconButton(onClick = collapse) { Symbol(R.drawable.sym_arrow_back, tr(R.string.ui_cb4e7a1ff5)) }
                else Symbol(R.drawable.sym_search)
            },
            trailingIcon = {
                Row {
                    if (state.query.isNotEmpty()) IconButton(onClick = { model.query("") }) { Symbol(R.drawable.sym_close, tr(R.string.ui_b6f00afa65)) }
                    if (searchBarState.currentValue == SearchBarValue.Expanded) IconButton(onClick = { searchSettings = true }) { Symbol(R.drawable.sym_settings, tr(R.string.ui_2874151bd3)) }
                }
            })
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SearchBar(state = searchBarState, inputField = inputField, colors = SearchBarDefaults.containedColors(searchBarState), modifier = Modifier.weight(1f))
            FilledTonalIconButton(onClick = { searchSettings = true }) { Symbol(R.drawable.sym_settings, tr(R.string.ui_2874151bd3)) }
        }
        ExpandedFullScreenContainedSearchBar(state = searchBarState, inputField = inputField) {
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                if (state.query.isNotBlank()) item {
                    ListItem(content = { Text(tr(R.string.ui_0e0ecd4c21, state.query)) }, leadingContent = { Symbol(R.drawable.sym_search) },
                        modifier = Modifier.fillMaxWidth(), supportingContent = { Text(tr(R.string.ui_3cdd980817, state.version.title)) },
                        trailingContent = { IconButton(onClick = submit) { Symbol(R.drawable.sym_arrow_forward, tr(R.string.ui_bdceb7de57)) } })
                    TextButton(onClick = submit, modifier = Modifier.padding(horizontal = 16.dp)) { Text(tr(R.string.ui_cac3809c50)) }
                }
                if (state.history.isNotEmpty()) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(tr(R.string.ui_3b5ac9baaa), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = model::clearHistory) { Text(tr(R.string.ui_1ef3de06b3)) }
                        }
                    }
                    items(state.history.size) { i ->
                        Surface(onClick = { pick(state.history[i]) }) {
                            ListItem(content = { Text(state.history[i]) }, leadingContent = { Symbol(R.drawable.sym_history) }, trailingContent = { Symbol(R.drawable.sym_arrow_forward) })
                        }
                    }
                }
                item { Text(tr(R.string.ui_fdcd6199e5), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp)) }
                items(suggestions.size) { i ->
                    Surface(onClick = { pick(suggestions[i]) }) {
                        ListItem(content = { Text(suggestions[i]) }, leadingContent = { Symbol(R.drawable.sym_auto_awesome) })
                    }
                }
            }
        }
        run {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(state.modes.sortedBy { it.ordinal }.joinToString(" + ") { it.title }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = { searchSettings = true }) { Text(tr(R.string.ui_c3e9c3c600)) }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Scripture.entries.forEach { scripture ->
                    FilterChip(selected = state.version.scripture == scripture, onClick = { model.scripture(scripture) }, label={Text(scripture.title)})
                }
            }
            if(state.version.isClassical) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                BibleVersion.entries.filter { it.scripture==state.version.scripture }.forEach { version ->
                    FilterChip(selected=state.version==version,onClick={model.loadVersion(version)},label={Text(Classics.forVersion(version)!!.title)})
                }
            }
            if (state.version.scripture == Scripture.BIBLE) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Testament.entries.forEach { testament ->
                    FilterChip(selected = state.testament == testament, onClick = { model.testament(testament) }, label = { Text(testament.title) },
                        leadingIcon = if (state.testament == testament) ({ Symbol(R.drawable.sym_check) }) else null)
                }
            }
            else Text(if(state.version.isAnalects) tr(R.string.ui_1e0a4f73f1) else if(state.version.isClassical) Classics.forVersion(state.version)!!.summary else tr(R.string.ui_7c3ff9a0e4), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            when {
                state.loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) { CircularProgressIndicator(); Text(tr(R.string.ui_2e12854ada)) } }
                state.error != null -> EmptyState(R.drawable.sym_info, tr(R.string.ui_40493fd844), state.error, action = { Button(onClick = { model.loadVersion(state.version) }) { Text(tr(R.string.ui_b8784c8dd5)) } })
                state.query.isBlank() -> WelcomeContent(state, pick)
                state.searching -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CircularProgressIndicator()
                        if (SearchMode.SEMANTIC in state.modes) Text(tr(R.string.ui_16778b4cd1), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                state.response.hits.isEmpty() -> EmptyState(R.drawable.sym_search, tr(R.string.ui_e7c66d9bed), state.response.message ?: if (state.version.isAnalects) tr(R.string.ui_51f7877be4) else tr(R.string.ui_f6a5dbf26d), Modifier.padding(top = 32.dp))
                else -> {
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(if (SearchMode.SEMANTIC in state.modes) tr(R.string.ui_2ace33819d, state.response.hits.size) else tr(R.string.ui_77fd8a97e7, state.response.total), style = MaterialTheme.typography.titleMedium)
                                Text(when {
                                    SearchMode.SEMANTIC in state.modes -> when (state.version.scripture) {
                                        Scripture.ANALECTS, Scripture.BUDDHISM, Scripture.TAOISM -> tr(R.string.ui_cdab8699f7)
                                        Scripture.QURAN -> tr(R.string.ui_27fff02351)
                                        Scripture.BIBLE -> tr(R.string.ui_b0dbc2f2b4)
                                    }
                                    state.modes.size > 1 -> tr(R.string.ui_530287e0b1)
                                    SearchMode.EXPRESSION in state.modes -> tr(R.string.ui_687a797e36)
                                    else -> tr(R.string.ui_5bedbb2bbb)
                                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                state.response.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                                CorpusSource(state.version)
                            }
                        }
                        items(state.response.hits.size, key = { state.response.hits[it].verse.key.encoded }) { i ->
                            val hit = state.response.hits[i]
                            VerseCard(hit.verse, state.version, state.saved.any { it.version == state.version && it.verse.key == hit.verse.key }, state.fontSize, hit.reason,
                                onOpen = { model.openDetail(hit.verse) }, onSave = { save(hit.verse) }, onCopy = { copy(hit.verse) })
                        }
                        if (state.response.total > state.response.hits.size) item { Text(tr(R.string.ui_2b12eb9c73, state.response.hits.size), style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
    if (searchSettings) SearchModesDialog(state.modes, { searchSettings = false }, model::modes)
}

@Composable
private fun SearchModesDialog(selected: Set<SearchMode>, dismiss: () -> Unit, apply: (Set<SearchMode>) -> Unit) {
    var pending by remember { mutableStateOf(selected) }
    AlertDialog(onDismissRequest = dismiss, icon = { Symbol(R.drawable.sym_settings) }, title = { Text(tr(R.string.ui_b4e019b615)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr(R.string.ui_a6b6b983b9), style = MaterialTheme.typography.bodyMedium)
                SearchMode.entries.forEach { mode ->
                    val toggle = { pending = if (mode in pending) pending - mode else pending + mode }
                    ListItem(modifier = Modifier.toggleable(value = mode in pending, role = Role.Checkbox, onValueChange = { toggle() }), leadingContent = { Checkbox(checked = mode in pending, onCheckedChange = null) },
                        supportingContent = { Text(mode.description) }) { Text(mode.title) }
                }
                Text(if (pending.isEmpty()) tr(R.string.ui_afeebd4e36) else tr(R.string.ui_c853617881), style = MaterialTheme.typography.bodySmall,
                    color = if (pending.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }, confirmButton = { TextButton(enabled = pending.isNotEmpty(), onClick = { apply(pending); dismiss() }) { Text(tr(R.string.ui_63c73c4730)) } },
        dismissButton = { TextButton(onClick = dismiss) { Text(tr(R.string.ui_2cd0f3be87)) } })
}

@Composable
private fun ColumnScope.WelcomeContent(state: AppState, pick: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Symbol(R.drawable.sym_auto_awesome)
                    Text(if (state.version.isAnalects) tr(R.string.ui_a70b1ce594) else if(state.version.isClassical) tr(R.string.ui_69cb65a50b) else tr(R.string.ui_2db8d1d045), style = MaterialTheme.typography.headlineMedium)
                    Text(if (state.version.isAnalects) tr(R.string.ui_24bdf2a299) else if(state.version.isClassical) tr(R.string.ui_ba508d5b85, Classics.forVersion(state.version)!!.title) else if (state.version.isQuran) tr(R.string.ui_c8c8e88dad) else tr(R.string.ui_afc8786371), style = MaterialTheme.typography.bodyLarge)
                    Text(tr(R.string.ui_c4da9d52e6), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr(R.string.ui_0641ada9bf), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    searchSuggestions(state.version).forEach { phrase -> AssistChip(onClick = { pick(phrase) }, label = { Text(phrase) }) }
                }
            }
        }
        if (state.history.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr(R.string.ui_3b5ac9baaa), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.history.take(4).forEach { phrase -> SuggestionChip(onClick = { pick(phrase) }, label = { Text(phrase) }) }
                }
            }
        }
        item {
            Text(if (state.version.isAnalects) tr(R.string.ui_a1571c3647) else if(state.version.isClassical) tr(R.string.ui_bca6e7c942) else if (state.version.isQuran) tr(R.string.ui_3f6c6be271) else tr(R.string.ui_d3d5f013f1), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            CorpusSource(state.version)
        }
    }
}

private fun searchSuggestions(version: BibleVersion): List<String> = when {
    version.isClassical -> Classics.suggestions(version)
    version.isQuran -> Quran.suggestions
    else -> ExpressionLexicon.suggestions
}
