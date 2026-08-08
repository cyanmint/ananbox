package com.github.ananbox.console

import android.view.KeyEvent
import android.view.MotionEvent
import com.termux.terminal.TerminalSession

/**
 * Minimal [TerminalViewClient] for the embedded Console screen, adapted from
 * https://github.com/Miuzarte/ScrcpyForAndroid (Apache-2.0).
 */
class ConsoleViewClient(
    private val logClient: ConsoleSessionClient,
) : com.termux.view.TerminalViewClient {

    override fun onScale(scale: Float): Float = scale

    override fun onSingleTapUp(e: MotionEvent) = Unit

    override fun shouldBackButtonBeMappedToEscape(): Boolean = false

    override fun shouldEnforceCharBasedInput(): Boolean = true

    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false

    override fun isTerminalViewSelected(): Boolean = true

    override fun copyModeChanged(copyMode: Boolean) = Unit

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean = false

    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false

    override fun onLongPress(event: MotionEvent): Boolean = false

    override fun readControlKey(): Boolean = false

    override fun readAltKey(): Boolean = false

    override fun readShiftKey(): Boolean = false

    override fun readFnKey(): Boolean = false

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false

    override fun onEmulatorSet() = Unit

    override fun logError(tag: String?, message: String?) = logClient.logError(tag, message)

    override fun logWarn(tag: String?, message: String?) = logClient.logWarn(tag, message)

    override fun logInfo(tag: String?, message: String?) = logClient.logInfo(tag, message)

    override fun logDebug(tag: String?, message: String?) = logClient.logDebug(tag, message)

    override fun logVerbose(tag: String?, message: String?) = logClient.logVerbose(tag, message)

    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) =
        logClient.logStackTraceWithMessage(tag, message, e)

    override fun logStackTrace(tag: String?, e: Exception?) = logClient.logStackTrace(tag, e)
}
