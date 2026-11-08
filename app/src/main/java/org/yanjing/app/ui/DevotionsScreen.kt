package org.yanjing.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.yanjing.app.R
import org.yanjing.app.core.*
import org.yanjing.app.data.DevotionRepository
import org.yanjing.app.i18n.I18n
import org.yanjing.app.i18n.tr
import java.time.LocalDate

@Composable
fun DevotionsScreen(fontSize: Float) {
    val context = LocalContext.current
    val repository = remember(context) { DevotionRepository(context.applicationContext) }
    var catalog by remember { mutableStateOf<DevotionCatalog?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(repository, attempt) {
        failed = false
        try { catalog = withContext(Dispatchers.IO) { repository.catalog } }
        catch (_: Exception) { failed = true }
    }
    val data = catalog
    if (data == null) {
        if (failed) EmptyState(R.drawable.sym_info, tr(R.string.ui_573afa897f), tr(R.string.ui_2c7bac0bff), action = { Button(onClick = { attempt++ }) { Text(tr(R.string.ui_b8784c8dd5)) } })
        else CircularProgressIndicator(Modifier.padding(24.dp))
        return
    }
    var route by rememberSaveable { mutableStateOf("home") }
    val language = I18n.tag(context)
    val text: (DevotionText) -> String = { it.resolve(language) }
    val set = data.sets.firstOrNull { route == "rosary:${it.id}" }
    val prayer = data.prayers.firstOrNull { route == "prayer:${it.id}" }
    BackHandler(route != "home") { route = if (prayer != null) "prayers" else "home" }
    Column(Modifier.fillMaxSize()) {
        if (route != "home") TextButton(onClick = { route = if (prayer != null) "prayers" else "home" }, modifier = Modifier.testTag("devotions.back")) {
            Symbol(R.drawable.sym_arrow_back); Spacer(Modifier.width(8.dp)); Text(tr(if (prayer != null) R.string.devotions_prayers else R.string.devotions_title))
        }
        when {
            set != null -> RosaryReader(data, set, repository, language, fontSize)
            prayer != null -> LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                item { Text(text(prayer.title), style = MaterialTheme.typography.headlineMedium) }
                item { PrayerActions(text(prayer.title), text(prayer.text)) }
                item { DevotionBody(text(prayer.text), fontSize) }
                item { Text(text(data.edition), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            route == "prayers" -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text(tr(R.string.devotions_prayers), style = MaterialTheme.typography.headlineMedium) }
                for (category in listOf("rosary", "daily")) {
                    item { Text(tr(if (category == "rosary") R.string.devotions_rosary_prayers else R.string.devotions_daily_prayers), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp)) }
                    items(data.prayers.filter { it.category == category }, key = { it.id }) { entry ->
                        OutlinedCard(onClick = { route = "prayer:${entry.id}" }, modifier = Modifier.fillMaxWidth().testTag("prayer.${entry.id}")) {
                            Text(text(entry.title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(20.dp))
                        }
                    }
                }
                item { Text(text(data.edition), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 16.dp)) }
            }
            else -> DevotionHome(data, repository, text) { route = it }
        }
    }
}

@Composable
private fun DevotionHome(data: DevotionCatalog, repository: DevotionRepository, text: (DevotionText) -> String, navigate: (String) -> Unit) {
    val today = data.recommended(LocalDate.now().dayOfWeek.value)
    val uri = LocalUriHandler.current
    var showSources by rememberSaveable { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Card(onClick = { navigate("rosary:${today.id}") }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth().testTag("devotions.today")) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(tr(R.string.devotions_today), style = MaterialTheme.typography.labelLarge)
                    Text(text(today.title), style = MaterialTheme.typography.headlineMedium)
                    Text(text(today.schedule))
                    Text(tr(if (repository.position(today) > 0) R.string.devotions_resume else R.string.devotions_begin), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        item {
            OutlinedCard(onClick = { navigate("prayers") }, modifier = Modifier.fillMaxWidth().testTag("devotions.prayers")) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr(R.string.devotions_prayers), style = MaterialTheme.typography.titleLarge)
                    Text(tr(R.string.devotions_prayers_hint), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item { Text(tr(R.string.devotions_all_mysteries), style = MaterialTheme.typography.titleLarge) }
        items(data.sets, key = { it.id }) { set ->
            OutlinedCard(onClick = { navigate("rosary:${set.id}") }, modifier = Modifier.fillMaxWidth().testTag("rosary.${set.id}")) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text(set.title), style = MaterialTheme.typography.titleMedium)
                    Text(text(set.schedule), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Text(tr(R.string.devotions_guide), style = MaterialTheme.typography.titleLarge); Text(text(data.guide), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp)) }
        item {
            TextButton(onClick = { showSources = !showSources }) { Text(tr(R.string.devotions_sources)) }
            if (showSources) {
                Text(text(data.edition), style = MaterialTheme.typography.bodySmall)
                data.sources.forEach { source -> TextButton(onClick = { uri.openUri(source.url) }) { Text(source.title) } }
            }
        }
    }
}

@Composable
private fun RosaryReader(data: DevotionCatalog, set: RosarySet, repository: DevotionRepository, language: String, fontSize: Float) {
    val steps = remember(set.id) { data.steps(set) }
    var position by remember(set.id) { mutableIntStateOf(repository.position(set)) }
    var restart by remember { mutableStateOf(false) }
    val scroll = rememberLazyListState()
    LaunchedEffect(set.id, position) { scroll.scrollToItem(0) }
    fun move(value: Int) { position = value.coerceIn(0, steps.size); repository.position(set, position) }
    val step = steps.getOrNull(position)
    val mystery = step?.let { set.mysteries.getOrNull(it.decade - 1) }
    val prayer = data.prayers.firstOrNull { it.id == step?.prayerID }
    val title = (prayer?.title ?: mystery?.title)?.resolve(language)
    val body = (prayer?.text ?: mystery?.meditation)?.resolve(language)
    Column(Modifier.fillMaxSize()) {
        LazyColumn(state = scroll, modifier = Modifier.weight(1f), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item {
                Text(set.title.resolve(language), style = MaterialTheme.typography.headlineSmall)
                Text(if (step == null) tr(R.string.devotions_completed) else tr(R.string.devotions_step, position + 1, steps.size), modifier = Modifier.padding(vertical = 12.dp).testTag("rosary.progress"))
                LinearProgressIndicator(progress = { position.toFloat() / steps.size }, modifier = Modifier.fillMaxWidth())
            }
            if (step != null) {
                item { Text(when { step.decade == 0 -> tr(R.string.devotions_opening); mystery != null -> tr(R.string.devotions_mystery, step.decade) + " · " + mystery.title.resolve(language); else -> tr(R.string.devotions_closing) }, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium) }
                if (mystery != null) item { Text(mystery.reference.resolve(language), style = MaterialTheme.typography.bodyMedium) }
                item { Text(title.orEmpty(), style = MaterialTheme.typography.headlineMedium) }
                if (step.repetition > 0) item { Text(tr(R.string.devotions_repetition, step.repetition), style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("rosary.repetition")) }
                if (step.optional) item { Text(tr(R.string.devotions_optional), color = MaterialTheme.colorScheme.secondary) }
                item { DevotionBody(body.orEmpty(), fontSize) }
                if (prayer != null) item { PrayerActions(title.orEmpty(), body.orEmpty()) }
            } else item { Text(tr(R.string.devotions_complete_hint), style = MaterialTheme.typography.bodyLarge) }
            item { TextButton(onClick = { restart = true }, modifier = Modifier.testTag("rosary.restart")) { Text(tr(R.string.devotions_restart)) } }
        }
        Surface(tonalElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { move(position - 1) }, enabled = position > 0, modifier = Modifier.weight(1f).testTag("rosary.previous")) { Text(tr(R.string.devotions_previous)) }
                Button(onClick = { move(position + 1) }, enabled = step != null, modifier = Modifier.weight(1f).testTag("rosary.next")) { Text(tr(if (position == steps.lastIndex) R.string.devotions_finish else R.string.devotions_next)) }
            }
        }
    }
    if (restart) AlertDialog(onDismissRequest = { restart = false }, title = { Text(tr(R.string.devotions_restart)) }, text = { Text(tr(R.string.devotions_restart_hint)) }, confirmButton = { TextButton(onClick = { move(0); restart = false }) { Text(tr(R.string.devotions_restart)) } }, dismissButton = { TextButton(onClick = { restart = false }) { Text(tr(R.string.ui_2cd0f3be87)) } })
}

@Composable
private fun DevotionBody(text: String, fontSize: Float) {
    SelectionContainer { Text(text, fontSize = fontSize.sp, lineHeight = (fontSize * 1.8f).sp) }
}

@Composable
private fun PrayerActions(title: String, text: String) {
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(title, "$title\n$text")) }, modifier = Modifier.testTag("devotions.copy")) { Symbol(R.drawable.sym_content_copy); Spacer(Modifier.width(8.dp)); Text(tr(R.string.devotions_copy)) }
        TextButton(onClick = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "$title\n$text") }, title)) }) { Symbol(R.drawable.sym_share); Spacer(Modifier.width(8.dp)); Text(tr(R.string.devotions_share)) }
    }
}
