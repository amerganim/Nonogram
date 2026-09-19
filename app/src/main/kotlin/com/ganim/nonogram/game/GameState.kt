package com.ganim.nonogram.game

import com.ganim.nonogram.puzzle.model.CellState
import com.ganim.nonogram.puzzle.model.Puzzle

/** What a tap or drag writes into a cell. Toggled by the mode button (build plan 5.2). */
enum class PaintMode { FILL, CROSS }

enum class GameStatus {
    PLAYING,

    /** Every filled cell of the solution is filled on the board. */
    COMPLETE,

    /** All lives spent. The player restarts, or watches an ad to restore one (5.3, 8.2). */
    OUT_OF_LIVES,
}

/** One cell changing value, recorded so undo can put it back. */
data class CellChange(val index: Int, val from: CellState, val to: CellState)

/**
 * One undoable step.
 *
 * A step is a whole *gesture*, not a cell: dragging across eight cells undoes in one
 * press, which is the only behaviour that feels right when a drag is the primary way to
 * paint.
 */
data class UndoEntry(val changes: List<CellChange>) {
    val isEmpty: Boolean get() = changes.isEmpty()
}

/**
 * Everything about a puzzle in progress.
 *
 * Immutable, so the ViewModel can hand it straight to Compose and so the whole of
 * [GameEngine] is a pure function of it. A 20x20 board is 400 entries, copied once per
 * painted cell - a few microseconds, nowhere near the frame budget.
 */
data class GameState(
    val puzzle: Puzzle,
    val board: List<CellState>,
    /** Cells the player filled wrongly. Permanently revealed, and never repainted. */
    val mistakeCells: Set<Int>,
    val livesRemaining: Int,
    val hintsUsed: Int,
    val elapsedMs: Long,
    val paintMode: PaintMode,
    val status: GameStatus,
    val undoStack: List<UndoEntry>,
    /** The gesture currently being painted, accumulating changes until it ends. */
    val openGesture: UndoEntry? = null,
) {
    val width: Int get() = puzzle.width
    val height: Int get() = puzzle.height

    val canUndo: Boolean get() = undoStack.isNotEmpty()

    val isPlayable: Boolean get() = status == GameStatus.PLAYING

    fun stateAt(row: Int, col: Int): CellState = board[row * width + col]

    fun isMistake(row: Int, col: Int): Boolean = (row * width + col) in mistakeCells

    /** True when [index] holds a filled cell in the real solution. */
    fun solutionFilled(index: Int): Boolean = puzzle.solution[index]

    /** Filled cells placed so far, out of the total the solution needs. */
    val filledCount: Int get() = board.count { it == CellState.FILLED }

    val targetFilledCount: Int get() = puzzle.solution.count { it }

    val progress: Float
        get() = if (targetFilledCount == 0) 1f else filledCount.toFloat() / targetFilledCount

    companion object {
        /** Lives per puzzle, per build plan 5.3. */
        const val MAX_LIVES = 3

        /** Build plan 5.2: "Undo stack, minimum 50 steps." */
        const val UNDO_LIMIT = 100

        fun newGame(puzzle: Puzzle): GameState = GameState(
            puzzle = puzzle,
            board = List(puzzle.cellCount) { CellState.UNKNOWN },
            mistakeCells = emptySet(),
            livesRemaining = MAX_LIVES,
            hintsUsed = 0,
            elapsedMs = 0L,
            paintMode = PaintMode.FILL,
            status = GameStatus.PLAYING,
            undoStack = emptyList(),
        )
    }
}
