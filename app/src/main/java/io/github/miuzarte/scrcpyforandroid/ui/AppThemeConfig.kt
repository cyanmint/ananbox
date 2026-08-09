package io.github.miuzarte.scrcpyforandroid.ui

import androidx.compose.ui.graphics.Color
import io.github.miuzarte.scrcpyforandroid.storage.AppSettings
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

val MonetKeyColors: List<Pair<String, Color>> = listOf(
    "Blue" to Color(0xFF3482FF),
    "Green" to Color(0xFF36D167),
    "Purple" to Color(0xFF7C4DFF),
    "Yellow" to Color(0xFFFFB21D),
    "Orange" to Color(0xFFFF5722),
    "Pink" to Color(0xFFE91E63),
    "Teal" to Color(0xFF00BCD4),
)

val MonetKeyColorOptions: List<String> = listOf("Default") + MonetKeyColors.map { it.first }

fun monetKeyColorFor(index: Int): Color? =
    if (index <= 0) null else MonetKeyColors.getOrNull(index - 1)?.second

fun AppSettings.Bundle.createThemeController(): ThemeController {
    val themeMode = when (themeBaseIndex.coerceIn(0, 2)) {
        1 -> if (!monet) ColorSchemeMode.Light else ColorSchemeMode.MonetLight
        2 -> if (!monet) ColorSchemeMode.Dark else ColorSchemeMode.MonetDark
        else -> if (!monet) ColorSchemeMode.System else ColorSchemeMode.MonetSystem
    }
    if (!monet) return ThemeController(colorSchemeMode = themeMode)

    val keyColor = monetKeyColorFor(monetSeedIndex)
    val paletteStyle = ThemePaletteStyle.entries.getOrNull(monetPaletteStyle)
        ?: ThemePaletteStyle.Content
    val colorSpec = ThemeColorSpec.entries.getOrNull(monetColorSpec)
        ?: ThemeColorSpec.Spec2021

    return ThemeController(
        colorSchemeMode = themeMode,
        keyColor = keyColor,
        paletteStyle = paletteStyle,
        colorSpec = colorSpec,
    )
}
