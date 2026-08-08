package com.github.ananbox.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.github.ananbox.ui.constants.UiSpacing
import com.github.ananbox.ui.contextClick
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import kotlin.math.roundToInt

enum class VirtualButtonAction(
    val id: String,
    val title: String,
    val icon: ImageVector,
) {
    MORE("more", "More", Icons.Rounded.MoreHoriz),
    BACK("back", "Back", Icons.AutoMirrored.Rounded.ArrowBack),
    HOME("home", "Home", Icons.Rounded.Home),
    RECENTS("recents", "Recents", Icons.Rounded.Apps),
    POWER("power", "Power", Icons.Rounded.PowerSettingsNew),
    VOLUME_UP("volume_up", "Volume +", Icons.AutoMirrored.Rounded.VolumeUp),
    VOLUME_DOWN("volume_down", "Volume -", Icons.AutoMirrored.Rounded.VolumeDown),
    FULLSCREEN("fullscreen", "Fullscreen", Icons.Rounded.Fullscreen),
    SETTINGS("settings", "Settings", Icons.Rounded.Settings),
    CONSOLE("console", "Console", Icons.Rounded.Code),
}

data class VirtualButtonCallbacks(
    val onBack: () -> Unit = {},
    val onHome: () -> Unit = {},
    val onRecents: () -> Unit = {},
    val onPower: () -> Unit = {},
    val onVolumeUp: () -> Unit = {},
    val onVolumeDown: () -> Unit = {},
    val onFullscreenToggle: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onOpenConsole: () -> Unit = {},
) {
    fun invoke(action: VirtualButtonAction) {
        when (action) {
            VirtualButtonAction.BACK -> onBack()
            VirtualButtonAction.HOME -> onHome()
            VirtualButtonAction.RECENTS -> onRecents()
            VirtualButtonAction.POWER -> onPower()
            VirtualButtonAction.VOLUME_UP -> onVolumeUp()
            VirtualButtonAction.VOLUME_DOWN -> onVolumeDown()
            VirtualButtonAction.FULLSCREEN -> onFullscreenToggle()
            VirtualButtonAction.SETTINGS -> onOpenSettings()
            VirtualButtonAction.CONSOLE -> onOpenConsole()
            VirtualButtonAction.MORE -> Unit
        }
    }
}

class VirtualButtonBar(
    private val primaryActions: List<VirtualButtonAction>,
    private val overflowActions: List<VirtualButtonAction> = emptyList(),
) {
    enum class FullscreenDock {
        TOP,
        BOTTOM,
        LEFT,
        RIGHT,
    }

    @Composable
    fun Preview(
        callbacks: VirtualButtonCallbacks,
        modifier: Modifier = Modifier,
        showLabels: Boolean = false,
    ) {
        var showOverflow by remember { mutableStateOf(false) }

        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(UiSpacing.Small),
        ) {
            AnimatedVisibility(visible = showOverflow && overflowActions.isNotEmpty()) {
                ActionStrip(
                    actions = overflowActions,
                    callbacks = callbacks,
                    showLabels = showLabels,
                    orientation = FullscreenDock.TOP,
                    onMoreClick = { showOverflow = false },
                )
            }
            ActionStrip(
                actions = primaryActions,
                callbacks = callbacks,
                showLabels = showLabels,
                orientation = FullscreenDock.BOTTOM,
                onMoreClick = { showOverflow = !showOverflow },
            )
        }
    }

    @Composable
    fun Fullscreen(
        callbacks: VirtualButtonCallbacks,
        modifier: Modifier = Modifier,
        dock: FullscreenDock = FullscreenDock.BOTTOM,
        reverseOrder: Boolean = false,
        thickness: Dp = 54.dp,
    ) {
        var showOverflow by remember { mutableStateOf(false) }
        val actions = if (reverseOrder) primaryActions.asReversed() else primaryActions

        Box(modifier = modifier) {
            ActionStrip(
                actions = actions,
                callbacks = callbacks,
                showLabels = false,
                orientation = dock,
                thickness = thickness,
                onMoreClick = { showOverflow = !showOverflow },
            )

            if (overflowActions.isNotEmpty()) {
                val alignment = when (dock) {
                    FullscreenDock.TOP -> Alignment.TopCenter
                    FullscreenDock.BOTTOM -> Alignment.BottomCenter
                    FullscreenDock.LEFT -> Alignment.CenterStart
                    FullscreenDock.RIGHT -> Alignment.CenterEnd
                }
                AnimatedVisibility(
                    visible = showOverflow,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                    modifier = Modifier.align(alignment),
                ) {
                    val expansionModifier = when (dock) {
                        FullscreenDock.TOP -> Modifier.padding(top = thickness + UiSpacing.Small)
                        FullscreenDock.BOTTOM -> Modifier.padding(bottom = thickness + UiSpacing.Small)
                        FullscreenDock.LEFT -> Modifier.padding(start = thickness + UiSpacing.Small)
                        FullscreenDock.RIGHT -> Modifier.padding(end = thickness + UiSpacing.Small)
                    }
                    ActionStrip(
                        actions = overflowActions,
                        callbacks = callbacks,
                        showLabels = false,
                        orientation = dock,
                        modifier = expansionModifier,
                        thickness = thickness,
                        onMoreClick = { showOverflow = false },
                    )
                }
            }
        }
    }

    @Composable
    fun FloatingBall(
        callbacks: VirtualButtonCallbacks,
        modifier: Modifier = Modifier,
        actions: List<VirtualButtonAction> = primaryActions.filterNot { it == VirtualButtonAction.MORE },
    ) {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val bubbleSize = 56.dp
            val bubbleSizePx = with(density) { bubbleSize.toPx() }
            var offsetXPx by rememberSaveable { mutableFloatStateOf(constraints.maxWidth * 0.82f) }
            var offsetYPx by rememberSaveable { mutableFloatStateOf(constraints.maxHeight * 0.62f) }
            var expanded by remember { mutableStateOf(false) }
            val maxX = (constraints.maxWidth - bubbleSizePx).coerceAtLeast(0f)
            val maxY = (constraints.maxHeight - bubbleSizePx).coerceAtLeast(0f)

            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetXPx.roundToInt(), offsetYPx.roundToInt()) },
            ) {
                AnimatedVisibility(
                    visible = expanded,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                ) {
                    Row(
                        modifier = Modifier
                            .padding(end = bubbleSize + UiSpacing.Small)
                            .clip(CircleShape)
                            .background(colorScheme.surface.copy(alpha = 0.92f))
                            .padding(UiSpacing.Small),
                        horizontalArrangement = Arrangement.spacedBy(UiSpacing.Small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        actions.forEach { action ->
                            CompactActionButton(
                                action = action,
                                onClick = {
                                    callbacks.invoke(action)
                                    expanded = false
                                },
                            )
                        }
                    }
                }

                BubbleHandle(
                    modifier = Modifier
                        .size(bubbleSize)
                        .pointerInput(maxX, maxY) {
                            detectDragGestures(
                                onDragStart = { expanded = false },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    offsetXPx = (offsetXPx + dragAmount.x).coerceIn(0f, maxX)
                                    offsetYPx = (offsetYPx + dragAmount.y).coerceIn(0f, maxY)
                                },
                            )
                        },
                    expanded = expanded,
                    onClick = { expanded = !expanded },
                )
            }
        }
    }

    @Composable
    private fun ActionStrip(
        actions: List<VirtualButtonAction>,
        callbacks: VirtualButtonCallbacks,
        orientation: FullscreenDock,
        modifier: Modifier = Modifier,
        showLabels: Boolean,
        thickness: Dp = 54.dp,
        onMoreClick: () -> Unit,
    ) {
        val isVertical = orientation == FullscreenDock.LEFT || orientation == FullscreenDock.RIGHT
        val stripModifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.30f))
            .padding(UiSpacing.Small)

        if (isVertical) {
            Column(
                modifier = stripModifier.width(thickness),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.Small),
            ) {
                actions.forEach { action ->
                    ActionButton(
                        action = action,
                        callbacks = callbacks,
                        showLabels = showLabels,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(thickness),
                        onMoreClick = onMoreClick,
                    )
                }
            }
        } else {
            Row(
                modifier = stripModifier.height(thickness),
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions.forEach { action ->
                    ActionButton(
                        action = action,
                        callbacks = callbacks,
                        showLabels = showLabels,
                        modifier = Modifier
                            .height(thickness)
                            .weight(1f),
                        onMoreClick = onMoreClick,
                    )
                }
            }
        }
    }

    @Composable
    private fun ActionButton(
        action: VirtualButtonAction,
        callbacks: VirtualButtonCallbacks,
        showLabels: Boolean,
        modifier: Modifier = Modifier,
        onMoreClick: () -> Unit,
    ) {
        val haptic = LocalHapticFeedback.current
        Button(
            onClick = {
                haptic.contextClick()
                if (action == VirtualButtonAction.MORE) onMoreClick() else callbacks.invoke(action)
            },
            modifier = modifier,
            insideMargin = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(color = Color.Transparent),
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = action.title,
                tint = Color.White,
            )
            if (showLabels) {
                Spacer(modifier = Modifier.width(UiSpacing.Small))
                Text(text = action.title, color = Color.White)
            }
        }
    }

    @Composable
    private fun CompactActionButton(
        action: VirtualButtonAction,
        onClick: () -> Unit,
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier.size(46.dp),
            insideMargin = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(
                color = colorScheme.primary.copy(alpha = 0.92f),
            ),
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = action.title,
                tint = colorScheme.onPrimary,
            )
        }
    }

    @Composable
    private fun BoxScope.BubbleHandle(
        modifier: Modifier = Modifier,
        expanded: Boolean,
        onClick: () -> Unit,
    ) {
        val haptic = LocalHapticFeedback.current
        Button(
            onClick = {
                haptic.contextClick()
                onClick()
            },
            modifier = modifier.align(Alignment.CenterEnd),
            insideMargin = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(
                color = colorScheme.primary.copy(alpha = if (expanded) 0.72f else 0.92f),
            ),
        ) {
            Icon(
                imageVector = if (expanded) Icons.Rounded.MoreHoriz else Icons.Rounded.Apps,
                contentDescription = "Virtual controls",
                tint = colorScheme.onPrimary,
            )
        }
    }
}
