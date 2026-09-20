package com.ganim.nonogram.puzzle.tutorial

import com.ganim.nonogram.puzzle.solver.BacktrackingVerifier
import com.ganim.nonogram.puzzle.solver.PuzzleSolver
import com.ganim.nonogram.puzzle.solver.SolveResult
import com.ganim.nonogram.puzzle.solver.Verification
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The tutorial is the one place where being wrong is worse than being absent.
 *
 * A player who has never solved a nonogram has no way to tell a sound deduction from a
 * lucky guess, so a script that fills the wrong square, or that claims a move is forced
 * when it is not, teaches them to guess - and someone who thinks nonograms are guessing
 * games will decide they dislike nonograms.
 *
 * These checks are what make the hand-written script trustworthy.
 */
class TutorialLessonTest {

    @Test
    @DisplayName("the lesson puzzle is solvable by logic alone, with one solution")
    fun `the lesson puzzle is fair`() {
        val size = TutorialLesson.SIZE
        val run = PuzzleSolver().run(size, size, TutorialLesson.rowClues, TutorialLesson.colClues)

        assertTrue(run.result is SolveResult.Unique) {
            "the tutorial teaches a puzzle that needs guessing: ${run.result}"
        }
        BacktrackingVerifier()
            .countSolutions(size, size, TutorialLesson.rowClues, TutorialLesson.colClues)
            .shouldBe(Verification.Counted(1, 2))
    }

    @Test
    @DisplayName("no step fills a square the answer leaves empty, or crosses a filled one")
    fun `every move agrees with the solution`() {
        TutorialLesson.steps.forEachIndexed { index, step ->
            step.moves.forEach { move ->
                val filled = TutorialLesson.solution[move.row * TutorialLesson.SIZE + move.col]
                val claims = move.mark == TutorialLesson.Mark.FILL
                assertEquals(filled, claims) {
                    "step $index marks (${move.row},${move.col}) as ${move.mark}, " +
                        "but the answer has it ${if (filled) "filled" else "empty"}"
                }
            }
        }
    }

    @Test
    fun `the lesson ends with the whole board decided`() {
        val end = TutorialLesson.stateAfter(TutorialLesson.steps.lastIndex)
        end.forEachIndexed { i, mark ->
            assertTrue(mark != null) {
                "square (${i / TutorialLesson.SIZE},${i % TutorialLesson.SIZE}) is never " +
                    "decided - the lesson stops before the picture is finished"
            }
        }
        // Every square decided exactly once: a square written twice means an earlier
        // step was wrong and a later one quietly corrected it, which is precisely the
        // "guess and fix" habit the lesson must not demonstrate.
        TutorialLesson.moveCount shouldBe TutorialLesson.SIZE * TutorialLesson.SIZE
    }

    @Test
    fun `every step is reachable and says something`() {
        assertTrue(TutorialLesson.steps.size >= 5) { "too short to teach anything" }
        TutorialLesson.steps.forEachIndexed { index, step ->
            assertTrue(step.caption.isNotBlank()) { "step $index has no caption" }
            (step.focusRows + step.focusCols).forEach { line ->
                assertTrue(line in 0 until TutorialLesson.SIZE) {
                    "step $index highlights line $line, which is off the board"
                }
            }
            // A step that moves squares should say which line it is reasoning about;
            // an unexplained mark is the thing this lesson exists to avoid.
            if (step.moves.isNotEmpty()) {
                assertTrue(step.focusRows.isNotEmpty() || step.focusCols.isNotEmpty()) {
                    "step $index fills squares without highlighting a line"
                }
            }
        }
        // The first and last steps carry the framing - what the numbers mean, and that
        // none of it was guesswork - so neither should be doing board work as well.
        TutorialLesson.steps.first().moves.shouldBe(emptyList())
        TutorialLesson.steps.last().moves.shouldBe(emptyList())
    }

    @Test
    fun `stepping forward matches jumping straight to a step`() {
        val running = arrayOfNulls<TutorialLesson.Mark>(TutorialLesson.SIZE * TutorialLesson.SIZE)
        TutorialLesson.steps.forEachIndexed { index, step ->
            step.moves.forEach { running[it.row * TutorialLesson.SIZE + it.col] = it.mark }
            assertEquals(running.toList(), TutorialLesson.stateAfter(index).toList()) {
                "stateAfter($index) disagrees with replaying the steps one at a time"
            }
        }
    }
}
