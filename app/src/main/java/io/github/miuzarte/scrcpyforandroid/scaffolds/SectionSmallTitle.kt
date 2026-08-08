package io.github.miuzarte.scrcpyforandroid.scaffolds

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

@Composable
fun SectionSmallTitle(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = colorScheme.onBackgroundVariant,
    insideMargin: PaddingValues = PaddingValues(16.dp, 8.dp),
) {
    SmallTitle(
        text = text,
        modifier = modifier,
        textColor = textColor,
        insideMargin = insideMargin,
    )
}
