package com.lightchat.ui.chat

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged

/**
 * Leaves an unzoomed single-finger horizontal drag to the parent pager.
 * The image only consumes gestures for pinch zoom or panning after zoom-in.
 */
fun Modifier.zoomPanGesture(
    currentScale: () -> Float,
    onGesture: (pan: Offset, zoom: Float) -> Unit
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent(PointerEventPass.Main)
            val pressedCount = event.changes.count { it.pressed }
            if (pressedCount >= 2 || currentScale() > 1f) {
                val zoom = event.calculateZoom()
                val pan = event.calculatePan()
                if (zoom != 1f || pan != Offset.Zero) {
                    onGesture(pan, zoom)
                    event.changes.forEach { change ->
                        if (change.positionChanged()) change.consume()
                    }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}
