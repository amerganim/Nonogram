package com.ganim.nonogram.puzzle.generator

import com.ganim.nonogram.puzzle.model.Difficulty
import com.ganim.nonogram.puzzle.model.Grid
import com.ganim.nonogram.puzzle.model.Puzzle
import com.ganim.nonogram.puzzle.solver.LineSolver
import com.ganim.nonogram.puzzle.solver.PuzzleSolver
import com.ganim.nonogram.puzzle.solver.SolveResult
import com.ganim.nonogram.puzzle.solver.SolveStats
import kotlin.random.Random

/**
 * Generate-and-reject puzzle generation (build plan 4.4).
 *
 * ```
 * 1. Generate a candidate solution grid at the target fill density.
 * 2. Derive row and column clues from it.
 * 3. Run PuzzleSolver against the clues from a blank state.
 * 4. If the result is not Unique, discard.
 * 5. Rate difficulty from solveDepth and size.
 * 6. Emit.
 * ```
 *
 * The plan warns the rejection rate will be high, and it is - most random grids stall
 * the logic solver. That is fine: this runs offline on a desktop JVM, never on device.
 *
 * [generateAny] exists because throwing away a perfectly good puzzle for landing in the
 * wrong difficulty band is pure waste. The pack builder generates whatever comes out,
 * rates it, and files it in the bucket it belongs to, only falling back to targeted
 * generation for buckets that are still short.
 *
 * ## Not thread-safe
 * Holds a [PuzzleSolver], which holds a [LineSolver]. One instance per thread.
 */
class PuzzleGenerator(
    private val rng: Random,
    private val solver: PuzzleSolver = PuzzleSolver(LineSolver()),
) {

    /**
     * Produces one logic-solvable puzzle of the given [size], whatever difficulty it
     * turns out to be, or null if [maxAttempts] candidates were all rejected.
     */
    fun generateAny(size: Int, maxAttempts: Int = DEFAULT_ATTEMPTS): Puzzle? {
        require(size in DifficultyRater.SHIPPED_SIZES) {
            "Unsupported grid size $size, expected one of ${DifficultyRater.SHIPPED_SIZES}"
        }
        repeat(maxAttempts) {
            attempt(size)?.let { return it }
        }
        return null
    }

    /**
     * Produces a puzzle of the given [size] that rates as [target], or null if
     * [maxAttempts] candidates were all rejected.
     */
    fun generate(size: Int, target: Difficulty, maxAttempts: Int = DEFAULT_ATTEMPTS): Puzzle? {
        require(target in DifficultyRater.bandsFor(size)) {
            "$size x $size grids cannot rate as $target (they produce ${DifficultyRater.bandsFor(size)})"
        }
        repeat(maxAttempts) {
            val puzzle = attempt(size)
            if (puzzle != null && puzzle.difficulty == target) return puzzle
        }
        return null
    }

    /** One candidate: shape a grid, derive its clues, and keep it only if logic solves it. */
    private fun attempt(size: Int): Puzzle? {
        val density = rng.nextDouble(GridShaper.MIN_DENSITY, GridShaper.MAX_DENSITY)
        val cells = GridShaper.forSize(size, size, density, rng)
        if (!GridShaper.isUsable(cells)) return null

        val rowClues = Grid.rowClues(size, size, cells)
        val colClues = Grid.columnClues(size, size, cells)

        val run = solver.run(size, size, rowClues, colClues)
        val depth = (run.result as? SolveResult.Unique)?.depth ?: return null

        return Puzzle.fromSolution(
            width = size,
            height = size,
            solution = cells,
            difficulty = DifficultyRater.rate(size, depth, run.stats),
            solveDepth = depth,
        )
    }

    /** Same as [attempt], but also hands back the solve statistics, for calibration. */
    private fun attemptWithStats(size: Int): Pair<Puzzle, SolveStats>? {
        val density = rng.nextDouble(GridShaper.MIN_DENSITY, GridShaper.MAX_DENSITY)
        val cells = GridShaper.forSize(size, size, density, rng)
        if (!GridShaper.isUsable(cells)) return null

        val rowClues = Grid.rowClues(size, size, cells)
        val colClues = Grid.columnClues(size, size, cells)

        val run = solver.run(size, size, rowClues, colClues)
        val depth = (run.result as? SolveResult.Unique)?.depth ?: return null

        val puzzle = Puzzle.fromSolution(
            width = size,
            height = size,
            solution = cells,
            difficulty = DifficultyRater.rate(size, depth, run.stats),
            solveDepth = depth,
        )
        return puzzle to run.stats
    }

    /**
     * Generates [sampleSize] logic-solvable grids per size and reports the depth
     * distribution. This is how the [DifficultyRater] cutoffs get their numbers, and how
     * the plan's "rejection rate will be high" claim gets a concrete value.
     */
    fun calibrate(size: Int, sampleSize: Int, maxAttempts: Int = sampleSize * 400): Calibration {
        val samples = ArrayList<Sample>(sampleSize)
        var attempts = 0
        while (samples.size < sampleSize && attempts < maxAttempts) {
            attempts++
            attemptWithStats(size)?.let { (puzzle, stats) ->
                samples.add(
                    Sample(
                        depth = puzzle.solveDepth,
                        slowPasses = stats.slowPasses,
                        openingYield = stats.openingYield,
                        hardness = DifficultyRater.hardness(puzzle.solveDepth, stats),
                    )
                )
            }
        }
        return Calibration(size, samples, attempts)
    }

    /** One measured puzzle: its depth, the secondary signals, and the composite score. */
    class Sample(
        val depth: Int,
        val slowPasses: Int,
        val openingYield: Double,
        val hardness: Int,
    )

    /** Depth measurements for one grid size. */
    class Calibration(val size: Int, val samples: List<Sample>, val attempts: Int) {

        val depths: List<Int> get() = samples.map { it.depth }

        val accepted: Int get() = samples.size

        /** Share of candidate grids that turned out to be solvable by logic alone. */
        val acceptanceRate: Double get() = if (attempts == 0) 0.0 else accepted.toDouble() / attempts

        val histogram: Map<Int, Int> get() = depths.groupingBy { it }.eachCount().toSortedMap()

        /** The depth at or below which [fraction] of accepted puzzles fall. */
        fun percentile(fraction: Double): Int {
            if (depths.isEmpty()) return 0
            val sorted = depths.sorted()
            val idx = ((sorted.size - 1) * fraction).toInt().coerceIn(0, sorted.size - 1)
            return sorted[idx]
        }

        override fun toString(): String = buildString {
            appendLine("size ${size}x$size: $accepted accepted / $attempts attempts " +
                "(${"%.2f".format(acceptanceRate * 100)}% acceptance)")
            if (depths.isNotEmpty()) {
                appendLine("  depth p50=${percentile(0.50)} p75=${percentile(0.75)} " +
                    "p90=${percentile(0.90)} p99=${percentile(0.99)} max=${depths.max()}")
                appendLine("  depth histogram: " + histogram.entries.joinToString(" ") { "${it.key}:${it.value}" })
                appendLine("  slowPasses histogram: " + samples.groupingBy { it.slowPasses }.eachCount()
                    .toSortedMap().entries.joinToString(" ") { "${it.key}:${it.value}" })
                appendLine("  openingYield p10=${"%.2f".format(yieldPercentile(0.10))} " +
                    "p50=${"%.2f".format(yieldPercentile(0.50))} p90=${"%.2f".format(yieldPercentile(0.90))}")
                appendLine("  hardness histogram: " + samples.groupingBy { it.hardness }.eachCount()
                    .toSortedMap().entries.joinToString(" ") { "${it.key}:${it.value}" })
                appendLine("  joint (depth, slowPasses) -> count:")
                samples.groupingBy { it.depth to it.slowPasses }.eachCount()
                    .entries.sortedWith(compareBy({ it.key.first }, { it.key.second }))
                    .chunked(8)
                    .forEach { chunk ->
                        appendLine("    " + chunk.joinToString("  ") { "d${it.key.first}s${it.key.second}:${it.value}" })
                    }
            }
        }

        /**
         * The hardness cutoff that sends [fraction] of puzzles to the easier band.
         *
         * This is the number to paste into DifficultyRater: pick the fraction from the
         * quota split you need (e.g. 900 easy of 1,650 total 10x10 puzzles is 0.545) and
         * the cutoff falls out of the measured distribution rather than being guessed.
         */
        fun suggestedCutoff(fraction: Double): Int {
            if (samples.isEmpty()) return 0
            // Scores are discrete, so taking the value at the percentile index can
            // overshoot badly when many puzzles share it. Search the distinct scores for
            // the cutoff whose achieved share actually lands closest to the target.
            return samples.map { it.hardness }.distinct().sorted()
                .minBy { candidate -> kotlin.math.abs(shareAtOrBelow(candidate) - fraction) }
        }

        /** Share of samples that would land in the easier band for a given [cutoff]. */
        fun shareAtOrBelow(cutoff: Int): Double =
            if (samples.isEmpty()) 0.0 else samples.count { it.hardness <= cutoff }.toDouble() / samples.size

        /** The opening-pass yield at or below which [fraction] of accepted puzzles fall. */
        fun yieldPercentile(fraction: Double): Double {
            if (samples.isEmpty()) return 0.0
            val sorted = samples.map { it.openingYield }.sorted()
            val idx = ((sorted.size - 1) * fraction).toInt().coerceIn(0, sorted.size - 1)
            return sorted[idx]
        }
    }

    companion object {
        const val DEFAULT_ATTEMPTS = 50_000
    }
}
