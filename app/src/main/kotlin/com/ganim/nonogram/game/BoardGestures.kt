package com.ganim.nonogram.game

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull

/** How the opening moments of a touch resolved. */
private enum class Opening { LIFTED, MOVED, SECOND_POINTER }

/**
 * Touch handling for the board (build plan 5.2).
 *
 * One gesture recogniser rather than several stacked modifiers, because tap, drag-paint,
 * long-press and pinch all start from the same finger-down and have to agree on which
 * one is happening. Layered `detectTapGestures` / `detectTransformGestures` modifiers
 * fight over event consumption and produce exactly the flickering the plan warns about.
 *
 * How a touch resolves:
 *
 *  - **Lifted without moving** - a tap, cycling that one cell.
 *  - **Moved past touch slop** - a drag. The first cell locks what the whole drag
 *    writes, and [DragTracker] snaps it to a row or column.
 *  - **Held still past the long-press timeout** - a drag in the *opposite* mode, so the
 *    player can cross a few cells without leaving fill mode.
 *  - **Second finger down** - pinch to zoom and pan. Any drag in progress is closed
 *    first, so a pinch never leaves a half-painted line.
 *  - **Started in a gutter** - a one-finger pan, which is the natural thing to do with
 *    the margin of a zoomed board.
 *
 * [metrics] is a lambda rather than a value so zooming and panning do not restart the
 * pointer handler mid-gesture.
 */
fun Modifier.boardInput(
    metrics: () -> BoardMetrics,
    enabled: Boolean,
    restartKey: Any?,
    onTap: (CellRef) -> Unit,
    onLongPress: () -> Unit,
    onDragStart: (CellRef, Boolean) -> Unit,
    onDragTo: (CellRef, Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onHighlight: (CellRef?) -> Unit,
    onTransform: (Float, Offset) -> Unit,
): Modifier = pointerInput(restartKey, enabled) {
    if (!enabled) return@pointerInput

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val startCell = metrics().cellAt(down.position.x, down.position.y)
        onHighlight(startCell)

        var dragging = false
        var transforming = false

        // Wait out the long-press window, watching for whichever happens first.
        val opening = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            var result: Opening
            while (true) {
                val event = awaitPointerEvent()
                if (event.changes.count { it.pressed } >= 2) {
                    result = Opening.SECOND_POINTER
                    break
                }
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed) {
                    result = Opening.LIFTED
                    break
                }
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                    result = Opening.MOVED
                    break
                }
            }
            result
        }

        when {
            // Timed out with the finger still down and still: a long press.
            opening == null -> {
                if (startCell != null) {
                    onLongPress()
                    onDragStart(startCell, true)
                    dragging = true
                } else {
                    transforming = true
                }
            }

            opening == Opening.LIFTED -> {
                if (startCell != null) onTap(startCell)
                onHighlight(null)
                return@awaitEachGesture
            }

            opening == Opening.MOVED -> {
                if (startCell != null) {
                    onDragStart(startCell, false)
                    dragging = true
                } else {
                    // Started in a gutter, so this is a pan rather than a paint.
                    transforming = true
                }
            }

            else -> transforming = true
        }

        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) break

            if (pressed.size >= 2) {
                if (dragging) {
                    onDragEnd()
                    dragging = false
                }
                transforming = true
                onTransform(event.calculateZoom(), event.calculatePan())
                event.changes.forEach { it.consume() }
                continue
            }

            if (transforming) {
                // One finger left after a pinch. Pan with it, but do not start painting
                // mid-gesture - the player is still positioning the board.
                onTransform(1f, event.calculatePan())
                pressed.forEach { it.consume() }
                continue
            }

            if (dragging) {
                val change = pressed.first()
                val cell = metrics().nearestCell(change.position.x, change.position.y)
                val delta = change.position - down.position
                onDragTo(cell, delta.x, delta.y)
                change.consume()
            }
        }

        if (dragging) onDragEnd()
        onHighlight(null)
    }
}
