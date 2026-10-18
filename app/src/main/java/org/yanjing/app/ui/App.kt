@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.yanjing.app.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.yanjing.app.*
import org.yanjing.app.R
import org.yanjing.app.core.*

private data class Destination(val title: String, val icon: Int)
private val destinations = listOf(
    Destination("匹配", R.drawable.sym_auto_awesome), Destination("阅读", R.drawable.sym_menu_book),
    Destination("收藏", R.drawable.sym_bookmark), Destination("设置", R.drawable.sym_settings)
)

@Composable
fun YanJingApp(state: AppState, model: AppViewModel) {
    var destination by rememberSaveable { mutableIntStateOf(0) }
    var versions by remember { mutableStateOf(false) }
    var legal by remember { mutableStateOf<String?>(null) }
    var showLegal by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbars = remember { SnackbarHostState() }
    val context = LocalContext.current
    val copy: (Verse, BibleVersion) -> Unit = { verse, version ->
        copyVerse(context, verse, version)
        if (Build.VERSION.SDK_INT < 33) scope.launch { snackbars.showSnackbar("经文与出处已复制") }
    }
    val save: (Verse, BibleVersion) -> Unit = { verse, version ->
        val alreadySaved = state.saved.any { it.verse.key == verse.key && it.version == version }
        model.toggleSave(verse, version)
        scope.launch { snackbars.currentSnackbarData?.dismiss(); snackbars.showSnackbar(if (alreadySaved) "已取消收藏" else "已收藏经文") }
    }
    BackHandler(enabled = destination != 0 && state.detail == null) { destination = 0 }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        Row(Modifier.fillMaxSize()) {
            if (wide) {
                WideNavigationRail {
                    destinations.forEachIndexed { i, item ->
                        WideNavigationRailItem(railExpanded = false, selected = destination == i, onClick = { destination = i },
                            icon = { Symbol(item.icon) }, label = { Text(item.title) })
                    }
                }
            }
            Scaffold(modifier = Modifier.weight(1f),
                topBar = {
                    TopAppBar(title = { Text(when (destination) { 0 -> "言·经"; 1 -> "圣经阅读"; 2 -> "我的收藏"; else -> "设置" }) },
                        subtitle = { Text(when (destination) { 0 -> "让日常的话，遇见经文"; 1 -> "读一句，也读它的上下文"; 2 -> "留住触动你的字句"; else -> "按你的习惯阅读" }) },
                        actions = {
                            if (destination <= 1) TextButton(onClick = { versions = true }) {
                                Text(state.version.title, maxLines = 1, style = MaterialTheme.typography.labelLarge)
                                Spacer(Modifier.width(4.dp)); Symbol(R.drawable.sym_expand_more)
                            }
                        })
                },
                bottomBar = {
                    if (!wide) NavigationBar {
                        destinations.forEachIndexed { i, item ->
                            NavigationBarItem(selected = destination == i, onClick = { destination = i },
                                icon = { Symbol(if (i == 2 && destination == i) R.drawable.sym_bookmark_filled else item.icon) },
                                label = { Text(item.title) })
                        }
                    }
                }, snackbarHost = { SnackbarHost(snackbars) }) { padding ->
                Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.widthIn(max = 840.dp).fillMaxSize()) {
                        when (destination) {
                            0 -> MatchScreen(state, model, { verse -> save(verse, state.version) }, { verse -> copy(verse, state.version) })
                            1 -> ReaderScreen(state, model)
                            2 -> SavedScreen(state, model, save, copy)
                            else -> SettingsScreen(state, model, onVersions = { versions = true }, onLegal = {
                                showLegal = true; scope.launch { legal = model.legalText() }
                            })
                        }
                    }
                }
            }
        }
    }
    if (versions) VersionDialog(state.version, state.counts, { versions = false }, model::loadVersion)
    state.detail?.let { detail ->
        VerseDetailSheet(detail, state, model, save, copy, onRead = {
            if (detail.version != state.version) model.loadVersion(detail.version)
            model.read(detail.key.book, detail.key.chapter)
            destination = 1
            model.closeDetail()
        })
    }
    if (showLegal) AlertDialog(onDismissRequest = { showLegal = false },
        title = { Text("开源许可与经文来源") },
        text = { Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) { Text(legal ?: "正在读取许可…", style = MaterialTheme.typography.bodySmall) } },
        confirmButton = { TextButton(onClick = { showLegal = false }) { Text("完成") } })
    UpdatePrompt(model.updates)
}

@Composable
private fun SavedScreen(state: AppState, model: AppViewModel, save: (Verse, BibleVersion) -> Unit, copy: (Verse, BibleVersion) -> Unit) {
    if (state.saved.isEmpty()) {
        EmptyState(R.drawable.sym_bookmark, "把喜欢的经文留在这里", "点击经文卡片上的收藏图标，即可离线保存经文与版本。", Modifier.padding(top = 48.dp))
    } else {
        androidx.compose.foundation.lazy.LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("${state.saved.size} 条收藏", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(state.saved.size, key = { state.saved[it].id }) { i ->
                val item = state.saved[i]
                VerseCard(item.verse, item.version, true, state.fontSize,
                    onOpen = { model.openDetail(item.verse, item.version) }, onSave = { save(item.verse, item.version) }, onCopy = { copy(item.verse, item.version) })
            }
        }
    }
}
