package io.github.miuzarte.scrcpyforandroid.widgets

import android.os.ParcelFileDescriptor
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.cyanmint.anbox.Anbox
import com.cyanmint.anbox.R
import io.github.miuzarte.scrcpyforandroid.container.ContainerProfileManager
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.pages.ContainerState
import io.github.miuzarte.scrcpyforandroid.services.EventLogger
import io.github.miuzarte.scrcpyforandroid.ui.contextClick
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.concurrent.thread

/**
 * Reads the container launch command's stdout/stderr (returned as a raw fd
 * by [Anbox.startContainer]) line by line and forwards each line to
 * [EventLogger] so it shows up in the app's log box, mirroring how scrcpy
 * server output is surfaced.
 */
private fun streamContainerOutputToLog(fd: Int) {
    if (fd < 0) return
    thread(start = true, name = "container-output-log") {
        try {
            ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.adoptFd(fd)).use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        EventLogger.logEvent(line)
                    }
                }
            }
        } catch (_: Exception) {
            // Pipe closed / process exited; nothing more to log.
        }
    }
}

/**
 * Renders this app's own container output (via the native Anbox renderer)
 * instead of a remote scrcpy video stream. Starts the container the first
 * time it becomes visible, provided a rootfs has been imported for the
 * active profile.
 */
@Composable
fun ContainerVideoSurface(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var runtimeStarted by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            if (runtimeStarted) {
                runCatching { Anbox.stopContainer() }
                runCatching { Anbox.stopRuntime() }
                runtimeStarted = false
            }
        }
    }

    if (!ContainerProfileManager.hasRootfs(context)) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(text = stringResource(R.string.container_rootfs_missing))
        }
        return
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceView(ctx).apply {
                holder.addCallback(object: SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        val windowManager =
                            ctx.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager
                        val metrics = android.util.DisplayMetrics()
                        @Suppress("DEPRECATION")
                        windowManager.defaultDisplay.getRealMetrics(metrics)
                        val profile = ContainerProfileManager.currentProfile(ctx)
                        Anbox.setPath(ContainerProfileManager.profileDir(ctx, profile).path)
                        if (!runtimeStarted) {
                            val ok = Anbox.initRuntime(width, height, metrics.densityDpi)
                            Anbox.createSurface(holder.surface)
                            if (ok) {
                                Anbox.startRuntime()
                                val prootCommand = ContainerProfileManager.getProotCommand(ctx, profile)
                                    ?: ContainerProfileManager.defaultProotCommand(ctx, profile)
                                EventLogger.logEvent(prootCommand)
                                val outputFd = Anbox.startContainer(prootCommand)
                                streamContainerOutputToLog(outputFd)
                            }
                            runtimeStarted = true
                        } else {
                            Anbox.createSurface(holder.surface)
                        }
                    }

                    override fun surfaceChanged(
                        holder: SurfaceHolder, format: Int, width: Int, height: Int,
                    ) {
                        Anbox.resetWindow(height, width)
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        runCatching { Anbox.destroySurface() }
                    }
                })
                setOnTouchListener { _, event -> Anbox.onTouch(this, event) }
            }
        },
    )
}

/**
 * Main-tab "Container" section: shows the active profile (instead of the
 * active adb connection), lets the user pick/rename/add profiles, import a
 * rootfs tarball for the selected profile, and start/stop the container,
 * showing the in-page native Anbox renderer once started.
 */
@Composable
fun ContainerSection(
    state: ContainerState,
    onSelectProfile: (String) -> Unit,
    onAddProfile: (String) -> Boolean,
    onRenameProfile: (String) -> Boolean,
    onDeleteProfile: (String) -> Unit,
    onImportRootfs: (targetProfile: String) -> Unit,
    launchCommand: () -> String,
    onSetLaunchCommand: (String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var showSelectDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var isFullscreen by rememberSaveable { mutableStateOf(false) }

    // Preserved across the compact card location and the fullscreen overlay so
    // the underlying SurfaceView (and running container) survives the move.
    val videoSurface = remember {
        movableContentOf<Modifier> { mod -> ContainerVideoSurface(modifier = mod) }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(UiSpacing.ContentVertical),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
        ) {
            Text(
                text = stringResource(R.string.container_section_title),
                style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.title4,
            )
            Text(text = stringResource(R.string.container_active_profile, state.activeProfile))
            Text(
                text = stringResource(
                    if (state.hasRootfs) R.string.container_rootfs_ready
                    else R.string.container_rootfs_missing,
                ),
                color = if (state.hasRootfs) colorScheme.primary else colorScheme.onSurfaceVariantSummary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal)) {
                TextButton(
                    text = stringResource(R.string.container_select_profile),
                    onClick = {
                        haptic.contextClick()
                        showSelectDialog = true
                    },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.container_edit_profile),
                    onClick = {
                        haptic.contextClick()
                        showEditDialog = true
                    },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.container_import_rootfs),
                    onClick = {
                        haptic.contextClick()
                        showImportDialog = true
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            TextButton(
                text = stringResource(
                    if (state.started) R.string.container_stop else R.string.container_start,
                ),
                onClick = {
                    haptic.contextClick()
                    if (state.started) onStop() else onStart()
                },
                enabled = state.hasRootfs,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
            if (state.started) {
                Box(modifier = Modifier.fillMaxWidth().height(240.dp)) {
                    videoSurface(Modifier.fillMaxSize())
                    IconButton(
                        onClick = {
                            haptic.contextClick()
                            isFullscreen = true
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(UiSpacing.Medium),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Fullscreen,
                            contentDescription = stringResource(R.string.cd_fullscreen),
                        )
                    }
                }
            }
        }
    }

    if (isFullscreen) {
        ContainerFullscreenOverlay(
            videoSurface = videoSurface,
            onExitFullscreen = { isFullscreen = false },
        )
    }

    ContainerSelectProfileDialog(
        show = showSelectDialog,
        state = state,
        onSelect = { onSelectProfile(it); showSelectDialog = false },
        onAdd = onAddProfile,
        onDismissRequest = { showSelectDialog = false },
    )

    ContainerEditProfileDialog(
        show = showEditDialog,
        state = state,
        onRename = onRenameProfile,
        onDelete = { onDeleteProfile(it); showEditDialog = false },
        launchCommand = launchCommand,
        onSetLaunchCommand = onSetLaunchCommand,
        onDismissRequest = { showEditDialog = false },
    )

    ContainerImportRootfsDialog(
        show = showImportDialog,
        state = state,
        onImport = { onImportRootfs(it); showImportDialog = false },
        onDismissRequest = { showImportDialog = false },
    )
}

/**
 * Fullscreen, immersive overlay for the in-page container renderer, mirroring
 * the fullscreen mode of the original Ananbox app: hides the system bars and
 * expands the running renderer to fill the entire screen.
 */
@Composable
private fun ContainerFullscreenOverlay(
    videoSurface: @Composable (Modifier) -> Unit,
    onExitFullscreen: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    Dialog(
        onDismissRequest = onExitFullscreen,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            if (window != null) {
                window.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                )
                WindowCompat.setDecorFitsSystemWindows(window, false)
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            onDispose {
                if (window != null) {
                    WindowInsetsControllerCompat(window, window.decorView).show(
                        WindowInsetsCompat.Type.systemBars(),
                    )
                    WindowCompat.setDecorFitsSystemWindows(window, true)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            videoSurface(Modifier.fillMaxSize())
            IconButton(
                onClick = {
                    haptic.contextClick()
                    onExitFullscreen()
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(UiSpacing.Medium),
            ) {
                Icon(
                    imageVector = Icons.Rounded.FullscreenExit,
                    contentDescription = stringResource(R.string.cd_fullscreen),
                )
            }
        }
    }
}

@Composable
private fun ContainerSelectProfileDialog(
    show: Boolean,
    state: ContainerState,
    onSelect: (String) -> Unit,
    onAdd: (String) -> Boolean,
    onDismissRequest: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var newProfileName by rememberSaveable(show) { mutableStateOf("") }

    OverlayDialog(
        show = show,
        title = stringResource(R.string.container_select_profile),
        onDismissRequest = onDismissRequest,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.Medium)) {
            state.profiles.forEach { profile ->
                TextButton(
                    text = profile,
                    onClick = {
                        haptic.contextClick()
                        onSelect(profile)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (profile == state.activeProfile)
                        ButtonDefaults.textButtonColorsPrimary()
                    else ButtonDefaults.textButtonColors(),
                )
            }
            Spacer(Modifier.height(UiSpacing.Medium))
            TextField(
                value = newProfileName,
                onValueChange = { newProfileName = it },
                label = stringResource(R.string.container_new_profile_name),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                text = stringResource(R.string.container_new_profile),
                onClick = {
                    haptic.contextClick()
                    if (onAdd(newProfileName.trim())) {
                        newProfileName = ""
                        onDismissRequest()
                    }
                },
                enabled = newProfileName.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ContainerEditProfileDialog(
    show: Boolean,
    state: ContainerState,
    onRename: (String) -> Boolean,
    onDelete: (String) -> Unit,
    launchCommand: () -> String,
    onSetLaunchCommand: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var name by rememberSaveable(show, state.activeProfile) { mutableStateOf(state.activeProfile) }
    var command by rememberSaveable(show, state.activeProfile) { mutableStateOf(launchCommand()) }

    OverlayDialog(
        show = show,
        title = stringResource(R.string.container_edit_profile),
        onDismissRequest = onDismissRequest,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.Medium)) {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.container_rename_profile),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal)) {
                TextButton(
                    text = stringResource(R.string.button_cancel),
                    onClick = {
                        haptic.contextClick()
                        onDismissRequest()
                    },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.container_rename_profile),
                    onClick = {
                        haptic.contextClick()
                        if (onRename(name.trim())) onDismissRequest()
                    },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
            TextField(
                value = command,
                onValueChange = { command = it },
                label = stringResource(R.string.container_launch_command_hint),
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                text = stringResource(R.string.container_launch_command),
                onClick = {
                    haptic.contextClick()
                    onSetLaunchCommand(command)
                },
                enabled = command.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
            TextButton(
                text = stringResource(R.string.container_delete_profile),
                onClick = {
                    haptic.contextClick()
                    onDelete(state.activeProfile)
                },
                enabled = state.profiles.size > 1,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ContainerImportRootfsDialog(
    show: Boolean,
    state: ContainerState,
    onImport: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var selected by rememberSaveable(show, state.activeProfile) { mutableStateOf(state.activeProfile) }

    OverlayDialog(
        show = show,
        title = stringResource(R.string.container_import_destination_title),
        onDismissRequest = onDismissRequest,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.Medium)) {
            state.profiles.forEach { profile ->
                TextButton(
                    text = profile,
                    onClick = {
                        haptic.contextClick()
                        selected = profile
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (profile == selected)
                        ButtonDefaults.textButtonColorsPrimary()
                    else ButtonDefaults.textButtonColors(),
                )
            }
            TextButton(
                text = if (state.importing)
                    stringResource(R.string.container_importing)
                else stringResource(R.string.container_import_rootfs),
                onClick = {
                    haptic.contextClick()
                    onImport(selected)
                },
                enabled = !state.importing,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}
