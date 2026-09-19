package com.ganim.nonogram.puzzle.model

/**
 * Row-major grid helpers shared by the solver, the generator and the pack codec.
 *
 * A grid is a [BooleanArray] of `width * height` cells indexed `row * width + col`.
 *
 * Bitset encoding (build plan §4.4/§4.5, and reused for `boardSnapshot` in §6.5):
 * cell `i` lives in byte `i / 8` at bit `i % 8`, least-significant bit first. Trailing
 * bits in the final byte are zero. A 20x20 grid is 400 bits = 50 bytes.
 */
object Grid {

    fun index(width: Int, row: Int, col: Int): Int = row * width + col

    fun row(width: Int, solution: BooleanArray, row: Int): BooleanArray =
        BooleanArray(width) { col -> solution[row * width + col] }

    fun column(width: Int, height: Int, solution: BooleanArray, col: Int): BooleanArray =
        BooleanArray(height) { row -> solution[row * width + col] }

    fun rowClues(width: Int, height: Int, solution: BooleanArray): List<Clue> =
        List(height) { r -> Clue.fromLine(row(width, solution, r)) }

    fun columnClues(width: Int, height: Int, solution: BooleanArray): List<Clue> =
        List(width) { c -> Clue.fromLine(column(width, height, solution, c)) }

    fun filledCount(solution: BooleanArray): Int = solution.count { it }

    fun density(solution: BooleanArray): Double =
        if (solution.isEmpty()) 0.0 else filledCount(solution).toDouble() / solution.size

    /** Packs cells into a bitset. See the encoding note on this object. */
    fun toBitset(cells: BooleanArray): ByteArray {
        val bytes = ByteArray((cells.size + 7) / 8)
        for (i in cells.indices) {
            if (cells[i]) {
                val b = i ushr 3
                bytes[b] = (bytes[b].toInt() or (1 shl (i and 7))).toByte()
            }
        }
        return bytes
    }

    /** Unpacks [cellCount] cells from a bitset written by [toBitset]. */
    fun fromBitset(bytes: ByteArray, cellCount: Int, offset: Int = 0): BooleanArray {
        val needed = (cellCount + 7) / 8
        require(bytes.size - offset >= needed) {
            "Bitset too short: need $needed bytes for $cellCount cells, have ${bytes.size - offset}"
        }
        return BooleanArray(cellCount) { i ->
            (bytes[offset + (i ushr 3)].toInt() shr (i and 7)) and 1 == 1
        }
    }

    /** Renders a grid for test failure messages and generator logs. */
    fun render(width: Int, height: Int, cells: BooleanArray, filled: Char = '#', empty: Char = '.'): String =
        (0 until height).joinToString("\n") { r ->
            (0 until width).map { c -> if (cells[r * width + c]) filled else empty }.joinToString("")
        }
}
