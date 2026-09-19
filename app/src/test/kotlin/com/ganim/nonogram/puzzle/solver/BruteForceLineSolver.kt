package com.ganim.nonogram.puzzle.solver

import com.ganim.nonogram.puzzle.model.CellState
import com.ganim.nonogram.puzzle.model.Clue

/**
 * The literal reading of build plan 4.2: enumerate every valid placement, then keep the
 * cells that agree across all of them.
 *
 * Exponential and test-only. It exists so the production [LineSolver] dynamic program
 * has something to be checked against that was arrived at a completely different way.
 * It reuses [BacktrackingVerifier.placements], which shares no code with [LineSolver].
 */
object BruteForceLineSolver {

    private val verifier = BacktrackingVerifier()

    /** Returns the forced deductions, or null if no placement fits [current]. */
    fun solveOrNull(clue: Clue, current: List<CellState>): List<CellState>? {
        val n = current.size
        val valid = validPlacements(clue, current)
        if (valid.isEmpty()) return null

        val fullMask = if (n == 32) -1 else (1 shl n) - 1
        var alwaysFilled = fullMask
        var alwaysEmpty = fullMask
        for (mask in valid) {
            alwaysFilled = alwaysFilled and mask
            alwaysEmpty = alwaysEmpty and mask.inv()
        }

        return List(n) { i ->
            when {
                (alwaysFilled ushr i) and 1 == 1 -> CellState.FILLED
                (alwaysEmpty ushr i) and 1 == 1 ->
                    if (current[i] == CellState.CROSSED) CellState.CROSSED else CellState.EMPTY
                else -> current[i]
            }
        }
    }

    /** Every placement of [clue] that is consistent with the cells already known in [current]. */
    fun validPlacements(clue: Clue, current: List<CellState>): List<Int> {
        val n = current.size
        return verifier.placements(clue, n).filter { mask ->
            (0 until n).all { i ->
                val filled = (mask ushr i) and 1 == 1
                when {
                    current[i] == CellState.FILLED -> filled
                    current[i].isKnownEmpty -> !filled
                    else -> true
                }
            }
        }
    }
}
