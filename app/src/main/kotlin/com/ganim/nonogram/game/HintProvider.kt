package com.ganim.nonogram.game

import com.ganim.nonogram.puzzle.model.CellState
import com.ganim.nonogram.puzzle.solver.PuzzleSolver
import com.ganim.nonogram.puzzle.solver.SolveResult

/**
 * Picks the cell a hint should reveal (build plan 5.4).
 *
 * > "A hint reveals one correct cell - specifically, pick a cell that is *logically
 * > deducible right now* from the current board state (run the solver against the
 * > player's current state and choose from the newly forced cells). A hint that reveals
 * > a random cell is much less useful and feels arbitrary."
 *
 * So this runs [PuzzleSolver] over the player's own board and returns something the
 * player could have worked out themselves. The hint teaches instead of just filling in
 * a square.
 *
 * ## Handling a player's wrong crosses
 *
 * Filled cells on the board are always correct - a wrong fill is a mistake and gets
 * turned into a cross instead. Crosses are a different matter: they are notes, and
 * players do cross the wrong cell. A wrong cross makes the clue set unsatisfiable, and
 * the solver would report a contradiction rather than a hint.
 *
 * So there are two passes. First trust the crosses; if that contradicts or yields
 * nothing, discard them and solve from the confirmed fills alone. That second pass
 * always works, because the puzzle is solvable from a blank board by construction.
 */
class HintProvider(private val solver: PuzzleSolver = PuzzleSolver()) {

    /**
     * The cell index to reveal, or null if there is nothing left to deduce.
     *
     * Returns null once the puzzle is finished or the lives are gone: there is nothing
     * to hint at, and offering one would let the hint button spend a rewarded ad (8.2)
     * on a board the player can no longer touch.
     */
    fun nextHint(state: GameState): Int? {
        if (!state.isPlayable) return null
        deduce(state, trustCrosses = true)?.let { return it }
        return deduce(state, trustCrosses = false)
    }

    private fun deduce(state: GameState, trustCrosses: Boolean): Int? {
        val puzzle = state.puzzle
        val seed = Array(puzzle.cellCount) { i ->
            when (state.board[i]) {
                CellState.FILLED -> CellState.FILLED
                CellState.CROSSED -> if (trustCrosses) CellState.CROSSED else CellState.UNKNOWN
                else -> CellState.UNKNOWN
            }
        }

        val run = solver.run(puzzle.width, puzzle.height, puzzle.rowClues, puzzle.colClues, seed)
        if (run.result is SolveResult.Contradiction) return null

        return pickBest(state, run.board)
    }

    /**
     * Chooses among the newly forced cells.
     *
     * Filled cells are preferred: revealing a square that should be filled moves the
     * picture along and feels like progress, while revealing an empty one mostly just
     * narrows the search. Within that preference the choice is by reading order, so the
     * same board always yields the same hint - a hint that moved around between taps
     * would feel random, which is the exact failure the plan warns about.
     */
    private fun pickBest(state: GameState, solved: Array<CellState>): Int? {
        var firstEmpty: Int? = null
        for (i in solved.indices) {
            if (state.board[i] != CellState.UNKNOWN) continue
            when {
                solved[i] == CellState.FILLED -> return i
                solved[i].isKnownEmpty -> if (firstEmpty == null) firstEmpty = i
            }
        }
        return firstEmpty
    }
}
