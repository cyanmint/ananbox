package io.github.miuzarte.scrcpyforandroid.scaffolds

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * `top.yukonga.miuix.kmp.basic.BreadcrumbBar`/`BreadcrumbItem` are referenced by the
 * vendored `io.github.miuzarte.scrcpyforandroid.pages.FileManagerScreen` (see
 * /NOTICE.md) but are not yet part of the published `miuix-ui` 0.9.3 release used by
 * this project. This is a small local stand-in reproducing the same call-site API
 * (a horizontally scrollable row of clickable path segments) so the vendored screen
 * keeps working unmodified.
 */
data class BreadcrumbItem(
    val path: String,
    val text: String,
)

@Composable
fun BreadcrumbBar(
    items: List<BreadcrumbItem>,
    onItemClick: (Int) -> Unit,
    highlightIndex: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            Text(
                modifier = Modifier
                    .clickable { onItemClick(index) }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                text = item.text,
                color = if (index == highlightIndex) {
                    MiuixTheme.colorScheme.onSurface
                } else {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                },
            )
            if (index != items.lastIndex) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                )
            }
        }
    }
}

