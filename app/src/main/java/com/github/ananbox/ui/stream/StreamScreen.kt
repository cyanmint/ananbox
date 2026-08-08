package com.github.ananbox.ui.stream

import android.graphics.Rect
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.viewinterop.AndroidView
import com.github.ananbox.ui.constants.UiSpacing
import kotlin.math.roundToInt

@Composable
fun StreamScreen(
    modifier: Modifier = Modifier,
    chromeVisible: Boolean = true,
    onVideoBoundsInWindowChanged: (Rect?) -> Unit = {},
    toolbar: (@Composable () -> Unit)? = null,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    onVideoBoundsInWindowChanged(coordinates.boundsInWindow().toAndroidRect())
                },
            content = content,
        )

        overlay?.invoke(this)

        if (toolbar != null) {
            AnimatedVisibility(
                visible = chromeVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(UiSpacing.Large),
            ) {
                toolbar()
            }
        }
    }
}

@Composable
fun StreamAndroidView(
    viewProvider: () -> View,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { viewProvider() },
        modifier = modifier.fillMaxSize(),
    )
}

private fun ComposeRect.toAndroidRect(): Rect =
    Rect(
        left.roundToInt(),
        top.roundToInt(),
        right.roundToInt(),
        bottom.roundToInt(),
    )
