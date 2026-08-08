package com.github.ananbox.ui.console

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.github.ananbox.console.ConsoleSession
import com.github.ananbox.console.ConsoleSessionClient
import com.github.ananbox.console.ConsoleViewClient
import com.termux.view.TerminalView

/**
 * The "Console" screen: an embedded terminal emulator backed by the real
 * termux terminal engine (`com.termux.terminal` / `com.termux.view`), wired
 * to a live shell process. UI adapted from
 * https://github.com/Miuzarte/ScrcpyForAndroid (Apache-2.0).
 */
@Composable
fun ConsoleScreen(modifier: Modifier = Modifier) {
    var invalidateTick by remember { mutableStateOf(0) }

    AndroidView(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        factory = { context ->
            val consoleSession = ConsoleSession()
            val sessionClient = ConsoleSessionClient(
                onInvalidate = { invalidateTick++ },
            )
            val terminalSession = consoleSession.start(sessionClient)

            TerminalView(context, null).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setTerminalViewClient(ConsoleViewClient(sessionClient))
                attachSession(terminalSession)
                setTextSize(28)
                tag = consoleSession
            }
        },
        onRelease = { view ->
            (view.tag as? ConsoleSession)?.stop()
        },
        update = { view ->
            view.onScreenUpdated()
        },
    )

    DisposableEffect(invalidateTick) {
        onDispose { }
    }
}
