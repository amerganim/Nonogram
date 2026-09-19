package com.ganim.nonogram.puzzle.solver

import com.ganim.nonogram.puzzle.model.CellState

/**
 * Outcome of running [PuzzleSolver] over a clue set (build plan 4.3).
 *
 * Only [Unique] is acceptable for a shipped puzzle. A puzzle that stalls is one the
 * player could only finish by guessing, and the plan calls rejecting those "the single
 * most important quality rule in the app".
 */
sealed class SolveResult {

    /**
     * Solved by forced deductions alone, in [depth] full row+column passes.
     *
     * Reaching this state also proves the solution is unique: every cell was forced, so
     * no other grid satisfies the clues.
     */
    data class Unique(val depth: Int) : SolveResult()

    /** Logic stalled with cells still unknown. More than one grid satisfies the clues. */
    data object Ambiguous : SolveResult()

    /** The clues contradict each other. No grid satisfies them. */
    data object Contradiction : SolveResult()
}

/**
 * A solver run: the classification, the board it ended on, and how the work went.
 *
 * The board matters beyond classification - the hint system (5.4) runs the solver
 * against the player's current board and picks from the cells that came back newly
 * forced, which is what makes a hint feel earned rather than arbitrary.
 */
class SolveRun(
    val result: SolveResult,
    val board: Array<CellState>,
    val width: Int,
    val height: Int,
    val stats: SolveStats,
) {
    fun stateAt(row: Int, col: Int): CellState = board[row * width + col]

    val isSolved: Boolean get() = result is SolveResult.Unique
}

/**
 * How a solve progressed, pass by pass.
 *
 * The build plan makes `solveDepth` the difficulty signal. Measurement says it is a
 * weak one on its own: across 1,500 logic-solvable grids per size, depth at 20x20 ran
 * 2-9 with 68% of puzzles at depth 3 or 4, so most of the catalogue collapses onto two
 * adjacent values and the plan's "EXPERT = depth 11+" band is empty.
 *
 * [cellsPerPass] gives the rater a second axis. A puzzle that resolves most of the grid
 * in its first pass and then tidies up is easy however many passes it takes; one that
 * grinds out a handful of cells per pass is hard. That grind is what [slowPasses]
 * counts, and it separates puzzles that depth alone cannot tell apart.
 */
class SolveStats(
    cellsPerPass: List<Int>,
    val cellCount: Int,
) {
    /**
     * Cells newly determined by each full row+column pass, in order.
     *
     * Copied on construction: the solver builds this up in a mutable list it keeps
     * appending to, and stats handed out mid-solve must not change underneath the caller.
     */
    val cellsPerPass: List<Int> = cellsPerPass.toList()

    /** Passes that resolved at most [SLOW_PASS_FRACTION] of the grid - the grinding ones. */
    val slowPasses: Int
        get() {
            val threshold = (cellCount * SLOW_PASS_FRACTION).coerceAtLeast(1.0)
            return cellsPerPass.count { it > 0 && it <= threshold }
        }

    /** Share of the grid resolved by the opening pass. High means the puzzle opens up easily. */
    val openingYield: Double
        get() = if (cellCount == 0 || cellsPerPass.isEmpty()) 0.0 else cellsPerPass.first().toDouble() / cellCount

    override fun toString(): String =
        "SolveStats(passes=${cellsPerPass.size}, slow=$slowPasses, opening=${"%.2f".format(openingYield)})"

    companion object {
        const val SLOW_PASS_FRACTION = 0.05

        val EMPTY = SolveStats(emptyList(), 0)
    }
}
