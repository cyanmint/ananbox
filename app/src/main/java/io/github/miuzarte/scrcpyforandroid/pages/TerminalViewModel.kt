package io.github.miuzarte.scrcpyforandroid.pages

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.termux.terminal.KeyHandler
import com.termux.terminal.TerminalSession
import com.termux.terminal.TextStyle
import com.cyanmint.anbox.R
import com.cyanmint.anbox.terminal.PtySession
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import io.github.miuzarte.scrcpyforandroid.services.LocalInputService
import io.github.miuzarte.scrcpyforandroid.storage.BundleSyncDelegate
import io.github.miuzarte.scrcpyforandroid.storage.Storage.appSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.yukonga.miuix.kmp.basic.SnackbarResult
import java.nio.charset.StandardCharsets
import kotlin.math.roundToInt

private const val DEFAULT_TERMINAL_FONT_SIZE_SP = 14f
private const val LOG_TAG = "TerminalScreen"

internal class TerminalViewModel: ViewModel() {

    private val asBundleSync = BundleSyncDelegate(
        sharedFlow = appSettings.bundleState,
        save = { appSettings.saveBundle(it) },
        scope = viewModelScope,
    )
    val asBundle = asBundleSync.value

    private val _terminalFontSizeSp = MutableStateFlow(asBundle.value.terminalFontSizeSp)
    val terminalFontSizeSp: StateFlow<Float> = _terminalFontSizeSp.asStateFlow()

    fun updateTerminalFontSize(newValue: Float, onApplied: (Float) -> Unit): Float {
        val clamped = newValue.coerceIn(1f, 32f)
        if (clamped == _terminalFontSizeSp.value) return clamped
        _terminalFontSizeSp.value = clamped
        asBundleSync.update { it.copy(terminalFontSizeSp = clamped) }
        onApplied(clamped)
        return clamped
    }

    fun adjustTerminalFontSize(delta: Float, onApplied: (Float) -> Unit): Float {
        return updateTerminalFontSize(_terminalFontSizeSp.value + delta, onApplied)
    }

    fun resetFontSizeToDefault(onApplied: (Float) -> Unit) {
        updateTerminalFontSize(DEFAULT_TERMINAL_FONT_SIZE_SP, onApplied)
    }

    private val _shellReady = MutableStateFlow(false)
    val shellReady: StateFlow<Boolean> = _shellReady.asStateFlow()

    private val _shellConnecting = MutableStateFlow(false)
    val shellConnecting: StateFlow<Boolean> = _shellConnecting.asStateFlow()

    private var shellProcess: PtySession? = null
    private var shellWriterJob: Job? = null
    private var shellWriteChannel = Channel<ByteArray>(Channel.UNLIMITED)

    val sessionHolder = arrayOfNulls<TerminalSession>(1)

    private fun startShellWriter() {
        shellWriteChannel = Channel(Channel.UNLIMITED)
        shellWriterJob = viewModelScope.launch(Dispatchers.IO) {
            for (payload in shellWriteChannel) {
                val session = shellProcess ?: break
                if (!session.isAlive) break
                val result = runCatching {
                    session.outputStream.write(payload)
                    session.outputStream.flush()
                }
                if (result.isFailure) {
                    withContext(Dispatchers.Main) {
                        result.exceptionOrNull()?.let { error ->
                            AppRuntime.snackbar(
                                R.string.terminal_snack_input_failed,
                                error.message ?: error.javaClass.simpleName,
                            )
                        }
                    }
                    break
                }
            }
        }
    }

    fun writeBytesToShell(data: ByteArray, offset: Int, count: Int) {
        if (!_shellReady.value) return
        val payload = data.copyOfRange(offset, offset + count)
        shellWriteChannel.trySend(payload)
    }

    fun writeClipboardToShell(context: Context) {
        val text = LocalInputService.getClipboardText(context)
        if (!text.isNullOrBlank()) {
            val bytes = text.toByteArray(StandardCharsets.UTF_8)
            writeBytesToShell(bytes, 0, bytes.size)
        }
    }

    var ctrlLatched by mutableStateOf(false)
    var altLatched by mutableStateOf(false)
    var pendingLatchedConsume by mutableStateOf(false)

    fun consumeLatchedVisualState() {
        ctrlLatched = false
        altLatched = false
        pendingLatchedConsume = false
    }

    fun writeLiteralKey(text: String) {
        var payload = text
        if (ctrlLatched) {
            payload = payload.map { applyCtrlModifier(it) }.joinToString(separator = "")
        }
        if (altLatched) {
            payload = "$payload"
        }
        ctrlLatched = false
        altLatched = false
        pendingLatchedConsume = false
        val bytes = payload.toByteArray(StandardCharsets.UTF_8)
        writeBytesToShell(bytes, 0, bytes.size)
    }

    fun writeSpecialKey(keyCode: Int) {
        val session = sessionHolder[0] ?: return
        var modifiers = 0
        if (ctrlLatched) modifiers = modifiers or KeyHandler.KEYMOD_CTRL
        if (altLatched) modifiers = modifiers or KeyHandler.KEYMOD_ALT
        ctrlLatched = false
        altLatched = false
        pendingLatchedConsume = false
        val sequence = KeyHandler.getCode(
            keyCode, modifiers,
            session.emulator.isCursorKeysApplicationMode(),
            session.emulator.isKeypadApplicationMode(),
        ) ?: return
        val bytes = sequence.toByteArray(StandardCharsets.UTF_8)
        writeBytesToShell(bytes, 0, bytes.size)
    }

    fun extractTranscript(session: TerminalSession): String {
        val screen = session.emulator.getScreen()
        return screen.getSelectedText(
            selX1 = 0, selY1 = -screen.activeTranscriptRows,
            selX2 = session.emulator.mColumns, selY2 = session.emulator.mRows - 1,
            joinBackLines = false, joinFullLines = false,
        ).trim('\n')
    }

    fun syncOutput(onOutputChange: (String) -> Unit) {
        val session = sessionHolder[0] ?: return
        onOutputChange(extractTranscript(session))
    }

    fun applyTerminalThemeColors(surfaceArgb: Int, onSurfaceArgb: Int, cursorArgb: Int) {
        val session = sessionHolder[0] ?: return
        val colors = session.emulator.mColors.mCurrentColors
        colors[TextStyle.COLOR_INDEX_BACKGROUND] = surfaceArgb
        colors[TextStyle.COLOR_INDEX_FOREGROUND] = onSurfaceArgb
        colors[TextStyle.COLOR_INDEX_CURSOR] = cursorArgb
    }

    /**
     * Launches a plain "sh" shell (no adb involved) with its working directory
     * set to this app's own internal storage, i.e.
     * /data/user/&lt;userId&gt;/com.cyanmint.anbox/files, attached to a real
     * pty so job control and local echo behave like a normal terminal.
     */
    fun openShellSession(showKeyboardAfterConnect: Boolean, requestFocus: () -> Unit) {
        if (shellProcess != null || _shellConnecting.value) {
            if (_shellReady.value && showKeyboardAfterConnect) requestFocus()
            return
        }
        _shellConnecting.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val appDir = AppRuntime.context.filesDir
            val sessionResult = runCatching {
                PtySession.start(cmd = "/system/bin/sh", cwd = appDir)
            }
            val session = sessionResult.getOrElse { error ->
                withContext(Dispatchers.Main) {
                    _shellConnecting.value = false
                    _shellReady.value = false
                    AppRuntime.snackbar(
                        R.string.terminal_snack_session_failed,
                        error.message ?: error.javaClass.simpleName,
                    )
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                shellProcess = session
                _shellReady.value = true
                _shellConnecting.value = false
                startShellWriter()
                if (showKeyboardAfterConnect) requestFocus()
            }

            val buffer = ByteArray(4096)
            try {
                while (session.isAlive) {
                    val count = session.inputStream.read(buffer)
                    if (count <= 0) break
                    withContext(Dispatchers.Main) {
                        sessionHolder[0]?.append(buffer, count)
                    }
                }
            } catch (error: Throwable) {
                withContext(Dispatchers.Main) {
                    AppRuntime.snackbar(
                        R.string.terminal_snack_output_failed,
                        error.message ?: error.javaClass.simpleName,
                    )
                }
            } finally {
                runCatching { session.destroy() }
                withContext(Dispatchers.Main) {
                    shellWriterJob?.cancel()
                    shellWriterJob = null
                    if (shellProcess === session) shellProcess = null
                    _shellReady.value = false
                    _shellConnecting.value = false
                }
            }
        }
    }

    /** Propagates a terminal resize to the pty so the shell's line discipline stays in sync. */
    fun resizePtyWindow(rows: Int, columns: Int) {
        shellProcess?.updateSize(rows, columns)
    }

    fun autoConnectIfNeeded(onFocus: () -> Unit) {
        if (_shellReady.value || _shellConnecting.value) return
        openShellSession(false, onFocus)
    }

    fun closeShell() {
        shellWriterJob?.cancel()
        shellWriterJob = null
        runCatching { shellProcess?.destroy() }
        shellProcess = null
        _shellReady.value = false
        _shellConnecting.value = false
    }

    fun launchFontSizeSnackbar(
        fontSizeSp: Float,
        onReset: (Float) -> Unit,
    ) {
        AppRuntime.snackbar(
            R.string.terminal_font_size_snackbar,
            fontSizeSp.roundToInt(),
            actionLabelResId = R.string.terminal_font_size_restore_default,
            withDismissAction = true,
            onResult = { result ->
                if (result == SnackbarResult.ActionPerformed)
                    resetFontSizeToDefault(onReset)
            },
            dismissNewest = true,
        )
    }

    init {
        asBundleSync.start()
    }

    override fun onCleared() {
        closeShell()
        runBlocking(Dispatchers.IO) { asBundleSync.flush() }
    }

    companion object {
        private fun applyCtrlModifier(ch: Char): Char = when (ch) {
            in 'a'..'z' -> (ch.code - 'a'.code + 1).toChar()
            in 'A'..'Z' -> (ch.code - 'A'.code + 1).toChar()
            ' ' -> 0.toChar()
            '[' -> 27.toChar()
            '\\' -> 28.toChar()
            ']' -> 29.toChar()
            '^' -> 30.toChar()
            '_', '/' -> 31.toChar()
            else -> ch
        }
    }
}
