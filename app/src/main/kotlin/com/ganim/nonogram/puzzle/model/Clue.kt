package com.ganim.nonogram.puzzle.model

/**
 * The clue for a single line (row or column): the ordered run lengths, e.g. `[3, 1, 2]`.
 *
 * A line with no filled cells is represented by an empty [values] list. Zeros are not a
 * valid run length and are rejected here rather than silently normalised, so that a
 * malformed pack or hand-written test fixture fails loudly instead of quietly changing
 * the puzzle. Use [Clue.EMPTY] for a blank line.
 */
data class Clue(val values: List<Int>) {

    init {
        require(values.all { it > 0 }) { "Clue run lengths must be positive, got $values" }
    }

    /** Total number of filled cells this clue accounts for. */
    val filledCount: Int get() = values.sum()

    /** Shortest line that could hold this clue: all runs plus one gap between each. */
    val minimumLineLength: Int get() = if (values.isEmpty()) 0 else filledCount + values.size - 1

    val isBlank: Boolean get() = values.isEmpty()

    override fun toString(): String = if (values.isEmpty()) "0" else values.joinToString(" ")

    companion object {
        val EMPTY = Clue(emptyList())

        fun of(vararg values: Int): Clue = Clue(values.toList())

        /** Derives the clue described by a concrete line of booleans. */
        fun fromLine(line: BooleanArray): Clue {
            val runs = ArrayList<Int>()
            var run = 0
            for (filled in line) {
                if (filled) {
                    run++
                } else if (run > 0) {
                    runs.add(run)
                    run = 0
                }
            }
            if (run > 0) runs.add(run)
            return Clue(runs)
        }
    }
}
