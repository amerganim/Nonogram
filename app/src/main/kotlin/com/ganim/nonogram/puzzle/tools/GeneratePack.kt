package com.ganim.nonogram.puzzle.tools

import com.ganim.nonogram.puzzle.generator.DifficultyRater
import com.ganim.nonogram.puzzle.generator.PuzzleGenerator
import com.ganim.nonogram.puzzle.model.Difficulty
import com.ganim.nonogram.puzzle.model.Puzzle
import com.ganim.nonogram.puzzle.pack.PuzzlePack
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.system.exitProcess

/**
 * Offline generation tool (build plan 4.5). Runs on the desktop JVM via
 * `gradlew generatePuzzlePack`; never on device.
 *
 * ## Reproducibility
 *
 * The plan says to commit the generated pack because "deterministic output shouldn't be
 * rebuilt per-developer". That only holds if generation really is deterministic, so
 * work is partitioned across a *fixed* worker count with per-worker seeds derived from
 * the run seed. The same `--seed` and `--threads` produce a byte-identical pack on any
 * machine, whatever its core count. That is also why `--threads` defaults to a constant
 * rather than to `availableProcessors()`.
 */
object GeneratePack {

    /** Plan 4.5: "roughly 1,500 easy, 1,500 medium, 1,200 hard, 800 expert". */
    private val DEFAULT_QUOTAS: Map<Pair<Difficulty, Int>, Int> = mapOf(
        (Difficulty.EASY to 5) to 600,
        (Difficulty.EASY to 10) to 900,
        (Difficulty.MEDIUM to 10) to 750,
        (Difficulty.MEDIUM to 15) to 750,
        (Difficulty.HARD to 15) to 600,
        (Difficulty.HARD to 20) to 600,
        (Difficulty.EXPERT to 20) to 800,
    )

    private const val DEFAULT_SEED = 20260919L
    private const val DEFAULT_THREADS = 8
    private const val DEFAULT_OUT = "src/main/assets/puzzles.bin"

    /** Plan 4.5: "If the pack exceeds 1 MB, the encoding is wrong." */
    private const val SIZE_BUDGET_BYTES = 1024 * 1024

    fun run(argv: Array<String>) {
        val args = Args.parse(argv)

        if (args.calibrate) {
            calibrate(args)
            return
        }

        val quotas = scaleQuotas(args.count)
        val total = quotas.values.sum()
        println("Generating $total puzzles (seed=${args.seed}, threads=${args.threads})")
        quotas.entries.sortedBy { it.key.second }.forEach { (key, want) ->
            println("  ${key.first} ${key.second}x${key.second}: $want")
        }
        println()

        val started = System.nanoTime()
        val buckets = generateBuckets(quotas, args)
        val elapsed = (System.nanoTime() - started) / 1_000_000_000.0

        val shortfalls = quotas.filter { (key, want) -> (buckets[key]?.size ?: 0) < want }
        val puzzles = orderForPack(quotas, buckets)

        println()
        println("Generated ${puzzles.size} puzzles in ${"%.1f".format(elapsed)}s")
        if (shortfalls.isNotEmpty()) {
            System.err.println("SHORTFALL - these buckets could not be filled:")
            shortfalls.forEach { (key, want) ->
                System.err.println("  ${key.first} ${key.second}x${key.second}: ${buckets[key]?.size ?: 0} of $want")
            }
            System.err.println("Either lower the quota or retune DifficultyRater's cutoffs.")
            exitProcess(1)
        }

        val bytes = PuzzlePack.encode(puzzles)
        val out = File(args.out).absoluteFile
        out.parentFile?.mkdirs()
        out.writeBytes(bytes)

        println("Wrote ${bytes.size} bytes (${"%.1f".format(bytes.size / 1024.0)} KB) to $out")
        summarise(puzzles)

        if (bytes.size > SIZE_BUDGET_BYTES) {
            System.err.println("Pack is ${bytes.size} bytes, over the ${SIZE_BUDGET_BYTES} byte budget.")
            exitProcess(1)
        }
    }

    // --- generation ------------------------------------------------------------------

    private fun generateBuckets(
        quotas: Map<Pair<Difficulty, Int>, Int>,
        args: Args,
    ): MutableMap<Pair<Difficulty, Int>, MutableList<Puzzle>> {
        val buckets = HashMap<Pair<Difficulty, Int>, MutableList<Puzzle>>()
        quotas.keys.forEach { buckets[it] = ArrayList() }
        val seen = HashSet<String>()

        for (size in DifficultyRater.SHIPPED_SIZES) {
            val wanted = quotas.filterKeys { it.second == size }
            if (wanted.isEmpty()) continue
            val need = wanted.values.sum()

            // Overshoot, because the difficulty split of what comes out is not controllable.
            val harvest = (need * HARVEST_FACTOR).toInt()
            println("size ${size}x$size: harvesting $harvest puzzles to fill $need slots")

            val produced = harvestParallel(size, harvest, args)
            for (puzzle in produced) {
                if (!seen.add(puzzle.id)) continue
                val key = puzzle.difficulty to size
                val quota = quotas[key] ?: continue
                val bucket = buckets.getValue(key)
                if (bucket.size < quota) bucket.add(puzzle)
            }

            wanted.forEach { (key, want) ->
                val have = buckets.getValue(key).size
                println("  ${key.first}: $have of $want")
                if (have < want) topUp(key, want - have, buckets.getValue(key), seen, args)
            }
        }
        return buckets
    }

    /**
     * Splits the harvest across a fixed number of workers, each with its own seed and
     * its own generator. Results are concatenated in worker order, so the output does
     * not depend on which worker finishes first.
     */
    private fun harvestParallel(size: Int, harvest: Int, args: Args): List<Puzzle> {
        val perWorker = (harvest + args.threads - 1) / args.threads
        val pool = Executors.newFixedThreadPool(args.threads)
        try {
            val tasks = (0 until args.threads).map { worker ->
                Callable {
                    val rng = Random(args.seed * 1_000_003L + size * 31L + worker)
                    val generator = PuzzleGenerator(rng)
                    val out = ArrayList<Puzzle>(perWorker)
                    repeat(perWorker) {
                        generator.generateAny(size, maxAttempts = ATTEMPTS_PER_PUZZLE)?.let(out::add)
                    }
                    out
                }
            }
            return pool.invokeAll(tasks).flatMap { it.get() }
        } finally {
            pool.shutdown()
            pool.awaitTermination(1, TimeUnit.MINUTES)
        }
    }

    /** Targeted single-threaded generation for a bucket the harvest left short. */
    private fun topUp(
        key: Pair<Difficulty, Int>,
        missing: Int,
        bucket: MutableList<Puzzle>,
        seen: MutableSet<String>,
        args: Args,
    ) {
        val (difficulty, size) = key
        println("    topping up $difficulty ${size}x$size by $missing")
        val generator = PuzzleGenerator(Random(args.seed * 7919L + size * 17L + difficulty.ordinal))
        var added = 0
        var giveUp = 0
        while (added < missing && giveUp < TOP_UP_GIVE_UP) {
            val puzzle = generator.generate(size, difficulty, maxAttempts = ATTEMPTS_PER_PUZZLE)
            if (puzzle == null) {
                giveUp++
                continue
            }
            if (seen.add(puzzle.id)) {
                bucket.add(puzzle)
                added++
            }
        }
    }

    /** Stable pack order: by size, then difficulty, then puzzle id. */
    private fun orderForPack(
        quotas: Map<Pair<Difficulty, Int>, Int>,
        buckets: Map<Pair<Difficulty, Int>, MutableList<Puzzle>>,
    ): List<Puzzle> = quotas.keys
        .sortedWith(compareBy({ it.second }, { it.first.ordinal }))
        .flatMap { key -> buckets.getValue(key).sortedBy { it.id } }

    // --- calibration -----------------------------------------------------------------

    private fun calibrate(args: Args) {
        println("Calibration run: ${args.sample} logic-solvable grids per size (seed=${args.seed})")
        println("Use these numbers to set DifficultyRater's cutoffs.")
        println()
        for (size in DifficultyRater.SHIPPED_SIZES) {
            val generator = PuzzleGenerator(Random(args.seed + size))
            val started = System.nanoTime()
            val calibration = generator.calibrate(size, args.sample)
            val elapsed = (System.nanoTime() - started) / 1_000_000_000.0
            print(calibration)

            // Cutoff that splits this size the way the pack quotas need it split.
            val quotas = DEFAULT_QUOTAS.filterKeys { it.second == size }
            if (quotas.size == 2) {
                val bands = quotas.keys.sortedBy { it.first.ordinal }
                val easier = quotas.getValue(bands[0])
                val fraction = easier.toDouble() / quotas.values.sum()
                val cutoff = calibration.suggestedCutoff(fraction)
                println(
                    "  quota split: ${bands[0].first} $easier / ${bands[1].first} ${quotas.getValue(bands[1])} " +
                        "-> need ${"%.1f".format(fraction * 100)}% in the easier band"
                )
                println(
                    "  SUGGESTED CUTOFF for ${size}x$size: $cutoff " +
                        "(gives ${"%.1f".format(calibration.shareAtOrBelow(cutoff) * 100)}% easier)"
                )
            }
            println("  took ${"%.1f".format(elapsed)}s")
            println()
        }
    }

    // --- reporting -------------------------------------------------------------------

    private fun summarise(puzzles: List<Puzzle>) {
        println()
        println("Pack contents:")
        puzzles.groupBy { it.difficulty to it.width }
            .toSortedMap(compareBy({ it.second }, { it.first.ordinal }))
            .forEach { (key, group) ->
                val depths = group.map { it.solveDepth }
                println(
                    "  ${key.first.name.padEnd(6)} ${key.second}x${key.second}: ${group.size} puzzles, " +
                        "depth ${depths.min()}-${depths.max()}"
                )
            }
        println("  distinct ids: ${puzzles.map { it.id }.toSet().size} of ${puzzles.size}")
    }

    private fun scaleQuotas(count: Int?): Map<Pair<Difficulty, Int>, Int> {
        val defaultTotal = DEFAULT_QUOTAS.values.sum()
        if (count == null || count == defaultTotal) return DEFAULT_QUOTAS
        val scale = count.toDouble() / defaultTotal
        return DEFAULT_QUOTAS.mapValues { (_, want) -> (want * scale).toInt().coerceAtLeast(1) }
    }

    private const val HARVEST_FACTOR = 2.2
    private const val ATTEMPTS_PER_PUZZLE = 200_000
    private const val TOP_UP_GIVE_UP = 40

    private class Args(
        val count: Int?,
        val seed: Long,
        val out: String,
        val threads: Int,
        val calibrate: Boolean,
        val sample: Int,
    ) {
        companion object {
            fun parse(argv: Array<String>): Args {
                val values = HashMap<String, String>()
                for (arg in argv) {
                    val trimmed = arg.removePrefix("--")
                    val idx = trimmed.indexOf('=')
                    if (idx < 0) values[trimmed] = "true" else values[trimmed.substring(0, idx)] = trimmed.substring(idx + 1)
                }
                return Args(
                    count = values["count"]?.toInt(),
                    seed = values["seed"]?.toLong() ?: DEFAULT_SEED,
                    out = values["out"] ?: DEFAULT_OUT,
                    threads = values["threads"]?.toInt() ?: DEFAULT_THREADS,
                    calibrate = values["calibrate"] == "true",
                    sample = values["sample"]?.toInt() ?: 2_000,
                )
            }
        }
    }
}

fun main(args: Array<String>) = GeneratePack.run(args)
