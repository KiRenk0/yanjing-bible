package org.yanjing.app.ui

import org.yanjing.app.i18n.tr

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.yanjing.app.*
import org.yanjing.app.R
import org.yanjing.app.core.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.*
import org.yanjing.app.diagnostics.Diagnostics

@Composable
fun SettingsScreen(state: AppState, model: AppViewModel, onVersions: () -> Unit, onLegal: () -> Unit) {
    var clearHistory by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exporting by remember { mutableStateOf(false) }
    var diagnosticError by remember { mutableStateOf<String?>(null) }
    var chooseLanguage by remember { mutableStateOf(false) }
    val fontSlider = rememberSliderState(value = state.fontSize, steps = 5, trackRange = 16f..28f)
    LaunchedEffect(state.fontSize) { fontSlider.value = state.fontSize }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Symbol(R.drawable.sym_menu_book)
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                    Text(tr(R.string.ui_54d9bf6f75), style = MaterialTheme.typography.titleMedium)
                    Text(tr(R.string.ui_5e6cd637d3, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            Text(tr(R.string.ui_4c3fe66dba), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Appearance.entries.forEachIndexed { i, appearance ->
                    SegmentedButton(selected = state.appearance == appearance, onClick = { model.appearance(appearance) }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(appearance.title) }
                }
            }
        }
        item {
            Surface(shape = MaterialTheme.shapes.large) {
                ListItem(modifier = Modifier.toggleable(value = state.dynamicColor && Build.VERSION.SDK_INT >= 31, enabled = Build.VERSION.SDK_INT >= 31, role = Role.Switch, onValueChange = model::dynamicColor),
                    content = { Text(tr(R.string.ui_bc99124901)) }, supportingContent = { Text(if (Build.VERSION.SDK_INT >= 31) tr(R.string.ui_13383b2187) else tr(R.string.ui_c700f88f57)) },
                    trailingContent = { Switch(checked = state.dynamicColor && Build.VERSION.SDK_INT >= 31, onCheckedChange = null, enabled = Build.VERSION.SDK_INT >= 31) })
            }
        }
        item {
            Surface(onClick = { chooseLanguage = true }, shape = MaterialTheme.shapes.large) {
                ListItem(content = { Text(tr(R.string.settings_language)) },
                    supportingContent = { Text(tr(R.string.language_hint)) },
                    trailingContent = { Text(when (org.yanjing.app.i18n.I18n.choice(context)) {
                        "en" -> "English"; "zh-CN" -> "简体中文"; "zh-TW" -> "繁體中文"; else -> tr(R.string.language_system)
                    }) })
            }
        }
        item { UpdateSettings(model.updates) }
        item {
            OutlinedCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr(R.string.ui_cbbbca43b0, state.fontSize.toInt()), style = MaterialTheme.typography.titleSmall)
                    Slider(state = fontSlider, onValueChange = model::fontSize,
                        modifier = Modifier.semantics { contentDescription = tr(R.string.ui_11d393ce3c); stateDescription = "${state.fontSize.toInt()} sp" })
                    Text(tr(R.string.ui_b2111b4ec2), style = MaterialTheme.typography.bodyLarge.copy(fontSize = state.fontSize.sp, lineHeight = (state.fontSize * 1.65f).sp))
                    Text(tr(R.string.ui_5dd460e68f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Text(tr(R.string.ui_914f6a1974), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Surface(onClick = onVersions, shape = MaterialTheme.shapes.large) {
                ListItem(content = { Text(tr(R.string.ui_da29379b71)) }, supportingContent = { Text(tr(R.string.ui_5b75f44647)) }, leadingContent = { Symbol(R.drawable.sym_menu_book) }, trailingContent = { Symbol(R.drawable.sym_chevron_right) })
            }
            Surface(onClick = { clearHistory = true }, shape = MaterialTheme.shapes.large) {
                ListItem(content = { Text(tr(R.string.ui_02c0ab8fbd)) }, supportingContent = { Text(tr(R.string.ui_fc4a87fa8d, state.history.size)) }, leadingContent = { Symbol(R.drawable.sym_history) }, trailingContent = { Symbol(R.drawable.sym_chevron_right) })
            }
            Surface(onClick = onLegal, shape = MaterialTheme.shapes.large) {
                ListItem(content = { Text(tr(R.string.ui_567a685cee)) }, supportingContent = { Text(tr(R.string.ui_5aad762f28)) }, leadingContent = { Symbol(R.drawable.sym_info) }, trailingContent = { Symbol(R.drawable.sym_chevron_right) })
            }
            Surface(onClick = {
                if (!exporting) scope.launch {
                    exporting=true; diagnosticError=null
                    try {
                        val intent=withContext(Dispatchers.IO) { Diagnostics.shareIntent(context) }
                        context.startActivity(intent)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { diagnosticError=tr(R.string.ui_8a36e4082a) }
                    finally { exporting=false }
                }
            }, shape = MaterialTheme.shapes.large) {
                ListItem(content = { Text(if (exporting) tr(R.string.ui_5326ad11c1) else tr(R.string.ui_a1fa288e54)) },
                    supportingContent = { Text(tr(R.string.ui_f623485bd2)) },
                    leadingContent = { Symbol(R.drawable.sym_info) })
            }
            diagnosticError?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        }
        item {
            OutlinedCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr(R.string.ui_2cc61c2dc1), style = MaterialTheme.typography.titleSmall)
                    Text(tr(R.string.ui_f91cd10a7e), style = MaterialTheme.typography.bodyMedium)
                    Text(tr(R.string.ui_d0d7217ee0), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (chooseLanguage) AlertDialog(onDismissRequest = { chooseLanguage = false }, title = { Text(tr(R.string.settings_language)) },
        text = { Column {
            listOf("system" to tr(R.string.language_system), "en" to "English", "zh-CN" to "简体中文", "zh-TW" to "繁體中文").forEach { (tag, label) ->
                Row(Modifier.fillMaxWidth().toggleable(value = org.yanjing.app.i18n.I18n.choice(context) == tag, role = Role.RadioButton, onValueChange = {
                    org.yanjing.app.i18n.I18n.setLanguage(context, tag)
                    chooseLanguage = false
                    model.loadVersion(state.version)
                    (context as? android.app.Activity)?.recreate()
                }).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = org.yanjing.app.i18n.I18n.choice(context) == tag, onClick = null)
                    Spacer(Modifier.width(12.dp)); Text(label)
                }
            }
        } }, confirmButton = { TextButton(onClick = { chooseLanguage = false }) { Text(tr(R.string.ui_2cd0f3be87)) } })
    if (clearHistory) AlertDialog(onDismissRequest = { clearHistory = false }, title = { Text(tr(R.string.ui_5fb94d43ef)) }, text = { Text(tr(R.string.ui_f423d22508, state.history.size)) },
        confirmButton = { TextButton(onClick = { model.clearHistory(); clearHistory = false }) { Text(tr(R.string.ui_1ef3de06b3)) } },
        dismissButton = { TextButton(onClick = { clearHistory = false }) { Text(tr(R.string.ui_2cd0f3be87)) } })
}
