@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.yanjing.app.ui

import org.yanjing.app.i18n.tr

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import kotlinx.coroutines.launch
import org.yanjing.app.*
import org.yanjing.app.R
import org.yanjing.app.core.*

private data class Destination(private val titleRes: Int, val icon: Int) { val title: String get() = tr(titleRes) }
private val destinations = listOf(
    Destination(R.string.ui_fd047ccb20, R.drawable.sym_auto_awesome), Destination(R.string.ui_ce9b12499c, R.drawable.sym_menu_book),
    Destination(R.string.ui_60a53514eb, R.drawable.sym_bookmark), Destination(R.string.ui_df3d58c7d8, R.drawable.sym_settings),
    Destination(R.string.devotions_title, R.drawable.sym_devotions)
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
        if (Build.VERSION.SDK_INT < 33) scope.launch { snackbars.showSnackbar(tr(R.string.ui_d6cc6b2d14)) }
    }
    val save: (Verse, BibleVersion) -> Unit = { verse, version ->
        val alreadySaved = state.saved.any { it.verse.key == verse.key && it.version == version }
        model.toggleSave(verse, version)
        scope.launch { snackbars.currentSnackbarData?.dismiss(); snackbars.showSnackbar(if (alreadySaved) tr(R.string.ui_7fa7b63b0e) else tr(R.string.ui_95c8e5c111)) }
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
                    TopAppBar(title = { Text(when (destination) { 0 -> stringResource(R.string.app_name); 1 -> tr(R.string.ui_c214cf65a4, state.version.scripture.title); 2 -> tr(R.string.ui_9385bfefca); 4 -> tr(R.string.devotions_title); else -> tr(R.string.ui_df3d58c7d8) }) },
                        subtitle = { Text(when (destination) { 0 -> tr(R.string.ui_72f30a9d58); 1 -> tr(R.string.ui_2ebb3b278e); 2 -> tr(R.string.ui_933a71e100); 4 -> tr(R.string.devotions_subtitle); else -> tr(R.string.ui_cf307954e9) }) },
                        actions = {
                            if (destination <= 1) TextButton(onClick = { versions = true },modifier=Modifier.semantics { contentDescription=tr(R.string.ui_ecdf24870a) }) {
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
                            4 -> DevotionsScreen(state.fontSize)
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
        title = { Text(tr(R.string.ui_567a685cee)) },
        text = { Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) { Text(legal ?: tr(R.string.ui_2c7bac0bff), style = MaterialTheme.typography.bodySmall) } },
        confirmButton = { TextButton(onClick = { showLegal = false }) { Text(tr(R.string.ui_c0b3fbff51)) } })
    UpdatePrompt(model.updates)
}

@Composable
private fun SavedScreen(state: AppState, model: AppViewModel, save: (Verse, BibleVersion) -> Unit, copy: (Verse, BibleVersion) -> Unit) {
    if (state.saved.isEmpty()) {
        EmptyState(R.drawable.sym_bookmark, tr(R.string.ui_550c1bce78), tr(R.string.ui_66104bccc5), Modifier.padding(top = 48.dp))
    } else {
        androidx.compose.foundation.lazy.LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(tr(R.string.ui_7c998110df, state.saved.size), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(state.saved.size, key = { state.saved[it].id }) { i ->
                val item = state.saved[i]
                VerseCard(item.verse, item.version, true, state.fontSize,
                    onOpen = { model.openDetail(item.verse, item.version) }, onSave = { save(item.verse, item.version) }, onCopy = { copy(item.verse, item.version) })
            }
        }
    }
}
