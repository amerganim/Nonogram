package com.ganim.nonogram.game

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ganim.nonogram.puzzle.model.CellState

/**
 * Makes the board readable by TalkBack (build plan 9).
 *
 * > "Accessibility pass: TalkBack labels on all controls; the grid should announce cell
 * > coordinates and state."
 *
 * The board is one `Canvas`, so to a screen reader it is a single blank rectangle. The
 * only way to expose cells is to put a real semantics node over each one.
 *
 * ## Why this is conditional
 *
 * Four hundred semantics nodes over the board would undo the entire reason the grid is
 * drawn on a canvas in the first place. So the overlay is built **only when touch
 * exploration is actually on**. With TalkBack off - which is almost always - this
 * composes nothing and costs nothing. With it on, the extra nodes are irrelevant,
 * because a screen-reader user is tapping cells one at a time rather than drag-painting
 * at sixty frames a second.
 *
 * Each node announces its coordinates and state, and activating it paints the cell in
 * the current mode, so the game is playable without sight of the screen.
 */
@Composable
fun BoardAccessibilityOverlay(
    boardState: State<GameState>,
    metrics: BoardMetrics,
    onCellActivated: (CellRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!rememberTouchExplorationEnabled()) return

    val state = boardState.value
    val density = LocalDensity.current
    if (metrics.cellSize <= 0f) return

    val cellDp = with(density) { metrics.cellSize.toDp() }

    Box(modifier) {
        for (row in 0 until metrics.rows) {
            for (col in 0 until metrics.columns) {
                val index = row * metrics.columns + col
                val cell = state.board[index]
                val isMistake = index in state.mistakeCells

                Box(
                    Modifier
                        .offset(
                            x = with(density) { metrics.cellLeft(col).toDp() },
                            y = with(density) { metrics.cellTop(row).toDp() },
                        )
                        .size(cellDp)
                        .semantics {
                            contentDescription = describeCell(row, col, cell, isMistake)
                            onClick(label = "Paint") {
                                onCellActivated(CellRef(row, col))
                                true
                            }
                        },
                )
            }
        }
    }
}

/**
 * Coordinates first, then state.
 *
 * One-based, because "row 1" is what a person counting squares says; the internal
 * zero-based index is an implementation detail and announcing it would be confusing.
 */
internal fun describeCell(row: Int, col: Int, state: CellState, isMistake: Boolean): String {
    val what = when {
        isMistake -> "wrong, crossed"
        state == CellState.FILLED -> "filled"
        state.isKnownEmpty -> "crossed"
        else -> "empty"
    }
    return "Row ${row + 1}, column ${col + 1}, $what"
}

/**
 * Whether a screen reader is exploring by touch.
 *
 * Tracked live rather than read once, so turning TalkBack on does not require restarting
 * the app to get an accessible board.
 */
@Composable
private fun rememberTouchExplorationEnabled(): Boolean {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(context.isTouchExplorationEnabled()) }

    DisposableEffect(context) {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { active ->
            enabled = active
        }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }

    return enabled
}

private fun Context.isTouchExplorationEnabled(): Boolean {
    val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    return manager?.isTouchExplorationEnabled == true
}
