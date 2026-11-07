package org.yanjing.app.ui

import org.yanjing.app.i18n.tr
import org.yanjing.app.R

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.*
import org.yanjing.app.BuildConfig
import org.yanjing.app.update.*
import java.text.DateFormat
import java.util.Date

@Composable
fun UpdateSettings(manager: UpdateManager) {
    val state by manager.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var routes by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr(R.string.ui_cebbc1f61c), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        OutlinedCard {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr(R.string.ui_5a51b05996, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.titleSmall)
                Text(state.message.ifBlank { tr(R.string.ui_bccda3b1ca) }, style = MaterialTheme.typography.bodyMedium)
                if (state.lastChecked > 0) Text(tr(R.string.ui_8917b3c354, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(state.lastChecked))), style = MaterialTheme.typography.bodySmall)
                if (state.phase == UpdatePhase.DOWNLOADING) {
                    LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("${state.progress}%", style = MaterialTheme.typography.labelMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = state.phase !in setOf(UpdatePhase.CHECKING, UpdatePhase.DOWNLOADING, UpdatePhase.READY), onClick = manager::check) { Text(tr(R.string.ui_7f68ebad19)) }
                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UpdatePolicy.REPOSITORY_URL))) }) { Text("GitHub") }
                }
                if (state.release != null) {
                    Text(tr(R.string.ui_0ca906da49, state.release!!.versionName, "%.1f".format(state.release!!.size / 1048576.0)), style = MaterialTheme.typography.titleSmall)
                    if (state.phase == UpdatePhase.READY) InstallUpdateButton(manager)
                    else if (state.phase != UpdatePhase.DOWNLOADING) FilledTonalButton(onClick = manager::download) { Text(tr(R.string.ui_e005bc1767)) }
                }
            }
            UpdateToggle(tr(R.string.ui_c42a3dd9c2), tr(R.string.ui_173d0d252f), state.autoCheck) { manager.option("autoCheck", it) }
            UpdateToggle(tr(R.string.ui_7392248edb), tr(R.string.ui_85786e444b), state.autoDownload) { manager.option("autoDownload", it) }
            UpdateToggle(tr(R.string.ui_3a3fb31bf4), tr(R.string.ui_526fa83137), state.wifiOnly) { manager.option("wifiOnly", it) }
            ListItem(content = { Text(tr(R.string.ui_d9f41c12c3)) }, supportingContent = { Text(tr(R.string.ui_588a1d210d)) },
                trailingContent = {
                    Box {
                        TextButton(onClick = { routes = true }) { Text(state.route.title) }
                        DropdownMenu(expanded = routes, onDismissRequest = { routes = false }) {
                            DownloadRoute.entries.forEach { route -> DropdownMenuItem(text = { Text(route.title) }, onClick = { manager.route(route); routes = false }) }
                        }
                    }
                })
        }
    }
}

@Composable
private fun UpdateToggle(title: String, description: String, checked: Boolean, changed: (Boolean) -> Unit) {
    ListItem(modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = changed),
        content = { Text(title) }, supportingContent = { Text(description) }, trailingContent = { Switch(checked = checked, onCheckedChange = null) })
}

@Composable
fun UpdatePrompt(manager: UpdateManager) {
    val state by manager.state.collectAsStateWithLifecycle()
    val release = state.release ?: return
    if (state.dismissed == release.versionCode || state.phase !in setOf(UpdatePhase.AVAILABLE, UpdatePhase.DOWNLOADING, UpdatePhase.READY)) return
    AlertDialog(onDismissRequest = manager::dismiss, title = { Text(tr(R.string.ui_c8de69e34e, release.versionName)) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(release.notes.ifBlank { tr(R.string.ui_63fc3ceccf) })
                Text(state.message, style = MaterialTheme.typography.bodyMedium)
                if (state.phase == UpdatePhase.DOWNLOADING) {
                    LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("${state.progress}%")
                }
                Text(tr(R.string.ui_0d326909fe, "%.1f".format(release.size / 1048576.0)), style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = {
            when (state.phase) {
                UpdatePhase.READY -> InstallUpdateButton(manager)
                UpdatePhase.DOWNLOADING -> TextButton(onClick = manager::dismiss) { Text(tr(R.string.ui_e3f33e0777)) }
                else -> TextButton(onClick = manager::download) { Text(tr(R.string.ui_b088e89d54)) }
            }
        }, dismissButton = {
            if (state.phase != UpdatePhase.DOWNLOADING) TextButton(onClick = manager::dismiss) { Text(tr(R.string.ui_9d2ca26128)) }
        })
}

@Composable
private fun InstallUpdateButton(manager: UpdateManager) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column {
        TextButton(enabled = !busy, onClick = {
            scope.launch {
                busy = true; error = null
                try {
                    withContext(Dispatchers.IO) { manager.verifyReady() }
                    context.startActivity(manager.installIntent())
                    if (!context.packageManager.canRequestPackageInstalls()) error = tr(R.string.ui_bbd85c1dc1)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { error = failure.message ?: tr(R.string.ui_dc08150198) }
                finally { busy = false }
            }
        }) { Text(if (busy) tr(R.string.ui_84717e2cad) else tr(R.string.ui_7a6bafc9c8)) }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}
