package com.ganim.nonogram.game

import com.ganim.nonogram.puzzle.model.CellState
import com.ganim.nonogram.puzzle.model.Puzzle
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * An explanation that is not true is worse than no explanation.
 *
 * A player who applies a rule the app told them and collects a mistake for it will not
 * trust the next one - so the important test here is not that the wording is pretty, it
 * is that the square really is forced, and forced for the stated reason.
 */
class HintExplainerTest {

    private val explainer = HintExplainer()
    private val hints = HintProvider()

    /**
     * A heart, the same 5x5 the tutorial teaches.
     *
     * Row 2 is `#####` - five in a row of five - and column 1 is `2` with nothing else,
     * which gives both a full-line and a blank-line case on one board.
     */
    private fun heart() = Puzzle.fromRenderedRows(
        listOf(
            ".#.#.",
            "#####",
            "#####",
            ".###.",
            "..#..",
        ),
    )

    private fun blank(puzzle: Puzzle) = GameState.newGame(puzzle)

    @Test
    @DisplayName("a line whose clues exactly fill it is explained as such")
    fun `full line`() {
        val state = blank(heart())
        // Row 1 (0-based) is five filled in a width of five.
        val explanation = explainer.explain(state, 1 * 5 + 2)

        explanation?.reason shouldBe HintExplainer.Reason.FULL_LINE
        explanation?.axis shouldBe HintExplainer.Axis.ROW
        explanation?.line shouldBe 1
        assertTrue(explanation!!.text.contains("only one way")) { explanation.text }
    }

    @Test
    fun `a line with no clue says every square in it is empty`() {
        // A row of nothing, in a puzzle that has one.
        val puzzle = Puzzle.fromRenderedRows(listOf("....", "####", "####", "...."))
        val explanation = explainer.explain(blank(puzzle), 0)

        explanation?.reason shouldBe HintExplainer.Reason.BLANK_LINE
        assertTrue(explanation!!.text.contains("no clue")) { explanation.text }
    }

    @Test
    @DisplayName("overlap is named, and the arithmetic in the sentence is right")
    fun `overlap`() {
        // One run of 4 in a line of 5 overlaps by 3.
        val puzzle = Puzzle.fromRenderedRows(listOf(".####", ".####", ".####", ".####", "....."))
        val explanation = explainer.explain(blank(puzzle), 0 * 5 + 3)

        explanation?.reason shouldBe HintExplainer.Reason.OVERLAP
        // 4 in 5 leaves 1 of slack, so 3 squares are certain.
        assertTrue(explanation!!.text.contains("middle 3")) { explanation.text }
    }

    @Test
    @DisplayName("every explanation is true: the square really is what it claims")
    fun `explanations never lie`() {
        val puzzle = heart()
        var state = blank(puzzle)
        var steps = 0

        // Walk a whole solve through the hint button, checking each explanation against
        // the answer before applying it.
        while (steps < puzzle.cellCount * 2) {
            val index = hints.nextHint(state) ?: break
            val explanation = explainer.explain(state, index)
            val shouldBeFilled = puzzle.solution[index]

            if (explanation != null) {
                val within = when (explanation.axis) {
                    HintExplainer.Axis.ROW -> index / puzzle.width == explanation.line
                    HintExplainer.Axis.COLUMN -> index % puzzle.width == explanation.line
                }
                assertTrue(within) {
                    "explanation blames ${explanation.axis} ${explanation.line} for a " +
                        "square that is not on it"
                }
                // The wording must match what the square actually is.
                val claimsEmpty = explanation.text.contains("empty") ||
                    explanation.text.contains("is empty")
                if (claimsEmpty) {
                    assertTrue(!shouldBeFilled) {
                        "said empty, answer says filled: ${explanation.text}"
                    }
                }
            }

            state = GameEngine.paint(
                state,
                index,
                if (shouldBeFilled) CellState.FILLED else CellState.CROSSED,
            )
            steps++
        }

        assertTrue(steps > 0) { "the hint provider offered nothing on a blank board" }
    }

    @Test
    fun `a finished line is explained as finished`() {
        val puzzle = heart()
        var state = blank(puzzle)
        // Fill row 3 exactly: ".###." - its clue is 3, and the ends must then be empty.
        listOf(1, 2, 3).forEach { col ->
            state = GameEngine.paint(state, 3 * 5 + col, CellState.FILLED)
        }
        val explanation = explainer.explain(state, 3 * 5 + 0)

        explanation?.reason shouldBe HintExplainer.Reason.CLUE_DONE
        assertTrue(explanation!!.text.contains("finished")) { explanation.text }
    }

    @Test
    fun `nothing is explained for a square no single line settles`() {
        val puzzle = heart()
        // Row 0 is "1 1" in five and column 0 is "2" in five; neither pins (0,0) alone.
        val explanation = explainer.explain(blank(puzzle), 0)
        assertTrue(explanation == null) {
            "claimed to explain a square that needs both lines: ${explanation?.text}"
        }
    }
}
