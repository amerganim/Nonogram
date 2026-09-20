package com.ganim.nonogram.game

import com.ganim.nonogram.puzzle.model.CellState
import com.ganim.nonogram.puzzle.model.Clue

/**
 * Works out which clue groups the player has definitely satisfied, so the board can grey
 * them out (build plan 5.1).
 *
 * A group counts as satisfied only when it is *certain*: a run of exactly the right
 * length, closed at both ends by a known-empty cell or the edge of the board, and
 * reachable from one end without passing anything unknown. Scanning inward from both
 * ends catches the common case of a player working from the edges.
 *
 * Certainty matters more than generosity here. Greying out a clue the player has not
 * actually finished would actively mislead them, which is worse than greying out one
 * clue too few.
 */
object ClueProgress {

    /** One flag per clue value, in order, true when that group is definitely placed. */
    fun satisfied(clue: Clue, line: List<CellState>): List<Boolean> =
        satisfied(clue, line.size) { line[it] }

    /**
     * The same, reading cells through [cellAt] instead of from a materialised list.
     *
     * The board is a flat row-major list, so a column would otherwise have to be copied
     * out before it could be checked. At 20x20 that is 40 short-lived lists every time a
     * cell changes, which during a drag is every few frames.
     */
    fun satisfied(clue: Clue, length: Int, cellAt: (Int) -> CellState): List<Boolean> {
        val values = clue.values
        if (values.isEmpty()) return emptyList()

        // If what is already filled spells out the clue exactly, every group is
        // accounted for, whatever the player has or has not crossed.
        //
        // Without this a line that is completely and correctly filled keeps its clue
        // lit, because the edge-scanning below needs each run closed by a known-empty
        // cell. Plenty of players never cross at all, so they would finish a row and get
        // no acknowledgement of it - which is exactly what it looked like on a device.
        if (filledRunsMatch(values, length, cellAt)) return List(values.size) { true }

        val done = BooleanArray(values.size)
        val matchedFromLeft = scan(values, length, cellAt, done, fromLeft = true)

        // Only scan back from the right for groups the left pass did not already claim,
        // or a fully solved line would match the same group twice.
        scan(values, length, cellAt, done, fromLeft = false, stopAtIndex = matchedFromLeft)

        return done.toList()
    }

    /**
     * True when the filled cells already form exactly this clue.
     *
     * Certain, not generous: if the runs of filled cells match the clue run for run,
     * nothing further can be added without breaking it, so every group is placed.
     */
    private fun filledRunsMatch(values: List<Int>, length: Int, cellAt: (Int) -> CellState): Boolean {
        var group = 0
        var run = 0
        for (i in 0 until length) {
            if (cellAt(i) == CellState.FILLED) {
                run++
            } else if (run > 0) {
                if (group >= values.size || values[group] != run) return false
                group++
                run = 0
            }
        }
        if (run > 0) {
            if (group >= values.size || values[group] != run) return false
            group++
        }
        return group == values.size
    }

    /** True when every group in the clue is satisfied. */
    fun isComplete(clue: Clue, line: List<CellState>): Boolean =
        clue.values.isEmpty() || satisfied(clue, line).all { it }

    /**
     * Walks from one end matching closed runs against clue values in order.
     *
     * @return how many groups this pass matched.
     */
    private fun scan(
        values: List<Int>,
        length: Int,
        cellAt: (Int) -> CellState,
        done: BooleanArray,
        fromLeft: Boolean,
        stopAtIndex: Int = 0,
    ): Int {
        val n = length
        val k = values.size
        var matched = 0
        var at = 0

        while (at < n && matched < k - stopAtIndex) {
            val position = if (fromLeft) at else n - 1 - at
            val cell = cellAt(position)

            // Past an unknown cell nothing can be asserted.
            if (cell == CellState.UNKNOWN) break
            if (cell.isKnownEmpty) {
                at++
                continue
            }

            // Measure the run of filled cells starting here.
            var runLength = 0
            while (at + runLength < n) {
                val p = if (fromLeft) at + runLength else n - 1 - (at + runLength)
                if (cellAt(p) != CellState.FILLED) break
                runLength++
            }

            // A run still touching an unknown cell might yet grow, so it proves nothing.
            val afterIndex = at + runLength
            val closed = afterIndex >= n || run {
                val p = if (fromLeft) afterIndex else n - 1 - afterIndex
                cellAt(p).isKnownEmpty
            }
            if (!closed) break

            val groupIndex = if (fromLeft) matched else k - 1 - matched
            if (runLength != values[groupIndex]) break

            done[groupIndex] = true
            matched++
            at = afterIndex
        }
        return matched
    }
}
