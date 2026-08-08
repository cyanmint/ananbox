package io.github.miuzarte.scrcpyforandroid.widgets

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VideoOutputTarget {
    NONE,
    PREVIEW,
}

object VideoOutputTargetState {
    private val _current = MutableStateFlow(VideoOutputTarget.NONE)
    val current: StateFlow<VideoOutputTarget> = _current.asStateFlow()

    fun set(target: VideoOutputTarget) {
        _current.value = target
    }
}
