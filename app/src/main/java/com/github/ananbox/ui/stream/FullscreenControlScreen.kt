package com.github.ananbox.ui.stream

import android.graphics.Rect
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.github.ananbox.ui.component.FloatingBottomBar
import com.github.ananbox.ui.component.FloatingBottomBarItemSpec
import com.github.ananbox.ui.component.VirtualButtonAction
import com.github.ananbox.ui.component.VirtualButtonBar
import com.github.ananbox.ui.component.VirtualButtonCallbacks
import com.github.ananbox.ui.constants.UiMotion
import com.github.ananbox.ui.constants.UiSpacing
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import kotlinx.coroutines.delay

@Composable
fun FullscreenControlScreen(
    fullscreen: Boolean,
    onFullscreenToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onHome: () -> Unit = {},
    onRecents: () -> Unit = {},
    onPower: () -> Unit = {},
    onVolumeUp: () -> Unit = {},
    onVolumeDown: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenConsole: () -> Unit = {},
    showVirtualButtons: Boolean = true,
    showFloatingToolbar: Boolean = true,
    showFloatingButtonPalette: Boolean = false,
    virtualButtonDock: VirtualButtonBar.FullscreenDock = VirtualButtonBar.FullscreenDock.BOTTOM,
    reverseVirtualButtonOrder: Boolean = false,
    virtualButtonThickness: androidx.compose.ui.unit.Dp = 54.dp,
    onVideoBoundsInWindowChanged: (Rect?) -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val activity = LocalActivity.current
    var controlsVisible by rememberSaveable(fullscreen) { mutableStateOf(true) }
    var toolbarExpanded by rememberSaveable { mutableStateOf(false) }

    val callbacks = remember(
        onBack,
        onHome,
        onRecents,
        onPower,
        onVolumeUp,
        onVolumeDown,
        onFullscreenToggle,
        onOpenSettings,
        onOpenConsole,
    ) {
        VirtualButtonCallbacks(
            onBack = onBack,
            onHome = onHome,
            onRecents = onRecents,
            onPower = onPower,
            onVolumeUp = onVolumeUp,
            onVolumeDown = onVolumeDown,
            onFullscreenToggle = onFullscreenToggle,
            onOpenSettings = onOpenSettings,
            onOpenConsole = onOpenConsole,
        )
    }
    val virtualButtons = remember {
        VirtualButtonBar(
            primaryActions = listOf(
                VirtualButtonAction.BACK,
                VirtualButtonAction.HOME,
                VirtualButtonAction.RECENTS,
                VirtualButtonAction.MORE,
            ),
            overflowActions = listOf(
                VirtualButtonAction.POWER,
                VirtualButtonAction.VOLUME_DOWN,
                VirtualButtonAction.VOLUME_UP,
            ),
        )
    }

    val toolbarItems = remember(fullscreen, onFullscreenToggle, onOpenSettings, onOpenConsole) {
        listOf(
            FloatingBottomBarItemSpec(
                key = "fullscreen",
                label = if (fullscreen) "Exit" else "Fullscreen",
                icon = if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                onClick = onFullscreenToggle,
            ),
            FloatingBottomBarItemSpec(
                key = "settings",
                label = "Settings",
                icon = Icons.Rounded.Settings,
                onClick = onOpenSettings,
            ),
            FloatingBottomBarItemSpec(
                key = "console",
                label = "Console",
                icon = Icons.Rounded.Code,
                onClick = onOpenConsole,
            ),
        )
    }
    val overflowToolbarItems = remember(fullscreen, callbacks) {
        listOf(
            FloatingBottomBarItemSpec(
                key = "back",
                label = "Back",
                icon = VirtualButtonAction.BACK.icon,
                onClick = callbacks.onBack,
            ),
            FloatingBottomBarItemSpec(
                key = "home",
                label = "Home",
                icon = VirtualButtonAction.HOME.icon,
                onClick = callbacks.onHome,
            ),
            FloatingBottomBarItemSpec(
                key = "recents",
                label = "Recents",
                icon = VirtualButtonAction.RECENTS.icon,
                onClick = callbacks.onRecents,
            ),
        )
    }

    BackHandler(enabled = true) {
        if (fullscreen) onFullscreenToggle() else onBack()
    }

    DisposableEffect(activity, fullscreen) {
        val window = activity?.window ?: return@DisposableEffect onDispose {}
        WindowCompat.setDecorFitsSystemWindows(window, !fullscreen)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (fullscreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            WindowInsetsControllerCompat(window, window.decorView).show(
                WindowInsetsCompat.Type.systemBars(),
            )
            WindowCompat.setDecorFitsSystemWindows(window, true)
        }
    }

    LaunchedEffect(fullscreen, controlsVisible) {
        if (fullscreen && controlsVisible) {
            delay((UiMotion.PAGE_SWITCH_STIFFNESS * UiMotion.PAGE_SWITCH_DAMPING_RATIO * 3).toLong())
            controlsVisible = false
        }
    }

    StreamScreen(
        modifier = modifier,
        chromeVisible = controlsVisible && showFloatingToolbar,
        onVideoBoundsInWindowChanged = onVideoBoundsInWindowChanged,
        toolbar = {
            FloatingBottomBar(
                items = toolbarItems,
                overflowItems = overflowToolbarItems,
                expanded = toolbarExpanded,
                onExpandedChange = { toolbarExpanded = it },
            )
        },
        overlay = {
            Box(modifier = Modifier.fillMaxSize()) {
                if (showVirtualButtons && controlsVisible) {
                    virtualButtons.Fullscreen(
                        callbacks = callbacks,
                        dock = virtualButtonDock,
                        reverseOrder = reverseVirtualButtonOrder,
                        thickness = virtualButtonThickness,
                        modifier = Modifier.align(
                            when (virtualButtonDock) {
                                VirtualButtonBar.FullscreenDock.TOP -> Alignment.TopCenter
                                VirtualButtonBar.FullscreenDock.BOTTOM -> Alignment.BottomCenter
                                VirtualButtonBar.FullscreenDock.LEFT -> Alignment.CenterStart
                                VirtualButtonBar.FullscreenDock.RIGHT -> Alignment.CenterEnd
                            },
                        ),
                    )
                }

                if (showFloatingButtonPalette && controlsVisible) {
                    virtualButtons.FloatingBall(
                        callbacks = callbacks,
                        modifier = Modifier.fillMaxSize(),
                        actions = listOf(
                            VirtualButtonAction.BACK,
                            VirtualButtonAction.HOME,
                            VirtualButtonAction.RECENTS,
                            VirtualButtonAction.FULLSCREEN,
                            VirtualButtonAction.SETTINGS,
                            VirtualButtonAction.CONSOLE,
                        ),
                    )
                }

                EdgeRevealHandle(
                    visible = fullscreen && !controlsVisible,
                    onClick = { controlsVisible = true },
                )

                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn() + slideInVertically { -it / 2 },
                    exit = fadeOut() + slideOutVertically { -it / 2 },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(UiSpacing.Large),
                ) {
                    ControlChipRow(
                        fullscreen = fullscreen,
                        onFullscreenToggle = {
                            controlsVisible = true
                            onFullscreenToggle()
                        },
                        onOpenSettings = onOpenSettings,
                        onOpenConsole = onOpenConsole,
                    )
                }
            }
        },
        content = {
            Box(
                modifier = Modifier.fillMaxSize(),
                content = content,
            )
        },
    )
}

@Composable
private fun ControlChipRow(
    fullscreen: Boolean,
    onFullscreenToggle: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenConsole: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.32f))
            .padding(UiSpacing.Small),
        horizontalArrangement = Arrangement.spacedBy(UiSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChipButton(
            icon = if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
            label = if (fullscreen) "Windowed" else "Fullscreen",
            onClick = onFullscreenToggle,
        )
        ChipButton(
            icon = Icons.Rounded.Settings,
            label = "Settings",
            onClick = onOpenSettings,
        )
        ChipButton(
            icon = Icons.Rounded.Code,
            label = "Console",
            onClick = onOpenConsole,
        )
    }
}

@Composable
private fun ChipButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        colors = ButtonDefaults.buttonColors(
            color = colorScheme.surface.copy(alpha = 0.90f),
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = colorScheme.onSurface,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(UiSpacing.Small))
        Text(text = label, color = colorScheme.onSurface)
    }
}

@Composable
private fun BoxScope.EdgeRevealHandle(
    visible: Boolean,
    onClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = UiSpacing.SectionTitleTop),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(UiSpacing.Small),
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.36f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text(text = "Show controls", color = Color.White)
            }
            Box(
                modifier = Modifier
                    .width(46.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.8f)),
            )
        }
    }
}
