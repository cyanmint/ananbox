package com.cyanmint.anbox

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import com.cyanmint.anbox.databinding.ActivityConsoleBinding
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalViewClient
import java.io.IOException
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

/**
 * Launches a plain "sh" shell (no proot/container involved) with its working
 * directory set to this app's own internal storage
 * (/data/user/<userId>/com.cyanmint.anbox/files), and shows it using a
 * terminal view adapted from ScrcpyForAndroid (see /NOTICE.md).
 */
class ConsoleActivity : AppCompatActivity(), TerminalViewClient {

    private lateinit var binding: ActivityConsoleBinding
    private var process: Process? = null
    private var session: TerminalSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConsoleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.title_activity_console)

        startShell()
    }

    private fun startShell() {
        val appDir = filesDir
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
                runOnUiThread { binding.terminalView.onScreenUpdated() }
            },
            onCopyTextToClipboardRequested = { },
            onPasteTextFromClipboardRequested = { },
            onBellRequested = { },
        )
        session = newSession
        binding.terminalView.attachSession(newSession)
        binding.terminalView.setTerminalViewClient(this)

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
                val msg = ("Failed to start shell: " + e.message + "\r\n").toByteArray(StandardCharsets.UTF_8)
                newSession.append(msg, msg.size)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        process?.destroy()
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

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
