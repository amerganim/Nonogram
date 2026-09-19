package com.ganim.nonogram.game

import com.ganim.nonogram.puzzle.generator.DifficultyRater
import com.ganim.nonogram.puzzle.generator.PuzzleGenerator
import com.ganim.nonogram.puzzle.model.CellState
import com.ganim.nonogram.puzzle.model.Puzzle
import com.ganim.nonogram.puzzle.solver.PuzzleSolver
import com.ganim.nonogram.puzzle.solver.SolveResult
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * Build plan 5.4 and its acceptance criterion: "Hint always returns a logically-deducible
 * cell."
 */
class HintProviderTest {

    private val hints = HintProvider()
    private val solver = PuzzleSolver()

    @Test
    @DisplayName("every hint is a cell the player could have deduced themselves")
    fun `hints are always logically deducible`() {
        val generator = PuzzleGenerator(Random(4242))
        val puzzles = DifficultyRater.SHIPPED_SIZES.map { size ->
            generator.generateAny(size) ?: error("no puzzle at size $size")
        }

        puzzles.forEach { puzzle ->
            var state = GameState.newGame(puzzle)
            var taken = 0

            // Solve the whole puzzle by hint alone, checking each one.
            while (state.status == GameStatus.PLAYING && taken < puzzle.cellCount * 2) {
                val hint = hints.nextHint(state) ?: break
                assertTrue(deducibleNow(state, hint)) {
                    "hint at cell $hint was not deducible from the current board of ${puzzle.id}"
                }
                state = GameEngine.revealHint(state, hint)
                taken++
            }

            state.status shouldBe GameStatus.COMPLETE
        }
    }

    @Test
    fun `a hint reveals the true state of its cell`() {
        val puzzle = Puzzle.fromRenderedRows(listOf("#.#", ".#.", "#.#"))
        val state = GameState.newGame(puzzle)
        val hint = hints.nextHint(state).shouldNotBeNull()

        val revealed = GameEngine.revealHint(state, hint)
        val expected = if (puzzle.solution[hint]) CellState.FILLED else CellState.CROSSED
        revealed.board[hint] shouldBe expected
    }

    @Test
    fun `a hint prefers a filled cell when one is available`() {
        val puzzle = Puzzle.fromRenderedRows(listOf("###", "###", "###"))
        val hint = hints.nextHint(GameState.newGame(puzzle)).shouldNotBeNull()
        puzzle.solution[hint] shouldBe true
    }

    @Test
    fun `the same board always yields the same hint`() {
        val puzzle = Puzzle.fromRenderedRows(listOf("#.#", ".#.", "#.#"))
        val state = GameState.newGame(puzzle)
        assertEquals(hints.nextHint(state), hints.nextHint(state))
        assertEquals(hints.nextHint(state), HintProvider().nextHint(state))
    }

    @Test
    fun `a hint never picks a cell the player has already decided`() {
        val puzzle = Puzzle.fromRenderedRows(listOf("##.", ".##", "#.#"))
        var state = GameState.newGame(puzzle)
        repeat(4) {
            val hint = hints.nextHint(state) ?: return@repeat
            state.board[hint] shouldBe CellState.UNKNOWN
            state = GameEngine.revealHint(state, hint)
        }
    }

    @Test
    @DisplayName("a wrongly crossed cell does not break hints")
    fun `hints survive a players wrong cross`() {
        val puzzle = Puzzle.fromRenderedRows(listOf("##.", ".##", "#.#"))
        var state = GameState.newGame(puzzle)

        // Cross a cell that should actually be filled - legal, and not a mistake.
        val wrong = puzzle.solution.indexOfFirst { it }
        state = GameEngine.toggleMode(state)
        state = GameEngine.tap(state, wrong)
        state.board[wrong] shouldBe CellState.CROSSED
        state.livesRemaining shouldBe GameState.MAX_LIVES

        // Trusting that cross makes the clues unsatisfiable, so the provider must fall
        // back to deducing from the confirmed fills alone.
        assertNotNull(hints.nextHint(state)) { "a wrong cross should not disable hints" }
    }

    @Test
    fun `a finished game offers no hint`() {
        val puzzle = Puzzle.fromRenderedRows(listOf("#.#", ".#.", "#.#"))
        var state = GameState.newGame(puzzle)
        puzzle.solution.forEachIndexed { i, filled -> if (filled) state = GameEngine.tap(state, i) }
        state.status shouldBe GameStatus.COMPLETE

        hints.nextHint(state) shouldBe null
    }

    @Test
    fun `a board with nothing left undecided offers no hint`() {
        val puzzle = Puzzle.fromRenderedRows(listOf("#.#", ".#.", "#.#"))
        val decided = GameState.newGame(puzzle).copy(
            board = puzzle.solution.map { if (it) CellState.FILLED else CellState.CROSSED },
        )
        hints.nextHint(decided) shouldBe null
    }

    @Test
    fun `no hint is offered once the lives are gone`() {
        val puzzle = Puzzle.fromRenderedRows(listOf("#.#", ".#.", "#.#"))
        var state = GameState.newGame(puzzle)
        // Three wrong fills.
        listOf(1, 3, 5).forEach { state = GameEngine.tap(state, it) }
        state.status shouldBe GameStatus.OUT_OF_LIVES
        hints.nextHint(state) shouldBe null
    }

    /**
     * Independent check that a cell really is forced right now: solve from the player's
     * board and confirm this cell came back determined.
     */
    private fun deducibleNow(state: GameState, index: Int): Boolean {
        val puzzle = state.puzzle
        val seed = Array(puzzle.cellCount) { i ->
            when (state.board[i]) {
                CellState.FILLED -> CellState.FILLED
                CellState.CROSSED -> CellState.CROSSED
                else -> CellState.UNKNOWN
            }
        }
        val run = solver.run(puzzle.width, puzzle.height, puzzle.rowClues, puzzle.colClues, seed)
        if (run.result is SolveResult.Contradiction) {
            // The board holds a wrong cross; re-check without the player's notes.
            val fillsOnly = Array(puzzle.cellCount) { i ->
                if (state.board[i] == CellState.FILLED) CellState.FILLED else CellState.UNKNOWN
            }
            val retry = solver.run(puzzle.width, puzzle.height, puzzle.rowClues, puzzle.colClues, fillsOnly)
            return retry.board[index].isKnown
        }
        return run.board[index].isKnown
    }
}
