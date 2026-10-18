@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.yanjing.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.yanjing.app.R
import org.yanjing.app.core.*

@Composable
fun Symbol(@DrawableRes id: Int, description: String?, modifier: Modifier = Modifier) {
    Icon(painterResource(id), description, modifier.size(24.dp))
}

@Composable
fun Symbol(@DrawableRes id: Int, modifier: Modifier = Modifier) = Symbol(id, null, modifier)

fun copyVerse(context: Context, verse: Verse, version: BibleVersion) {
    val text = "${verse.text}\n——${verse.key.reference}（${version.title}）"
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(verse.key.reference, text))
}
fun shareVerse(context: Context, verse: Verse, version: BibleVersion) {
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "${verse.text}\n——${verse.key.reference}（${version.title}）")
    }, "分享经文"))
}

@Composable
fun VerseCard(verse: Verse, version: BibleVersion, saved: Boolean, fontSize: Float,
              reason: String? = null, onOpen: () -> Unit, onSave: () -> Unit, onCopy: () -> Unit) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(verse.key.reference, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                IconButton(onClick = onSave) { Symbol(if (saved) R.drawable.sym_bookmark_filled else R.drawable.sym_bookmark, if (saved) "取消收藏 ${verse.key.reference}" else "收藏 ${verse.key.reference}") }
            }
            Text(verse.text, style = MaterialTheme.typography.bodyLarge.copy(fontSize = fontSize.sp, lineHeight = (fontSize * 1.65f).sp), modifier = Modifier.padding(end = 8.dp, bottom = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(version.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (reason != null) Text(reason, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onOpen) { Text("上下文与对照") }
                IconButton(onClick = onCopy) { Symbol(R.drawable.sym_content_copy, "复制 ${verse.key.reference}") }
            }
        }
    }
}

@Composable
fun EmptyState(@DrawableRes icon: Int, title: String, description: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) { Symbol(icon) }
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action?.invoke()
    }
}

@Composable
fun VersionDialog(current: BibleVersion, counts: Map<String, Int>, onDismiss: () -> Unit, onSelect: (BibleVersion) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        icon = { Symbol(R.drawable.sym_menu_book) }, title = { Text("选择圣经版本") },
        text = {
            Column {
                Text("所有版本均已内置，可离线使用。", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 12.dp))
                BibleVersion.entries.forEach { version ->
                    Surface(shape = MaterialTheme.shapes.medium) {
                        Row(Modifier.fillMaxWidth().selectable(selected = version == current, role = Role.RadioButton, onClick = { onSelect(version); onDismiss() }).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = version == current, onClick = null, modifier = Modifier.padding(12.dp))
                            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                Text(version.title, style = MaterialTheme.typography.titleSmall)
                                Text("${version.description} · ${counts[version.id] ?: "—"} 节", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                Text("简体与繁体和合本为同一译本；文理和合本是独立的文言译本。", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } })
}
