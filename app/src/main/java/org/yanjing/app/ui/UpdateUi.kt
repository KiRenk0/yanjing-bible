package org.yanjing.app.ui

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
        Text("版本与更新", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        OutlinedCard {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("当前版本 ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleSmall)
                Text(state.message.ifBlank { "从本项目 GitHub Release 获取更新" }, style = MaterialTheme.typography.bodyMedium)
                if (state.lastChecked > 0) Text("上次检查：${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(state.lastChecked))}", style = MaterialTheme.typography.bodySmall)
                if (state.phase == UpdatePhase.DOWNLOADING) {
                    LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("${state.progress}%", style = MaterialTheme.typography.labelMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = state.phase !in setOf(UpdatePhase.CHECKING, UpdatePhase.DOWNLOADING, UpdatePhase.READY), onClick = manager::check) { Text("检查更新") }
                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UpdatePolicy.REPOSITORY_URL))) }) { Text("GitHub") }
                }
                if (state.release != null) {
                    Text("新版本 ${state.release!!.versionName} · ${"%.1f".format(state.release!!.size / 1048576.0)} MB", style = MaterialTheme.typography.titleSmall)
                    if (state.phase == UpdatePhase.READY) InstallUpdateButton(manager)
                    else if (state.phase != UpdatePhase.DOWNLOADING) FilledTonalButton(onClick = manager::download) { Text("立即下载更新") }
                }
            }
            UpdateToggle("自动检查更新", "启动时和后台定期检查新版本", state.autoCheck) { manager.option("autoCheck", it) }
            UpdateToggle("自动下载更新", "发现新版本后自动下载，安装前由你确认", state.autoDownload) { manager.option("autoDownload", it) }
            UpdateToggle("仅 Wi-Fi 自动下载", "手动点“立即下载”可使用当前网络", state.wifiOnly) { manager.option("wifiOnly", it) }
            ListItem(content = { Text("下载线路") }, supportingContent = { Text("直连受限时可切换镜像，下载后核对应用签名") },
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
    AlertDialog(onDismissRequest = manager::dismiss, title = { Text("发现新版本 ${release.versionName}") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(release.notes.ifBlank { "功能改进与问题修复" })
                Text(state.message, style = MaterialTheme.typography.bodyMedium)
                if (state.phase == UpdatePhase.DOWNLOADING) {
                    LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("${state.progress}%")
                }
                Text("安装包 ${"%.1f".format(release.size / 1048576.0)} MB", style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = {
            when (state.phase) {
                UpdatePhase.READY -> InstallUpdateButton(manager)
                UpdatePhase.DOWNLOADING -> TextButton(onClick = manager::dismiss) { Text("后台下载") }
                else -> TextButton(onClick = manager::download) { Text("立即下载") }
            }
        }, dismissButton = {
            if (state.phase != UpdatePhase.DOWNLOADING) TextButton(onClick = manager::dismiss) { Text("稍后") }
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
                    if (!context.packageManager.canRequestPackageInstalls()) error = "允许后返回此处，再点“安装更新”。"
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { error = failure.message ?: "无法打开安装程序，请重试" }
                finally { busy = false }
            }
        }) { Text(if (busy) "正在校验…" else "安装更新") }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}
