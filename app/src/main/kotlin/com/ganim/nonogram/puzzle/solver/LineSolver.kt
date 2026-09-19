package com.ganim.nonogram.puzzle.solver

import com.ganim.nonogram.puzzle.model.CellState
import com.ganim.nonogram.puzzle.model.Clue

/**
 * The core deduction primitive (build plan 4.2).
 *
 * Given one line - its clue and the cell states currently known - it returns the line
 * with every forced deduction applied: a cell is forced filled if it is filled in
 * *every* placement of the clue consistent with what is already known, and forced
 * empty if it is empty in every such placement.
 *
 * ## Why this is a dynamic program rather than a literal enumeration
 *
 * The plan describes enumerating all valid placements. This computes exactly that
 * answer without materialising the placements, in O(n * k) instead of O(C(f + k, k)):
 *
 *  - possible[j][i] - can blocks j..k-1 be laid out in cells i..n-1?
 *  - reach[j][i]    - can we arrive at "cell i, blocks j.. still to place" from the start?
 *
 * A state is *live* when both hold, which means it participates in at least one
 * complete valid placement. Walking the transitions out of live states marks, per cell,
 * whether it is filled in some placement and whether it is empty in some placement.
 * A cell that can only be one of the two is forced. This is provably the same result as
 * enumeration, and LineSolverPropertyTest pins that down by cross-checking every
 * deduction against a brute-force enumerator over exhaustive small inputs.
 *
 * Deductions are never guesses: if this returns FILLED for a cell, every solution of the
 * line has that cell filled. Returning null means the line admits no valid placement.
 *
 * ## Not thread-safe
 * The memo cache is a plain map. Construct one LineSolver per thread; the generator does.
 */
class LineSolver(private val cacheCapacity: Int = 1 shl 16) {

    private val cache = HashMap<LineKey, Long>()

    /**
     * Applies every forced deduction to [current].
     *
     * Returns null if the clue cannot be satisfied by any line consistent with [current].
     * Cells the clue cannot decide are returned untouched, and a player's [CellState.CROSSED]
     * marks survive - a cross already means "empty", so it is never downgraded to
     * [CellState.EMPTY].
     */
    fun solveOrNull(clue: Clue, current: List<CellState>): List<CellState>? {
        val n = current.size
        require(n <= MAX_LINE) { "Line length $n exceeds supported maximum $MAX_LINE" }
        if (clue.minimumLineLength > n) return null

        var filledMask = 0
        var emptyMask = 0
        for (i in 0 until n) {
            when {
                current[i] == CellState.FILLED -> filledMask = filledMask or (1 shl i)
                current[i].isKnownEmpty -> emptyMask = emptyMask or (1 shl i)
            }
        }

        val key = LineKey(clue, n, filledMask, emptyMask)
        val cached = cache[key]
        val packed = if (cached != null) {
            cached
        } else {
            val computed = deduce(clue.values.toIntArray(), n, filledMask, emptyMask)
            if (cache.size >= cacheCapacity) cache.clear()
            cache[key] = computed
            computed
        }

        if (packed == CONTRADICTION) return null

        val resultFilled = packed.toInt()
        val resultEmpty = (packed ushr 32).toInt()
        return List(n) { i ->
            when {
                (resultFilled ushr i) and 1 == 1 -> CellState.FILLED
                (resultEmpty ushr i) and 1 == 1 ->
                    if (current[i] == CellState.CROSSED) CellState.CROSSED else CellState.EMPTY
                else -> current[i]
            }
        }
    }

    /**
     * The signature named in build plan 4.2.
     *
     * @throws ContradictoryLineException when the clue admits no valid placement. Callers
     * that expect contradictions - the puzzle solver, which sees them constantly while
     * rating candidate grids - should use [solveOrNull] instead.
     */
    fun solve(clue: Clue, current: List<CellState>): List<CellState> =
        solveOrNull(clue, current)
            ?: throw ContradictoryLineException("Clue $clue cannot be satisfied by line $current")

    /** True when at least one line consistent with [current] satisfies [clue]. */
    fun isSatisfiable(clue: Clue, current: List<CellState>): Boolean =
        solveOrNull(clue, current) != null

    private fun deduce(blocks: IntArray, n: Int, filledMask: Int, emptyMask: Int): Long {
        val k = blocks.size
        val width = n + 1

        // Prefix counts turn "is any cell in [a, b) known empty / filled?" into O(1).
        val emptyPrefix = IntArray(width)
        val filledPrefix = IntArray(width)
        for (i in 0 until n) {
            emptyPrefix[i + 1] = emptyPrefix[i] + ((emptyMask ushr i) and 1)
            filledPrefix[i + 1] = filledPrefix[i] + ((filledMask ushr i) and 1)
        }

        // possible[j * width + i]: blocks j..k-1 fit into cells i..n-1.
        val possible = BooleanArray((k + 1) * width)
        val tailBase = k * width
        for (i in n downTo 0) {
            // With no blocks left the tail must be empty, so it may hold no known-filled cell.
            possible[tailBase + i] = filledPrefix[n] - filledPrefix[i] == 0
        }
        for (j in k - 1 downTo 0) {
            val len = blocks[j]
            val base = j * width
            val nextBase = (j + 1) * width
            possible[base + n] = false
            for (i in n - 1 downTo 0) {
                var ok = (filledMask ushr i) and 1 == 0 && possible[base + i + 1]
                if (!ok) {
                    val end = i + len
                    if (end <= n && emptyPrefix[end] - emptyPrefix[i] == 0) {
                        ok = if (end == n) {
                            possible[nextBase + n]
                        } else {
                            (filledMask ushr end) and 1 == 0 && possible[nextBase + end + 1]
                        }
                    }
                }
                possible[base + i] = ok
            }
        }

        if (!possible[0]) return CONTRADICTION

        val reach = BooleanArray((k + 1) * width)
        reach[0] = true
        var canBeFilled = 0
        var canBeEmpty = 0

        for (i in 0 until n) {
            for (j in 0..k) {
                val here = j * width + i
                if (!reach[here] || !possible[here]) continue

                // Leave cell i empty and carry the same blocks forward.
                if ((filledMask ushr i) and 1 == 0 && possible[here + 1]) {
                    reach[here + 1] = true
                    canBeEmpty = canBeEmpty or (1 shl i)
                }

                // Or start block j at cell i, followed by a gap unless it ends the line.
                if (j < k) {
                    val len = blocks[j]
                    val end = i + len
                    if (end <= n && emptyPrefix[end] - emptyPrefix[i] == 0) {
                        val nextBase = (j + 1) * width
                        val next = when {
                            end == n -> if (possible[nextBase + n]) nextBase + n else -1
                            (filledMask ushr end) and 1 != 0 -> -1
                            possible[nextBase + end + 1] -> nextBase + end + 1
                            else -> -1
                        }
                        if (next >= 0) {
                            reach[next] = true
                            for (t in i until end) canBeFilled = canBeFilled or (1 shl t)
                            if (end < n) canBeEmpty = canBeEmpty or (1 shl end)
                        }
                    }
                }
            }
        }

        var resultFilled = 0
        var resultEmpty = 0
        for (i in 0 until n) {
            val f = (canBeFilled ushr i) and 1 == 1
            val e = (canBeEmpty ushr i) and 1 == 1
            when {
                f && !e -> resultFilled = resultFilled or (1 shl i)
                e && !f -> resultEmpty = resultEmpty or (1 shl i)
                !f && !e -> return CONTRADICTION
            }
        }
        return (resultFilled.toLong() and 0xFFFFFFFFL) or (resultEmpty.toLong() shl 32)
    }

    private data class LineKey(val clue: Clue, val length: Int, val filled: Int, val empty: Int)

    companion object {
        /**
         * Longest line the bitmask representation supports. Shipped grids top out at 20
         * (4.1); the cap is 31 so the packed result can never collide with CONTRADICTION.
         */
        const val MAX_LINE = 31

        private const val CONTRADICTION = -1L
    }
}

/** Thrown by [LineSolver.solve] when a line admits no valid placement. */
class ContradictoryLineException(message: String) : IllegalArgumentException(message)

/**
 * Convenience form of the signature named in build plan 4.2, for callers that do not
 * hold a [LineSolver]. Allocates a fresh solver, so it gains nothing from memoisation -
 * hot paths should keep a [LineSolver] instance.
 */
fun solveLine(clue: Clue, current: List<CellState>): List<CellState> =
    LineSolver().solve(clue, current)
