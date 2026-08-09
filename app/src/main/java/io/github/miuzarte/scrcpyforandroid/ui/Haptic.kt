package io.github.miuzarte.scrcpyforandroid.ui

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

fun HapticFeedback.confirm() =
    performHapticFeedback(HapticFeedbackType.Confirm)

fun HapticFeedback.contextClick() =
    performHapticFeedback(HapticFeedbackType.ContextClick)

fun HapticFeedback.gestureEnd() =
    performHapticFeedback(HapticFeedbackType.GestureEnd)

fun HapticFeedback.gestureThresholdActivate() =
    performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)

fun HapticFeedback.keyboardTap() =
    performHapticFeedback(HapticFeedbackType.KeyboardTap)

fun HapticFeedback.longPress() =
    performHapticFeedback(HapticFeedbackType.LongPress)

fun HapticFeedback.reject() =
    performHapticFeedback(HapticFeedbackType.Reject)

fun HapticFeedback.segmentFrequentTick() =
    performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)

fun HapticFeedback.segmentTick() =
    performHapticFeedback(HapticFeedbackType.SegmentTick)

fun HapticFeedback.textHandleMove() =
    performHapticFeedback(HapticFeedbackType.TextHandleMove)

fun HapticFeedback.toggleOff() =
    performHapticFeedback(HapticFeedbackType.ToggleOff)

fun HapticFeedback.toggleOn() =
    performHapticFeedback(HapticFeedbackType.ToggleOn)

fun HapticFeedback.virtualKey() =
    performHapticFeedback(HapticFeedbackType.VirtualKey)
