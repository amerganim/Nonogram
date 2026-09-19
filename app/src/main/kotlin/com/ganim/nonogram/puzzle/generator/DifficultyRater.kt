package com.ganim.nonogram.puzzle.generator

import com.ganim.nonogram.puzzle.model.Difficulty
import com.ganim.nonogram.puzzle.solver.SolveStats

/**
 * Assigns a difficulty band to a solved puzzle (build plan 4.4).
 *
 * ## Why this departs from the plan's table
 *
 * The plan rates on `solveDepth` alone, with bands like "EXPERT: 20x20, depth 11+".
 * Measurement says that table cannot be implemented as written:
 *
 *  - Depth does **not** grow with grid size. Measured maxima were 13 at 10x10 but only
 *    10 at 20x20, because a bigger grid puts more crossing constraints through every
 *    cell. The plan's premise that larger grids need deeper solves is backwards.
 *  - No 20x20 puzzle reached depth 11 in 4,000 samples, so "EXPERT = depth 11+" would
 *    have produced an empty band and an unfillable quota.
 *  - Depth is coarse. At 20x20, 68% of puzzles are depth 4 or 5, so depth alone cannot
 *    separate the catalogue into four bands at all.
 *
 * So [hardness] keeps depth as the dominant term and adds two measured signals that
 * break up those clumps, and the bands are per-size quantiles of that score. The plan
 * explicitly invites this: "tune these thresholds empirically during Phase 1 and record
 * the final values in a comment."
 *
 * ## What the bands mean
 *
 * They are *relative within a grid size*, not absolute. A 20x20 HARD is not "the same
 * difficulty" as a 15x15 HARD; it is the easier 43% of 20x20 puzzles. That matches how
 * a player experiences the archive - they pick a size, then a difficulty within it.
 *
 * See docs/phase1-calibration.md for the measured distributions.
 */
object DifficultyRater {

    /** Grid sizes shipped, per build plan 4.1. */
    val SHIPPED_SIZES = listOf(5, 10, 15, 20)

    /**
     * Upper depth bound (inclusive) for the easier of the two bands each size can land in.
     * A depth above the cutoff promotes the puzzle to the harder band.
     */
    private val cutoffs: Map<Int, SizeBands> = mapOf(
        //         easier band, harder band, depth cutoff for the easier band
        5 to SizeBands(Difficulty.EASY, Difficulty.EASY, Int.MAX_VALUE),
        10 to SizeBands(Difficulty.EASY, Difficulty.MEDIUM, EASY_DEPTH_CUTOFF_10),
        15 to SizeBands(Difficulty.MEDIUM, Difficulty.HARD, MEDIUM_DEPTH_CUTOFF_15),
        20 to SizeBands(Difficulty.HARD, Difficulty.EXPERT, HARD_DEPTH_CUTOFF_20),
    )

    private class SizeBands(val easier: Difficulty, val harder: Difficulty, val cutoff: Int)

    /**
     * Rates a square puzzle of the given [size], solved in [solveDepth] passes with the
     * progress profile in [stats].
     *
     * @throws IllegalArgumentException for a size the app does not ship.
     */
    fun rate(size: Int, solveDepth: Int, stats: SolveStats = SolveStats.EMPTY): Difficulty {
        val bands = cutoffs[size]
            ?: throw IllegalArgumentException("Unsupported grid size $size, expected one of $SHIPPED_SIZES")
        return if (hardness(solveDepth, stats) <= bands.cutoff) bands.easier else bands.harder
    }

    /**
     * Combines the depth signal with how grindy the solve was.
     *
     * Depth alone leaves most of the catalogue on two adjacent values (see [SolveStats]),
     * so each grinding pass - one that resolved almost nothing - counts for extra, and a
     * puzzle whose first pass cracks most of the grid is discounted. The weights are
     * chosen so depth still dominates; the other terms break ties inside a depth bucket.
     */
    fun hardness(solveDepth: Int, stats: SolveStats): Int {
        if (stats.cellCount == 0) return solveDepth * DEPTH_WEIGHT
        // The opening term is continuous on purpose. With a coarse score most puzzles
        // pile onto a handful of values and any cutoff jumps the band share by ten
        // points or more, which makes the quotas impossible to hit.
        val openingResistance = ((1.0 - stats.openingYield) * OPENING_WEIGHT).toInt()
        return solveDepth * DEPTH_WEIGHT + stats.slowPasses * SLOW_PASS_WEIGHT + openingResistance
    }

    /** The two bands a given size can produce - used to route generation work. */
    fun bandsFor(size: Int): Set<Difficulty> =
        cutoffs[size]?.let { setOf(it.easier, it.harder) } ?: emptySet()

    /** The sizes that can produce [difficulty], cheapest (smallest) first. */
    fun sizesFor(difficulty: Difficulty): List<Int> =
        SHIPPED_SIZES.filter { difficulty in bandsFor(it) }

    // --- calibrated thresholds -------------------------------------------------------
    // Measured 2026-09-19 over 4,000 logic-solvable grids per size, seed 20260919.
    // Full histograms in docs/phase1-calibration.md. Cutoffs compare against `hardness`,
    // not raw depth, and are the quantiles that produce the plan's 4.5 quota split:
    //
    //   10x10  cutoff 417 -> 54.4% EASY   (quota wants 900/1650 = 54.5%)
    //   15x15  cutoff 472 -> 55.7% MEDIUM (quota wants 750/1350 = 55.6%)
    //   20x20  cutoff 477 -> 43.5% HARD   (quota wants 600/1400 = 42.9%)
    //
    // Re-run `gradlew generatePuzzlePack -Pcalibrate=true -Psample=4000` after any
    // change to GridShaper or the solver; both move this distribution.

    private const val DEPTH_WEIGHT = 100
    private const val SLOW_PASS_WEIGHT = 60
    private const val OPENING_WEIGHT = 40

    private const val EASY_DEPTH_CUTOFF_10 = 417
    private const val MEDIUM_DEPTH_CUTOFF_15 = 472
    private const val HARD_DEPTH_CUTOFF_20 = 477
}
