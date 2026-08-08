package com.github.ananbox.console

import android.util.Log
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.concurrent.thread

/**
 * Bridges a real OS shell process to the vendored termux terminal engine
 * (`com.termux.terminal` / `com.termux.view`, adapted from
 * https://github.com/Miuzarte/ScrcpyForAndroid) so the "Console" acts as a
 * genuine, working shell rather than a log viewer.
 */
class ConsoleSession(
    private val shellCommand: Array<String> = arrayOf("/system/bin/sh", "-i"),
    private val workingDirectory: String? = null,
) {
    private val TAG = "ConsoleSession"

    private var process: Process? = null
    private var outputStream: OutputStream? = null
    private var readerThread: Thread? = null

    var session: TerminalSession? = null
        private set

    fun start(client: TerminalSessionClient): TerminalSession {
        val newSession = TerminalSession(
            shellWriter = ::writeToShell,
            onScreenUpdated = {},
            onCopyTextToClipboardRequested = {},
            onPasteTextFromClipboardRequested = {},
            onBellRequested = {},
        )
        session = newSession

        try {
            val builder = ProcessBuilder(*shellCommand)
                .redirectErrorStream(true)
            workingDirectory?.let { builder.directory(java.io.File(it)) }
            builder.environment()["TERM"] = "xterm-256color"
            val proc = builder.start()
            process = proc
            outputStream = proc.outputStream

            readerThread = thread(name = "ConsoleSessionReader") {
                pumpProcessOutput(proc.inputStream, newSession)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start console shell", e)
        }

        return newSession
    }

    private fun pumpProcessOutput(input: InputStream, terminalSession: TerminalSession) {
        val buffer = ByteArray(4096)
        try {
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                terminalSession.append(buffer, read)
            }
        } catch (e: IOException) {
            Log.i(TAG, "Console shell stream closed: ${e.message}")
        }
    }

    private fun writeToShell(data: ByteArray, offset: Int, count: Int) {
        val out = outputStream ?: return
        try {
            out.write(data, offset, count)
            out.flush()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to write to console shell", e)
        }
    }

    fun stop() {
        readerThread?.interrupt()
        readerThread = null
        process?.destroy()
        process = null
        outputStream = null
        session = null
    }
}
