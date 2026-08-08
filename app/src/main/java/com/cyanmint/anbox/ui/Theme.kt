package com.cyanmint.anbox.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * Shared Miuix theme wrapper used by every Compose screen in the app.
 * Follows the pattern recommended by the Miuix README (and used by
 * ScrcpyForAndroid): pick a light/dark color scheme from the system setting
 * and provide it through [MiuixTheme].
 */
@Composable
fun AnanboxTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MiuixTheme(
        colors = colors,
        content = content,
    )
}
