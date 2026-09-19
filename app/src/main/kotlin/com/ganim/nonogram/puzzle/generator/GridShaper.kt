package com.ganim.nonogram.puzzle.generator

import com.ganim.nonogram.puzzle.model.Grid
import kotlin.random.Random

/**
 * Produces candidate solution grids for the generator (build plan 4.4).
 *
 * Two strategies, because the right answer depends on size:
 *
 *  - [random] - independent coin flips per cell. Fine at 5x5 and 10x10, where the grid
 *    is too small to read as a picture anyway.
 *  - [blob] - random fill followed by cellular-automaton smoothing, which pulls filled
 *    cells into contiguous regions. The plan calls for this at 15x15 and 20x20 because
 *    "purely random grids produce visual noise", and the completed picture is the
 *    emotional payload of the whole app.
 *
 * Smoothing also happens to raise the generator's yield: blobby grids have longer,
 * more constrained runs, and more of them turn out to be solvable by logic alone.
 */
object GridShaper {

    /** Below this width/height, smoothing has too little room to produce a shape. */
    const val SMOOTHING_MIN_SIZE = 15

    /** Fill density band from the plan - outside it, puzzles are dull or trivial. */
    const val MIN_DENSITY = 0.45
    const val MAX_DENSITY = 0.60

    /** Picks the strategy the plan specifies for this grid size. */
    fun forSize(width: Int, height: Int, density: Double, rng: Random): BooleanArray =
        if (minOf(width, height) >= SMOOTHING_MIN_SIZE) {
            blob(width, height, density, rng)
        } else {
            random(width, height, density, rng)
        }

    /** Independent per-cell coin flips, then nudged to land inside the density band. */
    fun random(width: Int, height: Int, density: Double, rng: Random): BooleanArray {
        val cells = BooleanArray(width * height) { rng.nextDouble() < density }
        adjustDensity(width, height, cells, density, rng)
        return cells
    }

    /**
     * Random fill, then [SMOOTHING_PASSES] rounds of majority smoothing.
     *
     * A cell survives or turns on when at least [SMOOTHING_THRESHOLD] of its nine-cell
     * neighbourhood (itself included) is filled. Out-of-bounds neighbours count as
     * empty, which pulls shapes away from the edges and leaves the picture framed.
     */
    fun blob(width: Int, height: Int, density: Double, rng: Random): BooleanArray {
        // Smoothing sheds filled cells, so start above target and let it settle down.
        val seedDensity = (density + SEED_DENSITY_BOOST).coerceAtMost(0.92)
        var cells = BooleanArray(width * height) { rng.nextDouble() < seedDensity }

        repeat(SMOOTHING_PASSES) {
            val next = BooleanArray(cells.size)
            for (r in 0 until height) {
                for (c in 0 until width) {
                    next[r * width + c] = neighbourhoodCount(width, height, cells, r, c) >= SMOOTHING_THRESHOLD
                }
            }
            cells = next
        }

        adjustDensity(width, height, cells, density, rng)
        return cells
    }

    /**
     * Adds or removes cells until the grid sits inside the density band.
     *
     * Additions prefer cells that already touch filled neighbours and removals prefer
     * the most isolated filled cells, so correcting the density grows and trims the
     * existing shape instead of sprinkling noise over it.
     */
    fun adjustDensity(
        width: Int,
        height: Int,
        cells: BooleanArray,
        target: Double,
        rng: Random,
    ) {
        val total = cells.size
        val lower = ((target - DENSITY_TOLERANCE) * total).toInt().coerceAtLeast(1)
        val upper = ((target + DENSITY_TOLERANCE) * total).toInt().coerceAtMost(total - 1)
        var filled = cells.count { it }

        while (filled < lower) {
            val pick = pickCell(width, height, cells, rng, wantFilled = false, preferAttached = true) ?: break
            cells[pick] = true
            filled++
        }
        while (filled > upper) {
            val pick = pickCell(width, height, cells, rng, wantFilled = true, preferAttached = false) ?: break
            cells[pick] = false
            filled--
        }
    }

    /**
     * Chooses a cell to flip. [preferAttached] picks from cells with the most filled
     * neighbours; otherwise from those with the fewest.
     */
    private fun pickCell(
        width: Int,
        height: Int,
        cells: BooleanArray,
        rng: Random,
        wantFilled: Boolean,
        preferAttached: Boolean,
    ): Int? {
        var bestScore = if (preferAttached) Int.MIN_VALUE else Int.MAX_VALUE
        val best = ArrayList<Int>()
        for (r in 0 until height) {
            for (c in 0 until width) {
                val i = r * width + c
                if (cells[i] != wantFilled) continue
                val score = neighbourhoodCount(width, height, cells, r, c)
                val better = if (preferAttached) score > bestScore else score < bestScore
                if (better) {
                    bestScore = score
                    best.clear()
                    best.add(i)
                } else if (score == bestScore) {
                    best.add(i)
                }
            }
        }
        return if (best.isEmpty()) null else best[rng.nextInt(best.size)]
    }

    /** Filled cells in the 3x3 neighbourhood centred on (row, col), the centre included. */
    private fun neighbourhoodCount(
        width: Int,
        height: Int,
        cells: BooleanArray,
        row: Int,
        col: Int,
    ): Int {
        var count = 0
        for (dr in -1..1) {
            val r = row + dr
            if (r < 0 || r >= height) continue
            for (dc in -1..1) {
                val c = col + dc
                if (c < 0 || c >= width) continue
                if (cells[r * width + c]) count++
            }
        }
        return count
    }

    /** True when the grid is neither empty nor solid and sits in the plan's density band. */
    fun isUsable(cells: BooleanArray): Boolean {
        val density = Grid.density(cells)
        return density in MIN_DENSITY..MAX_DENSITY
    }

    private const val SMOOTHING_PASSES = 3
    private const val SMOOTHING_THRESHOLD = 5
    private const val SEED_DENSITY_BOOST = 0.06
    private const val DENSITY_TOLERANCE = 0.02
}
