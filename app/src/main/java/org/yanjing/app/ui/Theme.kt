package org.yanjing.app.ui

import android.os.Build
import android.app.Activity
import android.graphics.Color
import android.content.res.Resources
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import org.yanjing.app.Appearance

@Composable
fun YanJingTheme(appearance: Appearance, dynamicColor: Boolean, content: @Composable () -> Unit) {
    val dark = when (appearance) {
        Appearance.SYSTEM -> isSystemInDarkTheme()
        Appearance.LIGHT -> false
        Appearance.DARK -> true
    }
    val context = LocalContext.current
    SideEffect {
        (context as? ComponentActivity)?.enableEdgeToEdge(
            statusBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
    }
    val colors = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) compatibleColorScheme(dark, true) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else compatibleColorScheme(dark, false) { error("Dynamic colors are disabled") }
    MaterialExpressiveTheme(colorScheme = colors, content = content)
}

internal fun compatibleColorScheme(dark: Boolean, dynamic: Boolean, loadDynamic: () -> ColorScheme): ColorScheme {
    val fallback = if (dark) darkColorScheme() else expressiveLightColorScheme()
    // Some vendor themes omit system palette resources. Keep the reader usable.
    return if (dynamic) try { loadDynamic() } catch (_: Resources.NotFoundException) { fallback } else fallback
}
