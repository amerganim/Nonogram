package com.ganim.nonogram.puzzle.solver

import com.ganim.nonogram.puzzle.model.Clue

/**
 * Exhaustive search that counts how many grids satisfy a clue set (build plan 4.3).
 *
 * This is a verification tool, not part of the shipping accept/reject decision: a
 * puzzle is shippable because [PuzzleSolver] solves it by logic, never because a
 * search found exactly one answer. Its job is to independently confirm that the
 * generator's "unique" verdicts are true.
 *
 * "Independently" is the whole point, so it deliberately shares no code with
 * [LineSolver]. It enumerates row placements directly and prunes on column feasibility.
 * Seeding it with LineSolver's deductions would make it much faster and completely
 * worthless - a soundness bug in LineSolver would be copied into the verifier and the
 * two would agree on a wrong answer.
 */
class BacktrackingVerifier(private val nodeBudget: Long = 50_000_000L) {

    /**
     * Counts solutions, stopping once [cap] have been found.
     *
     * [cap] of 2 is enough to answer "is this unique?" and keeps the search short.
     */
    fun countSolutions(
        width: Int,
        height: Int,
        rowClues: List<Clue>,
        colClues: List<Clue>,
        cap: Int = 2,
    ): Verification {
        require(width in 1..31) { "Width $width outside supported range" }
        require(rowClues.size == height && colClues.size == width) { "Clue counts do not match grid" }

        val rowPlacements = rowClues.map { placements(it, width) }
        if (rowPlacements.any { it.isEmpty() }) return Verification.Counted(0, cap)

        // Minimum column cells still required once `idx` blocks are consumed, used to
        // abandon a branch as soon as the remaining rows cannot possibly fit the rest.
        val stillNeeded = Array(width) { c ->
            val values = colClues[c].values
            IntArray(values.size + 1) { idx ->
                if (idx >= values.size) 0 else values.drop(idx).sum() + (values.size - idx) - 1
            }
        }

        val blockIdx = Array(height + 1) { IntArray(width) }
        val runLen = Array(height + 1) { IntArray(width) }

        val search = Search(width, height, colClues, rowPlacements, stillNeeded, blockIdx, runLen, cap, nodeBudget)
        return search.run()
    }

    fun isUnique(width: Int, height: Int, rowClues: List<Clue>, colClues: List<Clue>): Boolean =
        countSolutions(width, height, rowClues, colClues, cap = 2) == Verification.Counted(1, 2)

    /**
     * Every way [clue] can be laid out in a line of [length] cells, as bitmasks with
     * bit `i` set when cell `i` is filled.
     */
    fun placements(clue: Clue, length: Int): IntArray {
        if (clue.minimumLineLength > length) return IntArray(0)
        val out = ArrayList<Int>()
        fun place(blockIndex: Int, start: Int, mask: Int) {
            if (blockIndex == clue.values.size) {
                out.add(mask)
                return
            }
            val len = clue.values[blockIndex]
            val remaining = clue.values.drop(blockIndex + 1)
            val tail = if (remaining.isEmpty()) 0 else remaining.sum() + remaining.size
            var at = start
            while (at + len + tail <= length) {
                var bits = mask
                for (t in at until at + len) bits = bits or (1 shl t)
                place(blockIndex + 1, at + len + 1, bits)
                at++
            }
        }
        place(0, 0, 0)
        return out.toIntArray()
    }

    private class Search(
        val width: Int,
        val height: Int,
        val colClues: List<Clue>,
        val rowPlacements: List<IntArray>,
        val stillNeeded: Array<IntArray>,
        val blockIdx: Array<IntArray>,
        val runLen: Array<IntArray>,
        val cap: Int,
        val nodeBudget: Long,
    ) {
        var solutions = 0
        var nodes = 0L
        var exhausted = false

        fun run(): Verification {
            blockIdx[0].fill(0)
            runLen[0].fill(0)
            descend(0)
            return if (exhausted) Verification.BudgetExhausted else Verification.Counted(solutions, cap)
        }

        fun descend(row: Int) {
            if (exhausted || solutions >= cap) return
            if (row == height) {
                // Every column must have consumed all of its blocks. A run still open at
                // the bottom edge is fine - and common - as long as it is the column's
                // last block at its full length; the edge closes it.
                val idx = blockIdx[row]
                val run = runLen[row]
                for (c in 0 until width) {
                    val values = colClues[c].values
                    var consumed = idx[c]
                    if (run[c] > 0) {
                        if (consumed >= values.size || run[c] != values[consumed]) return
                        consumed++
                    }
                    if (consumed != values.size) return
                }
                solutions++
                return
            }

            val rowsLeft = height - row - 1
            for (mask in rowPlacements[row]) {
                if (++nodes > nodeBudget) {
                    exhausted = true
                    return
                }
                if (applyRow(row, mask, rowsLeft)) {
                    descend(row + 1)
                    if (exhausted || solutions >= cap) return
                }
            }
        }

        /** Folds one row into the column state, returning false if the branch is already dead. */
        fun applyRow(row: Int, mask: Int, rowsLeft: Int): Boolean {
            val fromIdx = blockIdx[row]
            val fromRun = runLen[row]
            val toIdx = blockIdx[row + 1]
            val toRun = runLen[row + 1]

            for (c in 0 until width) {
                var idx = fromIdx[c]
                var run = fromRun[c]
                val values = colClues[c].values

                if ((mask ushr c) and 1 == 1) {
                    if (idx >= values.size) return false
                    run++
                    if (run > values[idx]) return false
                } else if (run > 0) {
                    if (run != values[idx]) return false
                    idx++
                    run = 0
                }

                // Can the rows that are left still supply what this column still owes?
                val owed = if (run > 0) {
                    val rest = values.drop(idx + 1)
                    (values[idx] - run) + rest.sum() + rest.size
                } else {
                    stillNeeded[c][idx]
                }
                if (owed > rowsLeft) return false

                toIdx[c] = idx
                toRun[c] = run
            }
            return true
        }
    }
}

/** Result of a [BacktrackingVerifier] run. */
sealed class Verification {

    /** [solutions] grids satisfy the clues, counted up to [cappedAt]. */
    data class Counted(val solutions: Int, val cappedAt: Int) : Verification() {
        val isUnique: Boolean get() = solutions == 1
        val isAmbiguous: Boolean get() = solutions >= 2
        val isUnsatisfiable: Boolean get() = solutions == 0
    }

    /** The search hit its node budget. Says nothing about the puzzle - only that this took too long. */
    data object BudgetExhausted : Verification()
}
