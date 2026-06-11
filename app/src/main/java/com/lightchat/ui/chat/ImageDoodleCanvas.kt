package com.lightchat.ui.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput

data class DoodlePath(
    val path: Path,
    val color: Color,
    val strokeWidth: Float
)

@Composable
fun ImageDoodleCanvas(
    modifier: Modifier = Modifier,
    currentColor: Color,
    currentStrokeWidth: Float,
    isDrawingEnabled: Boolean,
    paths: List<DoodlePath>,
    onPathAdded: (DoodlePath) -> Unit
) {
    var currentPath by remember { mutableStateOf<Path?>(null) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(isDrawingEnabled) {
                if (!isDrawingEnabled) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val position = event.changes.firstOrNull()?.position ?: continue

                        if (event.changes.firstOrNull()?.pressed == true) {
                            val path = Path().apply { moveTo(position.x, position.y) }
                            currentPath = path
                            do {
                                val nextEvent = awaitPointerEvent()
                                val nextPos = nextEvent.changes.firstOrNull()?.position ?: break
                                path.lineTo(nextPos.x, nextPos.y)
                            } while (nextEvent.changes.firstOrNull()?.pressed == true)

                            currentPath?.let {
                                onPathAdded(DoodlePath(it, currentColor, currentStrokeWidth))
                            }
                            currentPath = null
                        }
                    }
                }
            }
    ) {
        paths.forEach { doodle ->
            drawPath(
                path = doodle.path,
                color = doodle.color,
                style = Stroke(
                    width = doodle.strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
        currentPath?.let {
            drawPath(
                path = it,
                color = currentColor,
                style = Stroke(
                    width = currentStrokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}
