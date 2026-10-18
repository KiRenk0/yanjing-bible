@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.yanjing.app.ui

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
            placeholder = { Text(if (SearchMode.SEMANTIC in state.modes) "用中文描述意思，或输入出处" else "输入想说的话、关键词或出处") },
            leadingIcon = {
                if (searchBarState.currentValue == SearchBarValue.Expanded) IconButton(onClick = collapse) { Symbol(R.drawable.sym_arrow_back, "收起搜索") }
                else Symbol(R.drawable.sym_search)
            },
            trailingIcon = {
                Row {
                    if (state.query.isNotEmpty()) IconButton(onClick = { model.query("") }) { Symbol(R.drawable.sym_close, "清空搜索") }
                    if (searchBarState.currentValue == SearchBarValue.Expanded) IconButton(onClick = { searchSettings = true }) { Symbol(R.drawable.sym_settings, "搜索设置") }
                }
            })
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SearchBar(state = searchBarState, inputField = inputField, colors = SearchBarDefaults.containedColors(searchBarState), modifier = Modifier.weight(1f))
            FilledTonalIconButton(onClick = { searchSettings = true }) { Symbol(R.drawable.sym_settings, "搜索设置") }
        }
        ExpandedFullScreenContainedSearchBar(state = searchBarState, inputField = inputField) {
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                if (state.query.isNotBlank()) item {
                    ListItem(content = { Text("搜索“${state.query}”") }, leadingContent = { Symbol(R.drawable.sym_search) },
                        modifier = Modifier.fillMaxWidth(), supportingContent = { Text("在${state.version.title}中查找") },
                        trailingContent = { IconButton(onClick = submit) { Symbol(R.drawable.sym_arrow_forward, "开始搜索") } })
                    TextButton(onClick = submit, modifier = Modifier.padding(horizontal = 16.dp)) { Text("查看匹配结果") }
                }
                if (state.history.isNotEmpty()) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("最近搜索", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = model::clearHistory) { Text("清空") }
                        }
                    }
                    items(state.history.size) { i ->
                        Surface(onClick = { pick(state.history[i]) }) {
                            ListItem(content = { Text(state.history[i]) }, leadingContent = { Symbol(R.drawable.sym_history) }, trailingContent = { Symbol(R.drawable.sym_arrow_forward) })
                        }
                    }
                }
                item { Text("试试这些日常表达", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp)) }
                items(ExpressionLexicon.suggestions.size) { i ->
                    Surface(onClick = { pick(ExpressionLexicon.suggestions[i]) }) {
                        ListItem(content = { Text(ExpressionLexicon.suggestions[i]) }, leadingContent = { Symbol(R.drawable.sym_auto_awesome) })
                    }
                }
            }
        }
        run {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(state.modes.sortedBy { it.ordinal }.joinToString(" + ") { it.title }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = { searchSettings = true }) { Text("选择模式") }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Testament.entries.forEach { testament ->
                    FilterChip(selected = state.testament == testament, onClick = { model.testament(testament) }, label = { Text(testament.title) },
                        leadingIcon = if (state.testament == testament) ({ Symbol(R.drawable.sym_check) }) else null)
                }
            }
            when {
                state.loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) { CircularProgressIndicator(); Text("正在打开离线经文库…") } }
                state.error != null -> EmptyState(R.drawable.sym_info, "经文库暂时无法打开", state.error, action = { Button(onClick = { model.loadVersion(state.version) }) { Text("重试") } })
                state.query.isBlank() -> WelcomeContent(state, pick)
                state.searching -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CircularProgressIndicator()
                        if (SearchMode.SEMANTIC in state.modes) Text("正在按句意查找，首次使用需加载本地模型…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                state.response.hits.isEmpty() -> EmptyState(R.drawable.sym_search, "暂时没有找到", state.response.message ?: "试试缩短句子、换个关键词，或切换“日常表达”。也可以输入“约 3:16”。", Modifier.padding(top = 32.dp))
                else -> {
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(if (SearchMode.SEMANTIC in state.modes) "相关经文 · ${state.response.hits.size} 处候选" else "找到 ${state.response.total} 处经文", style = MaterialTheme.typography.titleMedium)
                                Text(when {
                                    SearchMode.SEMANTIC in state.modes -> "按所选模式合并排序；句意基于和合本检索，展示当前版本原文。"
                                    state.modes.size > 1 -> "已合并去重，卡片标明命中模式。请结合上下文理解。"
                                    SearchMode.EXPRESSION in state.modes -> "按表达与用词的相关程度排序，请结合上下文理解。"
                                    else -> "精确匹配原文，简繁体通用。"
                                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                state.response.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                            }
                        }
                        items(state.response.hits.size, key = { state.response.hits[it].verse.key.encoded }) { i ->
                            val hit = state.response.hits[i]
                            VerseCard(hit.verse, state.version, state.saved.any { it.version == state.version && it.verse.key == hit.verse.key }, state.fontSize, hit.reason,
                                onOpen = { model.openDetail(hit.verse) }, onSave = { save(hit.verse) }, onCopy = { copy(hit.verse) })
                        }
                        if (state.response.total > state.response.hits.size) item { Text("已显示前 ${state.response.hits.size} 条。加入更具体的词，可进一步缩小范围。", style = MaterialTheme.typography.bodySmall) }
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
    AlertDialog(onDismissRequest = dismiss, icon = { Symbol(R.drawable.sym_settings) }, title = { Text("搜索模式") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("可多选，同时查找并合并重复经文。", style = MaterialTheme.typography.bodyMedium)
                SearchMode.entries.forEach { mode ->
                    val toggle = { pending = if (mode in pending) pending - mode else pending + mode }
                    ListItem(modifier = Modifier.toggleable(value = mode in pending, role = Role.Checkbox, onValueChange = { toggle() }), leadingContent = { Checkbox(checked = mode in pending, onCheckedChange = null) },
                        supportingContent = { Text(mode.description) }) { Text(mode.title) }
                }
                Text(if (pending.isEmpty()) "请至少选择一种搜索模式。" else "句意搜索完全离线，理解相关不等于经文原意。", style = MaterialTheme.typography.bodySmall,
                    color = if (pending.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }, confirmButton = { TextButton(enabled = pending.isNotEmpty(), onClick = { apply(pending); dismiss() }) { Text("应用") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("取消") } })
}

@Composable
private fun ColumnScope.WelcomeContent(state: AppState, pick: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Symbol(R.drawable.sym_auto_awesome)
                    Text("你想说的话，\n经文里也许有。", style = MaterialTheme.typography.headlineMedium)
                    Text("把一句日常表达，换成一次经文探索。", style = MaterialTheme.typography.bodyLarge)
                    Text("完全离线 · 无需登录", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("从一句话开始", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ExpressionLexicon.suggestions.forEach { phrase -> AssistChip(onClick = { pick(phrase) }, label = { Text(phrase) }) }
                }
            }
        }
        if (state.history.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("最近搜索", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.history.take(4).forEach { phrase -> SuggestionChip(onClick = { pick(phrase) }, label = { Text(phrase) }) }
                }
            }
        }
        item { Text("也支持出处定位，例如：约 3:16、诗篇 23、John 4:7。表达关联仅辅助查找，点击结果可读上下文和版本对照。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
