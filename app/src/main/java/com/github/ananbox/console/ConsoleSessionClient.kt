package com.github.ananbox.console

import android.util.Log
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient

/**
 * Minimal [TerminalSessionClient] implementation wiring the termux terminal
 * engine callbacks to Android `Log` / a screen-invalidation callback, adapted
 * from https://github.com/Miuzarte/ScrcpyForAndroid (Apache-2.0).
 */
class ConsoleSessionClient(
    private val onInvalidate: () -> Unit = {},
    private val onFinished: () -> Unit = {},
) : TerminalSessionClient {

    override fun onTextChanged(changedSession: TerminalSession) = onInvalidate()

    override fun onTitleChanged(changedSession: TerminalSession) = Unit

    override fun onSessionFinished(finishedSession: TerminalSession) = onFinished()

    override fun onCopyTextToClipboard(session: TerminalSession, text: String?) = Unit

    override fun onPasteTextFromClipboard(session: TerminalSession?) = Unit

    override fun onBell(session: TerminalSession) = Unit

    override fun onColorsChanged(session: TerminalSession) = onInvalidate()

    override fun onTerminalCursorStateChange(state: Boolean) = Unit

    override fun setTerminalShellPid(session: TerminalSession, pid: Int) = Unit

    override fun getTerminalCursorStyle(): Int? = null

    override fun logError(tag: String?, message: String?) {
        Log.e(tag ?: "Console", message ?: "")
    }

    override fun logWarn(tag: String?, message: String?) {
        Log.w(tag ?: "Console", message ?: "")
    }

    override fun logInfo(tag: String?, message: String?) {
        Log.i(tag ?: "Console", message ?: "")
    }

    override fun logDebug(tag: String?, message: String?) {
        Log.d(tag ?: "Console", message ?: "")
    }

    override fun logVerbose(tag: String?, message: String?) {
        Log.v(tag ?: "Console", message ?: "")
    }

    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
        Log.e(tag ?: "Console", message ?: "", e)
    }

    override fun logStackTrace(tag: String?, e: Exception?) {
        Log.e(tag ?: "Console", "", e)
    }
}
