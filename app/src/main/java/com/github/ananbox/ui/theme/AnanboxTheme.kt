package com.github.ananbox.ui.theme

import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AnanboxTheme(
    content: @Composable () -> Unit,
) {
    MiuixTheme {
        content()
    }
}
