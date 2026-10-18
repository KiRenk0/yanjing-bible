package org.yanjing.app.ui

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

@Composable
fun SettingsScreen(state: AppState, model: AppViewModel, onVersions: () -> Unit, onLegal: () -> Unit) {
    var clearHistory by remember { mutableStateOf(false) }
    val fontSlider = rememberSliderState(value = state.fontSize, steps = 5, trackRange = 16f..28f)
    LaunchedEffect(state.fontSize) { fontSlider.value = state.fontSize }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Symbol(R.drawable.sym_menu_book)
                    Text("言·经", style = MaterialTheme.typography.headlineMedium)
                    Text("日常表达 × 经文探索", style = MaterialTheme.typography.titleMedium)
                    Text("${BuildConfig.VERSION_NAME} · 离线检索 · 无广告", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            Text("显示与阅读", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
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
                    content = { Text("动态配色") }, supportingContent = { Text(if (Build.VERSION.SDK_INT >= 31) "使用系统壁纸提供的 Material 色彩" else "Android 12 及以上可用") },
                    trailingContent = { Switch(checked = state.dynamicColor && Build.VERSION.SDK_INT >= 31, onCheckedChange = null, enabled = Build.VERSION.SDK_INT >= 31) })
            }
        }
        item { UpdateSettings(model.updates) }
        item {
            OutlinedCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("经文字号 · ${state.fontSize.toInt()} sp", style = MaterialTheme.typography.titleSmall)
                    Slider(state = fontSlider, onValueChange = model::fontSize,
                        modifier = Modifier.semantics { contentDescription = "经文字号"; stateDescription = "${state.fontSize.toInt()} sp" })
                    Text("你的话是我脚前的灯，是我路上的光。", style = MaterialTheme.typography.bodyLarge.copy(fontSize = state.fontSize.sp, lineHeight = (state.fontSize * 1.65f).sp))
                    Text("诗篇 119:105 · 字体也随系统设置缩放", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Text("经文与数据", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Surface(onClick = onVersions, shape = MaterialTheme.shapes.large) {
                ListItem(content = { Text("内置版本") }, supportingContent = { Text("${state.version.title} · 4 套文本，3 种译本") }, leadingContent = { Symbol(R.drawable.sym_menu_book) }, trailingContent = { Symbol(R.drawable.sym_chevron_right) })
            }
            Surface(onClick = { clearHistory = true }, shape = MaterialTheme.shapes.large) {
                ListItem(content = { Text("清空搜索历史") }, supportingContent = { Text("${state.history.size} 条记录 · 只保存在本机") }, leadingContent = { Symbol(R.drawable.sym_history) }, trailingContent = { Symbol(R.drawable.sym_chevron_right) })
            }
            Surface(onClick = onLegal, shape = MaterialTheme.shapes.large) {
                ListItem(content = { Text("开源许可与经文来源") }, supportingContent = { Text("查看所用组件、文本来源与授权说明") }, leadingContent = { Symbol(R.drawable.sym_info) }, trailingContent = { Symbol(R.drawable.sym_chevron_right) })
            }
        }
        item {
            OutlinedCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("关于匹配", style = MaterialTheme.typography.titleSmall)
                    Text("句意搜索使用本地 BGE 模型；可与日常表达、原文搜索同时启用。句意基于简体和合本匹配，再展示所选版本。结果只辅助查找，请结合上下文理解。", style = MaterialTheme.typography.bodyMedium)
                    Text("模型、经文、收藏和查询均在本机。联网仅用于版本检查和安装包下载，不发送查询、收藏或历史。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (clearHistory) AlertDialog(onDismissRequest = { clearHistory = false }, title = { Text("清空搜索历史？") }, text = { Text("将删除本机的 ${state.history.size} 条搜索记录。") },
        confirmButton = { TextButton(onClick = { model.clearHistory(); clearHistory = false }) { Text("清空") } },
        dismissButton = { TextButton(onClick = { clearHistory = false }) { Text("取消") } })
}
