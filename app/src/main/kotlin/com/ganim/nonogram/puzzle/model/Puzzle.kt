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

    fun isFilled(row: Int, col: Int): Boolean = solution[row * width + col]

    /** The packed form used by the puzzle pack and by in-progress board snapshots. */
    fun bitset(): ByteArray = Grid.toBitset(solution)

    fun render(): String = Grid.render(width, height, solution)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Puzzle) return false
        return id == other.id &&
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
        "Puzzle(id=$id, ${width}x$height, $difficulty, depth=$solveDepth)"

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
        ): Puzzle = Puzzle(
            id = stableId(width, height, solution),
            width = width,
            height = height,
            rowClues = Grid.rowClues(width, height, solution),
            colClues = Grid.columnClues(width, height, solution),
            solution = solution,
            difficulty = difficulty,
            solveDepth = solveDepth,
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
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(width.toByte())
            digest.update(height.toByte())
            digest.update(bitset, offset, length)
            val hash = digest.digest()
            val sb = StringBuilder(ID_BYTES * 2)
            for (i in 0 until ID_BYTES) sb.append("%02x".format(hash[i]))
            return sb.toString()
        }
    }
}
