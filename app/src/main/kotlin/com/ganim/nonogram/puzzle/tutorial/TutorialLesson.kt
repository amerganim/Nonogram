package com.ganim.nonogram.puzzle.tutorial

import com.ganim.nonogram.puzzle.model.Clue
import com.ganim.nonogram.puzzle.model.Grid

/**
 * The worked example the How to play screen animates.
 *
 * ## Why it is a script and not a recording
 *
 * A nonogram is not taught by being told the rules. It is taught by watching someone
 * make one deduction, then the next, and noticing that at no point did they guess. So
 * this is a full solve of one small puzzle, broken into steps, each with the reason for
 * the move rather than a description of it.
 *
 * Every step here is *forced* - it follows from the clues and what is already on the
 * board, with no guessing anywhere. That is the single most important thing a new player
 * has to learn, because someone who thinks nonograms involve guessing will play one
 * badly and stop.
 *
 * ## Why it is pure Kotlin
 *
 * A hand-written script can teach something false: a caption that claims a square is
 * forced when it is not, or a move that fills a square the answer leaves empty. Keeping
 * it out of the UI layer means `TutorialLessonTest` can check the whole thing against
 * the real solver - that the puzzle has exactly one solution, that every move agrees
 * with it, and that the steps together determine all 25 squares.
 */
object TutorialLesson {

    /** What a step writes into a square. */
    enum class Mark { FILL, CROSS }

    data class Move(val row: Int, val col: Int, val mark: Mark)

    /**
     * One beat of the lesson: a reason, the lines it is about, and what follows.
     *
     * The screen highlights the focused lines, because "column 3" means nothing to
     * someone who has not yet learned to read the clue gutters. They are lists because
     * several steps apply one idea to two or three lines at once, and highlighting only
     * the first while the caption says "these three columns" teaches the player to
     * distrust the highlight.
     */
    data class Step(
        val caption: String,
        val focusRows: List<Int> = emptyList(),
        val focusCols: List<Int> = emptyList(),
        val moves: List<Move> = emptyList(),
    )

    const val SIZE = 5

    /** What the finished lesson shows. Named, like the picture puzzles. */
    const val PICTURE_NAME = "a heart"

    private val solutionRows = listOf(
        ".#.#.",
        "#####",
        "#####",
        ".###.",
        "..#..",
    )

    val solution: BooleanArray =
        BooleanArray(SIZE * SIZE) { i -> solutionRows[i / SIZE][i % SIZE] == '#' }

    val rowClues: List<Clue> = Grid.rowClues(SIZE, SIZE, solution)
    val colClues: List<Clue> = Grid.columnClues(SIZE, SIZE, solution)

    private fun fill(vararg cells: Pair<Int, Int>) =
        cells.map { (r, c) -> Move(r, c, Mark.FILL) }

    private fun cross(vararg cells: Pair<Int, Int>) =
        cells.map { (r, c) -> Move(r, c, Mark.CROSS) }

    /**
     * The solve, in order.
     *
     * Each caption gives the *reason*, in the words a player would use, and each one
     * introduces exactly one new idea: a full line, then overlap, then a finished clue,
     * then counting the gaps. Stacking two ideas into one step is how a tutorial loses
     * people.
     */
    val steps: List<Step> = listOf(
        Step(
            caption = "The numbers count filled squares. Down the side for rows, " +
                "across the top for columns.",
        ),
        Step(
            caption = "This row is 5 wide and asks for 5. There is only one way to fit that.",
            focusRows = listOf(1),
            moves = fill(1 to 0, 1 to 1, 1 to 2, 1 to 3, 1 to 4),
        ),
        Step(
            caption = "So is this one.",
            focusRows = listOf(2),
            moves = fill(2 to 0, 2 to 1, 2 to 2, 2 to 3, 2 to 4),
        ),
        Step(
            caption = "These three columns each want 4 in a column of 5. Whether the run " +
                "starts at the top or one below, the middle squares are covered either way.",
            focusCols = listOf(1, 2, 3),
            moves = fill(3 to 1, 3 to 2, 3 to 3),
        ),
        Step(
            caption = "This row asked for 3, and now has 3 together. Its clue is done, so " +
                "the rest of the row must be empty. Mark empty squares with a cross - " +
                "they are how you keep track.",
            focusRows = listOf(3),
            moves = cross(3 to 0, 3 to 4),
        ),
        Step(
            caption = "The outer columns each wanted 2, and each already has 2. Everything " +
                "else in them is empty.",
            focusCols = listOf(0, 4),
            moves = cross(0 to 0, 4 to 0, 0 to 4, 4 to 4),
        ),
        Step(
            caption = "The top row needs two single squares, and only three spots are left. " +
                "Two runs need a gap between them, so they have to sit at the ends.",
            focusRows = listOf(0),
            moves = fill(0 to 1, 0 to 3) + cross(0 to 2),
        ),
        Step(
            caption = "That finishes those two columns - 4 each. The squares below are empty.",
            focusCols = listOf(1, 3),
            moves = cross(4 to 1, 4 to 3),
        ),
        Step(
            caption = "The middle column still needs 4 in a row. The top is empty, so the " +
                "run has to reach the bottom.",
            focusCols = listOf(2),
            moves = fill(4 to 2),
        ),
        Step(
            caption = "Solved, without a single guess - and it is $PICTURE_NAME. Every " +
                "puzzle in the app works this way.",
        ),
    )

    /** How many moves the lesson makes in total. Every square, once. */
    val moveCount: Int get() = steps.sumOf { it.moves.size }

    /**
     * The board as it stands after [stepIndex] steps have played.
     *
     * Null means undecided. Rebuilt from the start each time rather than mutated, so
     * stepping backwards - or jumping straight to the end - cannot drift out of sync
     * with stepping forwards.
     */
    fun stateAfter(stepIndex: Int): Array<Mark?> {
        val board = arrayOfNulls<Mark>(SIZE * SIZE)
        steps.take(stepIndex + 1).forEach { step ->
            step.moves.forEach { board[it.row * SIZE + it.col] = it.mark }
        }
        return board
    }
}
