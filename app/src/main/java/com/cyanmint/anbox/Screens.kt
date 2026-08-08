package com.cyanmint.anbox

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

/**
 * Shared Miuix screen composables used by the tabbed [MainActivity] shell.
 *
 * The screen structure (Scaffold + large-title TopAppBar, section
 * [SmallTitle]s, grouped [Card]s of [ArrowPreference] rows, and the bottom
 * NavigationBar in [MainActivity]) is adapted from ScrcpyForAndroid by
 * Miuzarte (https://github.com/Miuzarte/ScrcpyForAndroid, Apache License 2.0);
 * see /NOTICE.md and Settings -> About.
 */

/** Combine an outer bottom-bar padding with an inner content padding. */
@Composable
private fun mergePadding(outer: PaddingValues, inner: PaddingValues): PaddingValues {
    val dir = LocalLayoutDirection.current
    return PaddingValues(
        start = outer.calculateStartPadding(dir) + inner.calculateStartPadding(dir),
        end = outer.calculateEndPadding(dir) + inner.calculateEndPadding(dir),
        top = outer.calculateTopPadding() + inner.calculateTopPadding(),
        bottom = outer.calculateBottomPadding() + inner.calculateBottomPadding(),
    )
}

// ---------------------------------------------------------------------------
// Container (launcher) tab
// ---------------------------------------------------------------------------

@Composable
fun ContainerScreen(
    profiles: List<String>,
    currentProfile: String,
    scrollBehavior: ScrollBehavior,
    bottomPadding: PaddingValues,
    onSelectProfile: (String) -> Unit,
    onAddProfile: (String) -> Unit,
    onRemoveProfile: () -> Unit,
    onStart: () -> Unit,
) {
    val context = LocalContext.current
    var showProfilePicker by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }

    val hasRootfs = ProfileManager.hasRootfs(context, currentProfile)
    val statusText = if (hasRootfs) {
        context.getString(R.string.profile_rootfs_ready, currentProfile)
    } else {
        context.getString(R.string.profile_rootfs_missing, currentProfile)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = context.getString(R.string.app_name),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val merged = mergePadding(bottomPadding, padding)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(merged),
        ) {
            SmallTitle(text = context.getString(R.string.profile_label))
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                ArrowPreference(
                    title = context.getString(R.string.profile_label),
                    summary = currentProfile,
                    onClick = { showProfilePicker = true },
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                Button(
                    onClick = { newProfileName = ""; showAddDialog = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = context.getString(R.string.profile_add))
                }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = { showRemoveDialog = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = context.getString(R.string.profile_remove))
                }
            }

            SmallTitle(text = context.getString(R.string.settings_category_container))
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                BasicComponent(
                    title = context.getString(R.string.profile_label),
                    summary = statusText,
                )
            }

            Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColorsPrimary(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 16.dp),
            ) {
                Text(text = context.getString(R.string.action_start))
            }
        }
    }

    WindowDialog(
        show = showProfilePicker,
        title = context.getString(R.string.profile_label),
        onDismissRequest = { showProfilePicker = false },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            profiles.forEach { name ->
                ArrowPreference(
                    title = name,
                    onClick = {
                        onSelectProfile(name)
                        showProfilePicker = false
                    },
                )
            }
        }
    }

    WindowDialog(
        show = showAddDialog,
        title = context.getString(R.string.profile_add),
        onDismissRequest = { showAddDialog = false },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = newProfileName,
                onValueChange = { newProfileName = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    text = context.getString(R.string.cancel),
                    onClick = { showAddDialog = false },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                TextButton(
                    text = context.getString(R.string.ok),
                    onClick = {
                        onAddProfile(newProfileName.trim())
                        showAddDialog = false
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    WindowDialog(
        show = showRemoveDialog,
        title = context.getString(R.string.profile_remove),
        summary = context.getString(R.string.profile_remove_confirm, currentProfile),
        onDismissRequest = { showRemoveDialog = false },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(
                text = context.getString(R.string.cancel),
                onClick = { showRemoveDialog = false },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            TextButton(
                text = context.getString(R.string.ok),
                onClick = {
                    onRemoveProfile()
                    showRemoveDialog = false
                },
                colors = ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Console tab
// ---------------------------------------------------------------------------

/**
 * A self-contained console tab hosting the legacy [TerminalView] via
 * [androidx.compose.ui.viewinterop.AndroidView].
 *
 * The [TerminalView] initialises its `mRenderer` in its own `init {}` block,
 * and [TerminalView.attachSession]/[TerminalView.setTerminalViewClient] are
 * invoked inside the AndroidView `factory` lambda -- i.e. immediately after
 * construction and before the view is attached to the window -- so the first
 * layout/`onSizeChanged` -> `updateSize()` pass never dereferences a null
 * renderer. See /NOTICE.md and the console-crash fix in TerminalView.kt.
 */
@Composable
fun ConsoleScreen(
    scrollBehavior: ScrollBehavior,
    bottomPadding: PaddingValues,
) {
    val context = LocalContext.current
    val controller = remember { ConsoleController(context.applicationContext) }

    DisposableEffect(controller) {
        onDispose { controller.stop() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = context.getString(R.string.title_activity_console),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val merged = mergePadding(bottomPadding, padding)
        androidx.compose.ui.viewinterop.AndroidView(
            modifier = Modifier.fillMaxSize().padding(merged),
            factory = { ctx ->
                TerminalView(ctx, null).apply {
                    setBackgroundColor(AndroidColor.BLACK)
                    isFocusable = true
                    isFocusableInTouchMode = true
                    controller.attach(this)
                }
            },
        )
    }
}

/**
 * Owns the shell [Process] and [TerminalSession] backing a [ConsoleScreen],
 * decoupled from any Activity so it can live inside a Compose tab.
 */
class ConsoleController(private val appContext: android.content.Context) : TerminalViewClient {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var process: Process? = null
    private var session: TerminalSession? = null
    private var view: TerminalView? = null

    fun attach(terminalView: TerminalView) {
        view = terminalView
        val appDir = appContext.filesDir
        appDir.mkdirs()

        val newSession = TerminalSession(
            shellWriter = { data, offset, count ->
                try {
                    process?.outputStream?.write(data, offset, count)
                    process?.outputStream?.flush()
                } catch (e: IOException) {
                    // shell process gone
                }
            },
            onScreenUpdated = {
                mainHandler.post { view?.onScreenUpdated() }
            },
            onCopyTextToClipboardRequested = { },
            onPasteTextFromClipboardRequested = { },
            onBellRequested = { },
        )
        session = newSession
        terminalView.attachSession(newSession)
        terminalView.setTerminalViewClient(this)

        thread {
            try {
                val proc = ProcessBuilder("/system/bin/sh", "-i")
                    .directory(appDir)
                    .redirectErrorStream(true)
                    .start()
                process = proc
                val buffer = ByteArray(4096)
                val input = proc.inputStream
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    newSession.append(buffer, read)
                }
            } catch (e: IOException) {
                val msg = ("Failed to start shell: " + e.message + "\r\n")
                    .toByteArray(StandardCharsets.UTF_8)
                newSession.append(msg, msg.size)
            }
        }
    }

    fun stop() {
        process?.destroy()
        process = null
        view = null
    }

    override fun onScale(scale: Float): Float = 1.0f
    override fun onSingleTapUp(e: MotionEvent) {}
    override fun shouldBackButtonBeMappedToEscape(): Boolean = false
    override fun shouldEnforceCharBasedInput(): Boolean = true
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
    override fun isTerminalViewSelected(): Boolean = true
    override fun copyModeChanged(copyMode: Boolean) {}
    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean = false
    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false
    override fun onLongPress(event: MotionEvent): Boolean = false
    override fun readControlKey(): Boolean = false
    override fun readAltKey(): Boolean = false
    override fun readShiftKey(): Boolean = false
    override fun readFnKey(): Boolean = false
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false
    override fun onEmulatorSet() {}
    override fun logError(tag: String?, message: String?) {}
    override fun logWarn(tag: String?, message: String?) {}
    override fun logInfo(tag: String?, message: String?) {}
    override fun logDebug(tag: String?, message: String?) {}
    override fun logVerbose(tag: String?, message: String?) {}
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
    override fun logStackTrace(tag: String?, e: Exception?) {}
}

// ---------------------------------------------------------------------------
// File browser tab
// ---------------------------------------------------------------------------

private data class FileEntry(val file: File, val isDirectory: Boolean)

private sealed interface Listing {
    data class Ok(val entries: List<FileEntry>) : Listing
    data object Error : Listing
}

private fun listDirectory(dir: File): Listing {
    val children = dir.listFiles() ?: return Listing.Error
    val entries = children
        .map { FileEntry(it, it.isDirectory) }
        .sortedWith(compareByDescending<FileEntry> { it.isDirectory }.thenBy { it.file.name.lowercase() })
    return Listing.Ok(entries)
}

/**
 * File browser rooted at [rootDir] (this app's internal storage). Backed by
 * [mutableStateOf] so navigation always re-renders; shows an explicit empty
 * state and a read-error state instead of a silently blank list -- the root
 * cause of the old ListView blank-screen bug.
 */
@Composable
fun FileBrowserScreen(
    rootDir: File,
    scrollBehavior: ScrollBehavior,
    bottomPadding: PaddingValues,
    onOpenFile: (File) -> Unit,
) {
    val context = LocalContext.current
    var currentDir by remember { mutableStateOf(rootDir) }

    val atRoot = currentDir == rootDir
    val relativePath = "/" + rootDir.toURI().relativize(currentDir.toURI()).path
    val listing = remember(currentDir) { listDirectory(currentDir) }

    fun goUp() {
        if (!atRoot) currentDir = currentDir.parentFile ?: rootDir
    }

    androidx.activity.compose.BackHandler(enabled = !atRoot) { goUp() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = context.getString(R.string.title_activity_file_browser),
                subtitle = if (atRoot) context.getString(R.string.file_browser_root) else relativePath,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val merged = mergePadding(bottomPadding, padding)
        Box(modifier = Modifier.fillMaxSize().padding(merged)) {
            when (listing) {
                is Listing.Error -> CenteredMessage(context.getString(R.string.file_browser_error))
                is Listing.Ok -> {
                    if (listing.entries.isEmpty() && atRoot) {
                        CenteredMessage(context.getString(R.string.file_browser_empty))
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            if (!atRoot) {
                                item(key = "..") {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 4.dp),
                                    ) {
                                        BasicComponent(title = "..", onClick = { goUp() })
                                    }
                                }
                            }
                            if (listing.entries.isEmpty()) {
                                item(key = "__empty__") {
                                    CenteredMessage(context.getString(R.string.file_browser_empty))
                                }
                            }
                            items(listing.entries, key = { it.file.absolutePath }) { entry ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 4.dp),
                                ) {
                                    BasicComponent(
                                        title = if (entry.isDirectory) entry.file.name + "/" else entry.file.name,
                                        onClick = {
                                            if (entry.isDirectory) currentDir = entry.file
                                            else onOpenFile(entry.file)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            textAlign = TextAlign.Center,
        )
    }
}

fun openInternalFile(activity: android.app.Activity, file: File) {
    try {
        val uri = Uri.parse(
            "content://" + "${activity.packageName}.documents/document/" +
                Uri.encode(file.absolutePath, "/"),
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, activity.contentResolver.getType(uri) ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(intent)
    } catch (e: Exception) {
        // No viewer available for this file; ignore.
    }
}

// ---------------------------------------------------------------------------
// Settings tab
// ---------------------------------------------------------------------------

@Composable
fun SettingsScreen(
    scrollBehavior: ScrollBehavior,
    bottomPadding: PaddingValues,
    onShutdown: () -> Unit,
    importRom: (Uri, String) -> Unit,
    importProgress: Boolean,
) {
    val context = LocalContext.current

    var showProotDialog by remember { mutableStateOf(false) }
    var prootText by remember { mutableStateOf("") }
    var showDestinationDialog by remember { mutableStateOf(false) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var showAboutDialog by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            pendingUri = uri
            showDestinationDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = context.getString(R.string.title_settings),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val merged = mergePadding(bottomPadding, padding)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(merged),
        ) {
            SmallTitle(text = context.getString(R.string.settings_category_rom))
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                ArrowPreference(
                    title = context.getString(R.string.settings_import_rom_title),
                    summary = context.getString(R.string.settings_import_rom_summary),
                    onClick = { picker.launch(arrayOf("application/x-tar")) },
                )
                ArrowPreference(
                    title = context.getString(R.string.settings_proot_command_title),
                    summary = context.getString(R.string.settings_proot_command_summary),
                    onClick = {
                        val profile = ProfileManager.currentProfile(context)
                        prootText = ProfileManager.getProotCommand(context, profile)
                            ?: ProfileManager.defaultProotCommand(context, profile)
                        showProotDialog = true
                    },
                )
            }

            SmallTitle(
                text = context.getString(R.string.settings_category_container),
                modifier = Modifier.padding(top = 6.dp),
            )
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                ArrowPreference(
                    title = context.getString(R.string.settings_shutdown_title),
                    summary = context.getString(R.string.settings_shutdown_summary),
                    onClick = onShutdown,
                )
            }

            SmallTitle(
                text = context.getString(R.string.settings_category_about),
                modifier = Modifier.padding(top = 6.dp),
            )
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                ArrowPreference(
                    title = context.getString(R.string.settings_about_title),
                    summary = context.getString(R.string.settings_about_summary),
                    onClick = { showAboutDialog = true },
                )
            }
        }
    }

    WindowDialog(
        show = showProotDialog,
        title = context.getString(R.string.proot_command_title),
        summary = context.getString(
            R.string.proot_command_message,
            ProfileManager.currentProfile(context),
        ),
        onDismissRequest = { showProotDialog = false },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = prootText,
                onValueChange = { prootText = it },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    text = context.getString(R.string.cancel),
                    onClick = { showProotDialog = false },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                TextButton(
                    text = context.getString(R.string.ok),
                    onClick = {
                        val profile = ProfileManager.currentProfile(context)
                        ProfileManager.setProotCommand(context, profile, prootText)
                        showProotDialog = false
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    WindowDialog(
        show = showDestinationDialog,
        title = context.getString(R.string.rom_installer_destination_title),
        onDismissRequest = { showDestinationDialog = false },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ProfileManager.listProfiles(context).forEach { profile ->
                ArrowPreference(
                    title = profile,
                    onClick = {
                        val uri = pendingUri
                        showDestinationDialog = false
                        if (uri != null) importRom(uri, profile)
                    },
                )
            }
        }
    }

    WindowDialog(
        show = importProgress,
        title = context.getString(R.string.rom_installer_extracting_title),
        summary = context.getString(R.string.rom_installer_extracting_msg),
        onDismissRequest = null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InfiniteProgressIndicator()
        }
    }

    WindowDialog(
        show = showAboutDialog,
        title = context.getString(R.string.about_title),
        onDismissRequest = { showAboutDialog = false },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = context.getString(R.string.app_name),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            BasicComponent(
                title = context.getString(R.string.about_ui_credit_title),
                summary = context.getString(R.string.about_ui_credit_summary),
            )
            BasicComponent(
                title = context.getString(R.string.about_terminal_credit_title),
                summary = context.getString(R.string.about_terminal_credit_summary),
            )
        }
    }
}
