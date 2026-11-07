@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.yanjing.app.ui

import org.yanjing.app.i18n.tr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
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
    val text = exportedVerse(verse, version)
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(verse.key.reference, text))
}
fun shareVerse(context: Context, verse: Verse, version: BibleVersion) {
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, exportedVerse(verse, version))
    }, tr(R.string.ui_2c3262050b)))
}

fun exportedVerse(verse: Verse, version: BibleVersion): String {
    val credit = when (version) {
        BibleVersion.QURAN_ZH -> tr(R.string.ui_7f7c718640)
        BibleVersion.QURAN_AR -> "\nTanzil Project · Uthmani v1.1 · CC BY 3.0 · https://tanzil.net"
        BibleVersion.ANALECTS -> tr(R.string.ui_d8d06d6030)
        else -> Classics.forVersion(version)?.let { tr(R.string.ui_96ca005377, it.credit) } ?: if(version==BibleVersion.VULGATE) tr(R.string.ui_1b5f2ef791) else ""
    }
    return "${verse.text}\n——${verse.key.reference}（${version.title}）$credit"
}

@Composable
fun VerseCard(verse: Verse, version: BibleVersion, saved: Boolean, fontSize: Float,
              reason: String? = null, onOpen: () -> Unit, onSave: () -> Unit, onCopy: () -> Unit) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(verse.key.reference, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                IconButton(onClick = onSave) { Symbol(if (saved) R.drawable.sym_bookmark_filled else R.drawable.sym_bookmark, if (saved) tr(R.string.ui_b067b29f39, verse.key.reference) else tr(R.string.ui_715c8ae33d, verse.key.reference)) }
            }
            Text(verse.text, style = verseTextStyle(version, fontSize), modifier = Modifier.fillMaxWidth().padding(end = 8.dp, bottom = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(version.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (reason != null) Text(reason, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onOpen) { Text(tr(R.string.ui_59b529978f)) }
                IconButton(onClick = onCopy) { Symbol(R.drawable.sym_content_copy, tr(R.string.ui_912cc32cbb, verse.key.reference)) }
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
fun verseTextStyle(version: BibleVersion, fontSize: Float, lineSpacing: Float = 1.65f) = MaterialTheme.typography.bodyLarge.copy(
    fontSize = fontSize.sp, lineHeight = (fontSize * if (version.isRtl) maxOf(1.9f,lineSpacing) else lineSpacing).sp,
    textDirection = if (version.isRtl) TextDirection.Rtl else TextDirection.ContentOrLtr)

@Composable
fun CorpusSource(version: BibleVersion) {
    val uri = LocalUriHandler.current
    if(version.isClassical && !version.isAnalects) Classics.forVersion(version)?.let { work ->
        TextButton(onClick={uri.openUri(work.sourceUrl)},contentPadding=PaddingValues(0.dp)) {
            Text(tr(R.string.ui_6cb0240500, work.credit),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if(version==BibleVersion.VULGATE) TextButton(onClick={uri.openUri("https://github.com/scrollmapper/bible_databases/tree/e1b254cef86d0e65b1a5d1a94b8b112d0f296a2c/sources/la/VulgClementine")},contentPadding=PaddingValues(0.dp)) {
        Text(tr(R.string.ui_3670a14237),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (version.isAnalects) TextButton(onClick = { uri.openUri("https://github.com/chinese-poetry/chinese-poetry/blob/fa1814a8044db4fb0c7a9e0a0449974e8c3463b7/论语/lunyu.json") }, contentPadding = PaddingValues(0.dp)) {
        Text(tr(R.string.ui_5e46be85c6), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (version.isQuran) TextButton(onClick = { uri.openUri(if (version.isRtl) "https://tanzil.net" else "https://quranenc.com/zh/browse/chinese_makin") }, contentPadding = PaddingValues(0.dp)) { Text(
        if (version.isRtl) tr(R.string.ui_967ab37610)
        else tr(R.string.ui_b40b0fc33a),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
fun VersionDialog(current: BibleVersion, counts: Map<String, Int>, onDismiss: () -> Unit, onSelect: (BibleVersion) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        icon = { Symbol(R.drawable.sym_menu_book) }, title = { Text(tr(R.string.ui_ecdf24870a)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(tr(R.string.ui_3f014bdf31), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 12.dp))
                Scripture.entries.forEach { scripture ->
                Text(scripture.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp))
                BibleVersion.entries.filter { it.scripture == scripture }.forEach { version ->
                    Surface(shape = MaterialTheme.shapes.medium) {
                        Row(Modifier.fillMaxWidth().selectable(selected = version == current, role = Role.RadioButton, onClick = { onSelect(version); onDismiss() }).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = version == current, onClick = null, modifier = Modifier.padding(12.dp))
                            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                Text(version.title, style = MaterialTheme.typography.titleSmall)
                                Text(tr(R.string.ui_08e0264b24, version.description, counts[version.id] ?: "—", if (version.isClassical) tr(R.string.ui_0477dbbdee) else tr(R.string.ui_ada40d1b8b)), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                }
                Text(tr(R.string.ui_d1e7d184ff), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text(tr(R.string.ui_c0b3fbff51)) } })
}
