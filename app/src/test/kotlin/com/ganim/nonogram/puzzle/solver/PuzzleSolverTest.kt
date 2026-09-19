package com.ganim.nonogram.puzzle.solver

import com.ganim.nonogram.puzzle.model.CellState
import com.ganim.nonogram.puzzle.model.Clue
import com.ganim.nonogram.puzzle.model.Grid
import com.ganim.nonogram.puzzle.model.Puzzle
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/**
 * Build plan 4.3 acceptance: "PuzzleSolver correctly classifies a hand-written suite:
 * at least 5 known-unique puzzles, 3 known-ambiguous, 2 contradictory."
 *
 * Every case is labelled by hand and then independently confirmed by
 * [BacktrackingVerifier], so a wrong hand-label fails the test rather than silently
 * validating the wrong behaviour.
 */
class PuzzleSolverTest {

    private val solver = PuzzleSolver()
    private val verifier = BacktrackingVerifier()

    // --- fixtures --------------------------------------------------------------------

    private class Case(
        val name: String,
        val width: Int,
        val height: Int,
        val rowClues: List<Clue>,
        val colClues: List<Clue>,
    ) {
        companion object {
            /** Builds a case from a picture, deriving its clues. */
            fun fromGrid(name: String, rows: List<String>): Case {
                val puzzle = Puzzle.fromRenderedRows(rows)
                return Case(name, puzzle.width, puzzle.height, puzzle.rowClues, puzzle.colClues)
            }

            fun fromClues(name: String, rows: List<Clue>, cols: List<Clue>) =
                Case(name, cols.size, rows.size, rows, cols)
        }
    }

    /** Five puzzles that logic alone finishes. */
    private val uniqueCases = listOf(
        Case.fromGrid(
            "5x5 solid block",
            listOf("#####", "#####", "#####", "#####", "#####"),
        ),
        Case.fromGrid(
            "5x5 single full row",
            listOf("#####", ".....", ".....", ".....", "....."),
        ),
        Case.fromGrid(
            "5x5 cross",
            listOf("..#..", "..#..", "#####", "..#..", "..#.."),
        ),
        Case.fromGrid(
            "5x5 border",
            listOf("#####", "#...#", "#...#", "#...#", "#####"),
        ),
        Case.fromGrid(
            "10x10 left half filled",
            List(10) { "#####....." },
        ),
    )

    /** Three puzzles where logic stalls because more than one grid fits the clues. */
    private val ambiguousCases = listOf(
        // The canonical switchable pair: this diagonal and its mirror both fit.
        Case.fromGrid("2x2 checker", listOf("#.", ".#")),
        Case.fromGrid("5x5 diagonal", listOf("#....", ".#...", "..#..", "...#.", "....#")),
        Case.fromGrid("4x4 diagonal", listOf("#...", ".#..", "..#.", "...#")),
    )

    /** Three clue sets no grid satisfies. */
    private val contradictoryCases = listOf(
        Case.fromClues(
            "1x1 row says filled, column says empty",
            rows = listOf(Clue.of(1)),
            cols = listOf(Clue.EMPTY),
        ),
        Case.fromClues(
            "2x2 rows full, columns single",
            rows = listOf(Clue.of(2), Clue.of(2)),
            cols = listOf(Clue.of(1), Clue.of(1)),
        ),
        Case.fromClues(
            "3x3 column totals disagree with row totals",
            rows = listOf(Clue.of(3), Clue.of(3), Clue.of(3)),
            cols = listOf(Clue.of(3), Clue.of(3), Clue.of(2)),
        ),
    )

    // --- classification --------------------------------------------------------------

    @TestFactory
    fun `unique puzzles are solved by logic alone`(): List<DynamicTest> =
        uniqueCases.map { case ->
            DynamicTest.dynamicTest(case.name) {
                val run = solver.run(case.width, case.height, case.rowClues, case.colClues)
                val result = run.result.shouldBeInstanceOf<SolveResult.Unique>()
                assert(result.depth >= 1) { "depth should be at least one pass" }
                assert(run.board.none { it == CellState.UNKNOWN }) { "board left unknown cells" }

                // Independent confirmation that the hand-label is right.
                verifier.countSolutions(case.width, case.height, case.rowClues, case.colClues) shouldBe
                    Verification.Counted(1, 2)
            }
        }

    @TestFactory
    fun `ambiguous puzzles stall instead of guessing`(): List<DynamicTest> =
        ambiguousCases.map { case ->
            DynamicTest.dynamicTest(case.name) {
                val run = solver.run(case.width, case.height, case.rowClues, case.colClues)
                run.result shouldBe SolveResult.Ambiguous

                val counted = verifier.countSolutions(case.width, case.height, case.rowClues, case.colClues)
                    .shouldBeInstanceOf<Verification.Counted>()
                assert(counted.isAmbiguous) { "${case.name} was labelled ambiguous but has ${counted.solutions} solution(s)" }
            }
        }

    @TestFactory
    fun `contradictory clue sets are reported as contradictions`(): List<DynamicTest> =
        contradictoryCases.map { case ->
            DynamicTest.dynamicTest(case.name) {
                solver.run(case.width, case.height, case.rowClues, case.colClues).result shouldBe
                    SolveResult.Contradiction

                verifier.countSolutions(case.width, case.height, case.rowClues, case.colClues) shouldBe
                    Verification.Counted(0, 2)
            }
        }

    // --- behaviour -------------------------------------------------------------------

    @Test
    fun `solving from a partial board finds only what is deducible from it`() {
        val case = uniqueCases[2] // 5x5 cross
        val partial = PuzzleSolver.blankBoard(case.width, case.height)
        val run = solver.run(case.width, case.height, case.rowClues, case.colClues, partial)
        run.isSolved shouldBe true
    }

    @Test
    fun `a solved board reproduces the clues it came from`() {
        val puzzle = Puzzle.fromRenderedRows(listOf("#####", "#...#", "#...#", "#...#", "#####"))
        val run = solver.run(puzzle.width, puzzle.height, puzzle.rowClues, puzzle.colClues)
        val cells = BooleanArray(puzzle.cellCount) { run.board[it] == CellState.FILLED }
        cells.toList() shouldBe puzzle.solution.toList()
        Grid.rowClues(puzzle.width, puzzle.height, cells) shouldBe puzzle.rowClues
        Grid.columnClues(puzzle.width, puzzle.height, cells) shouldBe puzzle.colClues
    }

    @Test
    fun `depth counts full row and column passes`() {
        // A solid block is fully forced by the very first row pass.
        val puzzle = Puzzle.fromRenderedRows(List(5) { "#####" })
        val result = solver.run(puzzle.width, puzzle.height, puzzle.rowClues, puzzle.colClues).result
        result shouldBe SolveResult.Unique(1)
    }

    @Test
    fun `solving is deterministic across repeated runs`() {
        val case = uniqueCases[3]
        val first = solver.run(case.width, case.height, case.rowClues, case.colClues)
        val second = PuzzleSolver().run(case.width, case.height, case.rowClues, case.colClues)
        first.result shouldBe second.result
        first.board.toList() shouldBe second.board.toList()
    }
}
