package com.ganim.nonogram.game

import com.ganim.nonogram.puzzle.model.CellState
import com.ganim.nonogram.puzzle.model.Puzzle
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Build plan 5.3 rules and the 5.2 undo requirement.
 *
 * Acceptance: "Undo correctly reverses every action type including hint reveals."
 */
class GameEngineTest {

    /**
     * ```
     * # . #
     * . # .
     * # . #
     * ```
     */
    private val puzzle = Puzzle.fromRenderedRows(listOf("#.#", ".#.", "#.#"))

    private fun newGame() = GameState.newGame(puzzle)

    private fun idx(row: Int, col: Int) = row * puzzle.width + col

    // --- painting --------------------------------------------------------------------

    @Test
    fun `filling a correct cell costs nothing`() {
        val state = GameEngine.tap(newGame(), idx(0, 0))
        state.board[idx(0, 0)] shouldBe CellState.FILLED
        state.livesRemaining shouldBe GameState.MAX_LIVES
        state.mistakeCells shouldBe emptySet()
    }

    @Test
    fun `tapping a filled cell in fill mode clears it`() {
        var state = GameEngine.tap(newGame(), idx(0, 0))
        state = GameEngine.tap(state, idx(0, 0))
        state.board[idx(0, 0)] shouldBe CellState.UNKNOWN
    }

    @Test
    fun `crossing is never a mistake even on a cell that should be filled`() {
        val crossMode = GameEngine.toggleMode(newGame())
        val state = GameEngine.tap(crossMode, idx(0, 0)) // (0,0) is filled in the solution
        state.board[idx(0, 0)] shouldBe CellState.CROSSED
        state.livesRemaining shouldBe GameState.MAX_LIVES
        state.mistakeCells shouldBe emptySet()
    }

    @Test
    fun `a tap never converts a fill straight into a cross`() {
        var state = GameEngine.tap(newGame(), idx(0, 0))
        state.board[idx(0, 0)] shouldBe CellState.FILLED
        state = GameEngine.toggleMode(state)
        state = GameEngine.tap(state, idx(0, 0))
        state.board[idx(0, 0)] shouldBe CellState.CROSSED
    }

    // --- mistakes --------------------------------------------------------------------

    @Test
    @DisplayName("a wrong fill costs a life and reveals the cell as crossed")
    fun `wrong fill costs a life`() {
        val state = GameEngine.tap(newGame(), idx(0, 1)) // empty in the solution
        state.livesRemaining shouldBe GameState.MAX_LIVES - 1
        state.board[idx(0, 1)] shouldBe CellState.CROSSED
        state.mistakeCells shouldBe setOf(idx(0, 1))
    }

    @Test
    fun `a revealed mistake cannot be repainted`() {
        var state = GameEngine.tap(newGame(), idx(0, 1))
        state = GameEngine.tap(state, idx(0, 1))
        state.board[idx(0, 1)] shouldBe CellState.CROSSED
        state.livesRemaining shouldBe GameState.MAX_LIVES - 1
    }

    @Test
    fun `three mistakes ends the game`() {
        var state = newGame()
        listOf(idx(0, 1), idx(1, 0), idx(1, 2)).forEach { state = GameEngine.tap(state, it) }
        state.livesRemaining shouldBe 0
        state.status shouldBe GameStatus.OUT_OF_LIVES
    }

    @Test
    fun `no painting is possible once out of lives`() {
        var state = newGame()
        listOf(idx(0, 1), idx(1, 0), idx(1, 2)).forEach { state = GameEngine.tap(state, it) }
        val frozen = GameEngine.tap(state, idx(0, 0))
        frozen.board[idx(0, 0)] shouldBe CellState.UNKNOWN
    }

    @Test
    fun `restoring a life puts the player back in play`() {
        var state = newGame()
        listOf(idx(0, 1), idx(1, 0), idx(1, 2)).forEach { state = GameEngine.tap(state, it) }
        state = GameEngine.restoreLife(state)
        state.status shouldBe GameStatus.PLAYING
        state.livesRemaining shouldBe 1
        GameEngine.tap(state, idx(0, 0)).board[idx(0, 0)] shouldBe CellState.FILLED
    }

    @Test
    @DisplayName("a wrong cell mid-drag stops the drag instead of burning every life")
    fun `a mistake ends the gesture`() {
        var state = GameEngine.beginGesture(newGame())
        state = GameEngine.paint(state, idx(0, 0), CellState.FILLED)  // correct
        state = GameEngine.paint(state, idx(0, 1), CellState.FILLED)  // wrong
        state.openGesture shouldBe null
        state.dragBlocked shouldBe true
        state.livesRemaining shouldBe GameState.MAX_LIVES - 1

        // The cells painted before the mistake are still undoable as one step.
        state.undoStack.size shouldBe 1
        state.undoStack.last().changes.map { it.index } shouldBe listOf(idx(0, 0))
    }

    @Test
    @DisplayName("one careless sweep costs one life, not three")
    fun `a drag across several wrong cells costs a single life`() {
        // Found on a real device: dragging across row 0 of a puzzle whose first row is
        // mostly empty ended the game outright. Clearing the gesture marker was not
        // enough, because the drag kept reporting cells and paint kept accepting them.
        var state = GameEngine.beginGesture(newGame())
        // (0,1) is empty in the solution; so are (1,0) and (1,2).
        listOf(idx(0, 1), idx(1, 0), idx(1, 2)).forEach { cell ->
            state = GameEngine.paint(state, cell, CellState.FILLED)
        }
        state = GameEngine.endGesture(state)

        state.livesRemaining shouldBe GameState.MAX_LIVES - 1
        state.mistakeCells shouldBe setOf(idx(0, 1))
        state.status shouldBe GameStatus.PLAYING
    }

    @Test
    fun `the next gesture after a blocked one paints normally`() {
        var state = GameEngine.beginGesture(newGame())
        state = GameEngine.paint(state, idx(0, 1), CellState.FILLED) // wrong, blocks
        state = GameEngine.endGesture(state)
        state.dragBlocked shouldBe false

        state = GameEngine.beginGesture(state)
        state = GameEngine.paint(state, idx(0, 0), CellState.FILLED) // correct
        state = GameEngine.endGesture(state)
        state.board[idx(0, 0)] shouldBe CellState.FILLED
        state.livesRemaining shouldBe GameState.MAX_LIVES - 1
    }

    @Test
    fun `three separate wrong taps still end the game`() {
        // The block is per-gesture, so deliberate repeated mistakes must still cost.
        var state = newGame()
        listOf(idx(0, 1), idx(1, 0), idx(1, 2)).forEach { state = GameEngine.tap(state, it) }
        state.livesRemaining shouldBe 0
        state.status shouldBe GameStatus.OUT_OF_LIVES
    }

    // --- gestures and undo -----------------------------------------------------------

    @Test
    @DisplayName("a drag across several cells undoes as a single step")
    fun `a drag is one undo step`() {
        var state = GameEngine.beginGesture(newGame())
        state = GameEngine.paint(state, idx(0, 0), CellState.FILLED)
        state = GameEngine.paint(state, idx(2, 0), CellState.FILLED)
        state = GameEngine.paint(state, idx(2, 2), CellState.FILLED)
        state = GameEngine.endGesture(state)

        state.undoStack.size shouldBe 1
        val undone = GameEngine.undo(state)
        undone.board.count { it == CellState.FILLED } shouldBe 0
        undone.canUndo shouldBe false
    }

    @Test
    fun `a gesture that changed nothing is not pushed`() {
        val state = GameEngine.endGesture(GameEngine.beginGesture(newGame()))
        state.canUndo shouldBe false
        state.undoStack shouldBe emptyList()
    }

    @Test
    fun `repainting the same value does not record a change`() {
        var state = GameEngine.beginGesture(newGame())
        state = GameEngine.paint(state, idx(0, 0), CellState.FILLED)
        state = GameEngine.paint(state, idx(0, 0), CellState.FILLED)
        state = GameEngine.endGesture(state)
        state.undoStack.single().changes.size shouldBe 1
    }

    @Test
    fun `undo reverses fills, crosses and clears alike`() {
        var state = newGame()
        state = GameEngine.tap(state, idx(0, 0))                       // fill
        state = GameEngine.tap(GameEngine.toggleMode(state), idx(1, 0)) // cross
        state = GameEngine.tap(GameEngine.toggleMode(state), idx(0, 0)) // clear the fill

        state.board[idx(0, 0)] shouldBe CellState.UNKNOWN
        state = GameEngine.undo(state)
        state.board[idx(0, 0)] shouldBe CellState.FILLED
        state = GameEngine.undo(state)
        state.board[idx(1, 0)] shouldBe CellState.UNKNOWN
        state = GameEngine.undo(state)
        state.board[idx(0, 0)] shouldBe CellState.UNKNOWN
        state.canUndo shouldBe false
    }

    @Test
    fun `undo reverses a hint reveal`() {
        val state = GameEngine.revealHint(newGame(), idx(0, 0))
        state.board[idx(0, 0)] shouldBe CellState.FILLED
        state.hintsUsed shouldBe 1

        val undone = GameEngine.undo(state)
        undone.board[idx(0, 0)] shouldBe CellState.UNKNOWN
    }

    @Test
    @DisplayName("undo does not refund a spent life - otherwise lives mean nothing")
    fun `undo does not refund lives`() {
        var state = GameEngine.tap(newGame(), idx(0, 1))
        state.livesRemaining shouldBe GameState.MAX_LIVES - 1

        state = GameEngine.undo(state)
        state.livesRemaining shouldBe GameState.MAX_LIVES - 1
        state.mistakeCells shouldBe setOf(idx(0, 1))
        state.board[idx(0, 1)] shouldBe CellState.CROSSED
    }

    @Test
    fun `undoing on an empty stack is a no-op`() {
        val state = newGame()
        GameEngine.undo(state) shouldBe state
    }

    @Test
    fun `the undo stack is bounded but holds well over the required 50 steps`() {
        assertTrue(GameState.UNDO_LIMIT >= 50) { "build plan 5.2 requires at least 50 undo steps" }

        var state = newGame()
        // Toggle one correct cell back and forth far past the limit.
        repeat(GameState.UNDO_LIMIT * 2 + 10) {
            state = GameEngine.tap(state, idx(0, 0))
        }
        state.undoStack.size shouldBe GameState.UNDO_LIMIT
    }

    @Test
    fun `undo unwinds a gesture that touched one cell twice`() {
        var state = GameEngine.beginGesture(newGame())
        state = GameEngine.paint(state, idx(0, 0), CellState.FILLED)
        state = GameEngine.paint(state, idx(0, 0), CellState.UNKNOWN)
        state = GameEngine.paint(state, idx(0, 0), CellState.FILLED)
        state = GameEngine.endGesture(state)

        GameEngine.undo(state).board[idx(0, 0)] shouldBe CellState.UNKNOWN
    }

    // --- drag targets ----------------------------------------------------------------

    @Test
    @DisplayName("the first cell of a drag locks what the whole drag writes")
    fun `drag target is decided by the starting cell`() {
        val blank = newGame()
        GameEngine.dragTargetFor(blank, idx(0, 0)) shouldBe CellState.FILLED

        val filled = GameEngine.tap(blank, idx(0, 0))
        GameEngine.dragTargetFor(filled, idx(0, 0)) shouldBe CellState.UNKNOWN
    }

    @Test
    fun `long press paints the opposite mode without switching mode`() {
        val state = newGame()
        GameEngine.dragTargetFor(state, idx(0, 0), overrideMode = PaintMode.CROSS) shouldBe CellState.CROSSED
        state.paintMode shouldBe PaintMode.FILL
    }

    // --- completion ------------------------------------------------------------------

    @Test
    fun `filling exactly the solution completes the puzzle`() {
        var state = newGame()
        puzzle.solution.forEachIndexed { i, filled ->
            if (filled) state = GameEngine.tap(state, i)
        }
        state.status shouldBe GameStatus.COMPLETE
    }

    @Test
    fun `crosses are not required for completion`() {
        var state = newGame()
        state = GameEngine.tap(GameEngine.toggleMode(state), idx(0, 1)) // an optional note
        state = GameEngine.toggleMode(state)
        puzzle.solution.forEachIndexed { i, filled ->
            if (filled) state = GameEngine.tap(state, i)
        }
        state.status shouldBe GameStatus.COMPLETE
    }

    @Test
    fun `undoing out of a completed board resumes play`() {
        var state = newGame()
        puzzle.solution.forEachIndexed { i, filled ->
            if (filled) state = GameEngine.tap(state, i)
        }
        state.status shouldBe GameStatus.COMPLETE
        GameEngine.undo(state).status shouldBe GameStatus.PLAYING
    }

    // --- timer -----------------------------------------------------------------------

    @Test
    fun `the timer runs while playing and stops when the puzzle ends`() {
        var state = GameEngine.tick(newGame(), 1_000)
        state.elapsedMs shouldBe 1_000

        puzzle.solution.forEachIndexed { i, filled ->
            if (filled) state = GameEngine.tap(state, i)
        }
        val afterWin = GameEngine.tick(state, 5_000)
        afterWin.elapsedMs shouldBe state.elapsedMs
    }
}
