package com.ganim.nonogram.game

import com.ganim.nonogram.puzzle.model.CellState
import com.ganim.nonogram.puzzle.model.Clue
import com.ganim.nonogram.puzzle.solver.LineSolver

/**
 * Says *why* a hinted square is what it is.
 *
 * ## Why this is the thing worth building
 *
 * Every nonogram app has a hint button, and in every one of them it does the same
 * thing: it fills in a square and tells you nothing. That unsticks you once and leaves
 * you no better at the next one, which is why players describe hints as cheating and
 * then use them anyway.
 *
 * This app can do better than that because it already contains a real line solver - the
 * one that proves every shipped puzzle is solvable without guessing. It knows not just
 * which square is forced but which line forced it, so the hint can name the reasoning
 * and the player can learn the move rather than borrow the answer.
 *
 * ## The rule this file lives under
 *
 * **An explanation that is not true is worse than no explanation.** A player who applies
 * a rule they were told and gets a mistake will not trust the app again. So every reason
 * below is checked against the actual line before it is offered, and anything that
 * cannot be justified falls back to [Reason.FORCED], which says only what is certainly
 * true: this square follows from that line.
 */
class HintExplainer(private val lines: LineSolver = LineSolver()) {

    /** Which line the reasoning is about, so the board can highlight it. */
    enum class Axis { ROW, COLUMN }

    /**
     * The shape of the argument.
     *
     * Ordered roughly by how satisfying they are to be shown: a player who learns
     * overlap has learned the single most useful nonogram technique there is.
     */
    enum class Reason { FULL_LINE, OVERLAP, CLUE_DONE, BLANK_LINE, FORCED }

    data class Explanation(
        val axis: Axis,
        /** 0-based index of the row or column. */
        val line: Int,
        val reason: Reason,
        val text: String,
    )

    /**
     * Explains [index] against the board in [state].
     *
     * Tries the row first, then the column: if both force the square, the row is the one
     * a player reads along, so it is the easier one to follow.
     */
    fun explain(state: GameState, index: Int): Explanation? {
        val puzzle = state.puzzle
        val row = index / puzzle.width
        val col = index % puzzle.width

        forLine(state, Axis.ROW, row, col)?.let { return it }
        return forLine(state, Axis.COLUMN, col, row)
    }

    /**
     * Whether [line] on [axis] forces the square at [position] on its own, and why.
     *
     * "On its own" matters: the full solver reaches answers by playing rows and columns
     * off each other for many passes, and an explanation that needs six of those steps
     * is not an explanation. Only a square that one line settles by itself gets a
     * reason.
     */
    private fun forLine(state: GameState, axis: Axis, line: Int, position: Int): Explanation? {
        val puzzle = state.puzzle
        val clue = if (axis == Axis.ROW) puzzle.rowClues[line] else puzzle.colClues[line]
        val length = if (axis == Axis.ROW) puzzle.width else puzzle.height

        val current = List(length) { i ->
            val cell = if (axis == Axis.ROW) {
                state.board[line * puzzle.width + i]
            } else {
                state.board[i * puzzle.width + line]
            }
            when (cell) {
                CellState.FILLED -> CellState.FILLED
                // The player's crosses are notes and can be wrong; a wrong one would
                // make this line unsatisfiable and produce no explanation at all.
                else -> CellState.UNKNOWN
            }
        }

        val solved = lines.solveOrNull(clue, current) ?: return null
        if (solved[position] == CellState.UNKNOWN) return null

        val filled = solved[position] == CellState.FILLED
        val reason = classify(clue, current, length, filled)
        return Explanation(axis, line, reason, phrase(axis, line, clue, length, reason, filled))
    }

    /**
     * Picks the simplest true description of the deduction.
     *
     * Each branch is a condition on the line itself, not a guess at what the solver was
     * thinking - which is what makes the sentence safe to show.
     */
    private fun classify(
        clue: Clue,
        current: List<CellState>,
        length: Int,
        filled: Boolean,
    ): Reason = when {
        clue.isBlank -> Reason.BLANK_LINE

        // Nothing to choose: the runs and their gaps use the whole line.
        clue.minimumLineLength == length -> Reason.FULL_LINE

        // Every run is already on the board, so whatever is left cannot be filled.
        !filled && filledRunsMatch(current, clue) -> Reason.CLUE_DONE

        // One run longer than the slack it has: its middle is covered wherever it sits.
        filled && clue.values.size == 1 && clue.values.first() > length - clue.values.first() ->
            Reason.OVERLAP

        else -> Reason.FORCED
    }

    private fun phrase(
        axis: Axis,
        line: Int,
        clue: Clue,
        length: Int,
        reason: Reason,
        filled: Boolean,
    ): String {
        val name = "${if (axis == Axis.ROW) "Row" else "Column"} ${line + 1}"
        return when (reason) {
            Reason.BLANK_LINE ->
                "$name has no clue at all, so every square in it is empty."

            Reason.FULL_LINE ->
                "$name needs ${clue.values.joinToString(" + ")} plus a gap between each, " +
                    "which is exactly $length. There is only one way to fit that."

            Reason.CLUE_DONE ->
                "$name already has its ${clue.values.joinToString(" and ")}. " +
                    "Its clue is finished, so the rest of the line is empty."

            Reason.OVERLAP -> {
                val run = clue.values.first()
                "$name wants $run together in $length. Slide that run to either end and " +
                    "the middle ${run - (length - run)} squares are covered both times."
            }

            Reason.FORCED ->
                if (filled) {
                    "$name can only hold its ${clue} one way here - this square has to be filled."
                } else {
                    "No arrangement of $name's ${clue} reaches this square, so it is empty."
                }
        }
    }

    /**
     * Whether the filled runs already spell out the clue.
     *
     * Duplicated from [ClueProgress] on purpose: that one answers "should this clue look
     * greyed out", which is a drawing question asked every frame, and coupling a hint's
     * wording to it would make a rendering change able to alter an explanation.
     */
    private fun filledRunsMatch(current: List<CellState>, clue: Clue): Boolean {
        val runs = mutableListOf<Int>()
        var run = 0
        current.forEach { cell ->
            if (cell == CellState.FILLED) {
                run++
            } else if (run > 0) {
                runs += run
                run = 0
            }
        }
        if (run > 0) runs += run
        return runs == clue.values
    }
}
