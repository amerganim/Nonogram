package com.ganim.nonogram.puzzle.model

import java.security.MessageDigest

/**
 * A fully specified puzzle. Shape is fixed by build plan §4.1.
 *
 * [solution] is row-major, `width * height` long. Because Kotlin generates array
 * identity comparisons for `data class` members, [equals] and [hashCode] are written
 * out by hand - the generated ones would report two identical puzzles as different.
 *
 * Clues are derived, never stored in the pack (§4.4). Build instances through
 * [fromSolution] so that id and clues always agree with the grid.
 */
data class Puzzle(
    val id: String,
    val width: Int,
    val height: Int,
    val rowClues: List<Clue>,
    val colClues: List<Clue>,
    val solution: BooleanArray,
    val difficulty: Difficulty,
    val solveDepth: Int,
    /**
     * What the finished picture shows, for the hand-drawn puzzles.
     *
     * Empty for generated ones. They are shaped into blobs rather than drawn, so there
     * is nothing honest to call them - naming a blob "Cloud" would be worse than leaving
     * it unnamed.
     */
    val name: String = "",
) {
    init {
        require(width > 0 && height > 0) { "Puzzle must have positive dimensions, got ${width}x$height" }
        require(solution.size == width * height) {
            "Solution has ${solution.size} cells, expected ${width * height} for ${width}x$height"
        }
        require(rowClues.size == height) { "Expected $height row clues, got ${rowClues.size}" }
        require(colClues.size == width) { "Expected $width column clues, got ${colClues.size}" }
    }

    val cellCount: Int get() = width * height

    /** Cells the finished picture fills. Cached: the completion check reads it often. */
    val targetFilledCount: Int by lazy(LazyThreadSafetyMode.PUBLICATION) { solution.count { it } }



    /** The packed form used by the puzzle pack and by in-progress board snapshots. */
    fun bitset(): ByteArray = Grid.toBitset(solution)

    fun render(): String = Grid.render(width, height, solution)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Puzzle) return false
        return id == other.id &&
            name == other.name &&
            width == other.width &&
            height == other.height &&
            difficulty == other.difficulty &&
            solveDepth == other.solveDepth &&
            rowClues == other.rowClues &&
            colClues == other.colClues &&
            solution.contentEquals(other.solution)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + difficulty.hashCode()
        result = 31 * result + solveDepth
        result = 31 * result + solution.contentHashCode()
        return result
    }

    override fun toString(): String =
        "Puzzle(id=$id, ${width}x$height, $difficulty, depth=$solveDepth" +
            (if (name.isEmpty()) "" else ", \"$name\"") + ")"

    companion object {

        /**
         * Builds a puzzle from its grid, deriving clues and the stable id.
         *
         * The id is a truncated SHA-256 over the dimensions and the packed grid, so it
         * is identical on every machine and every run - the daily-puzzle selector and
         * the progress table both key off it, and it must survive a pack regeneration
         * unchanged for any puzzle whose grid did not change.
         */
        fun fromSolution(
            width: Int,
            height: Int,
            solution: BooleanArray,
            difficulty: Difficulty,
            solveDepth: Int,
            name: String = "",
        ): Puzzle = Puzzle(
            id = stableId(width, height, solution),
            width = width,
            height = height,
            rowClues = Grid.rowClues(width, height, solution),
            colClues = Grid.columnClues(width, height, solution),
            solution = solution,
            difficulty = difficulty,
            solveDepth = solveDepth,
            name = name,
        )

        /** Parses a grid written as rows of '#' (filled) and '.' (empty). For tests and fixtures. */
        fun fromRenderedRows(
            rows: List<String>,
            difficulty: Difficulty = Difficulty.EASY,
            solveDepth: Int = 0,
        ): Puzzle {
            require(rows.isNotEmpty()) { "Need at least one row" }
            val width = rows.first().length
            require(rows.all { it.length == width }) { "All rows must be the same length" }
            val cells = BooleanArray(width * rows.size)
            rows.forEachIndexed { r, line ->
                line.forEachIndexed { c, ch ->
                    require(ch == '#' || ch == '.') { "Unexpected grid character '$ch'" }
                    cells[r * width + c] = ch == '#'
                }
            }
            return fromSolution(width, rows.size, cells, difficulty, solveDepth)
        }

        private const val ID_BYTES = 10

        /**
         * One digest per thread, reused.
         *
         * `MessageDigest.getInstance` walks the security provider list on every call.
         * Indexing the 5,000-puzzle pack calls this once per record, and on a Galaxy A15
         * that provider lookup - not the hashing - was **1.2 seconds on the main
         * thread**, which is what made the app stutter for its first few seconds.
         * `digest()` resets the instance, so reuse is safe; the ThreadLocal is there
         * because the generator tool hashes from several threads at once.
         */
        private val digests = ThreadLocal.withInitial { MessageDigest.getInstance("SHA-256") }

        private val HEX = "0123456789abcdef".toCharArray()

        fun stableId(width: Int, height: Int, solution: BooleanArray): String =
            stableIdFromBitset(width, height, Grid.toBitset(solution))

        /**
         * The same id, computed straight from an already-packed grid.
         *
         * Lets the archive index 5,000 puzzles without unpacking a single bitset into a
         * BooleanArray or deriving a single clue list.
         */
        fun stableIdFromBitset(
            width: Int,
            height: Int,
            bitset: ByteArray,
            offset: Int = 0,
            length: Int = bitset.size - offset,
        ): String {
            val digest = digests.get()!!
            digest.update(width.toByte())
            digest.update(height.toByte())
            digest.update(bitset, offset, length)
            val hash = digest.digest()
            // Hand-rolled hex rather than "%02x".format: that is ten String.format calls
            // per id, fifty thousand for the pack, and each one parses its format string.
            val out = CharArray(ID_BYTES * 2)
            for (i in 0 until ID_BYTES) {
                val b = hash[i].toInt() and 0xFF
                out[i * 2] = HEX[b ushr 4]
                out[i * 2 + 1] = HEX[b and 0x0F]
            }
            return String(out)
        }
    }
}
