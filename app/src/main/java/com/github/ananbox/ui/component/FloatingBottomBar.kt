package com.github.ananbox.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.ananbox.ui.constants.UiSpacing
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

data class FloatingBottomBarItemSpec(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)

@Composable
fun RowScope.FloatingBottomBarItem(
    item: FloatingBottomBarItemSpec,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = item.onClick,
        enabled = item.enabled,
        modifier = modifier.weight(1f),
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        colors = ButtonDefaults.buttonColors(
            color = colorScheme.primary.copy(alpha = 0.92f),
            disabledColor = colorScheme.primary.copy(alpha = 0.35f),
        ),
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = colorScheme.onPrimary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(UiSpacing.Small))
        Text(
            text = item.label,
            color = colorScheme.onPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun FloatingBottomBar(
    items: List<FloatingBottomBarItemSpec>,
    modifier: Modifier = Modifier,
    overflowItems: List<FloatingBottomBarItemSpec> = emptyList(),
    expanded: Boolean = false,
    onExpandedChange: (Boolean) -> Unit = {},
) {
    var localExpanded by remember(expanded) { mutableStateOf(expanded) }
    val showOverflow = localExpanded || expanded

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedVisibility(
            visible = showOverflow && overflowItems.isNotEmpty(),
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            Column(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colorScheme.surface.copy(alpha = 0.90f))
                    .padding(UiSpacing.Medium),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.Small),
            ) {
                overflowItems.forEach { item ->
                    Button(
                        onClick = {
                            item.onClick()
                            localExpanded = false
                            onExpandedChange(false)
                        },
                        enabled = item.enabled,
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        colors = ButtonDefaults.buttonColors(
                            color = colorScheme.secondary.copy(alpha = 0.92f),
                            disabledColor = colorScheme.secondary.copy(alpha = 0.35f),
                        ),
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = colorScheme.onSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(UiSpacing.Small))
                        Text(text = item.label, color = colorScheme.onSecondary)
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(colorScheme.surface.copy(alpha = 0.88f))
                .padding(6.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { item ->
                    FloatingBottomBarItem(item = item)
                }

                if (overflowItems.isNotEmpty()) {
                    val interactionSource = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                            ) {
                                localExpanded = !showOverflow
                                onExpandedChange(!showOverflow)
                            }
                            .background(colorScheme.surfaceVariant.copy(alpha = 0.80f))
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (showOverflow) "Close" else "More",
                            color = colorScheme.onSurfaceContainerVariant,
                        )
                    }
                }
            }
        }
    }
}
