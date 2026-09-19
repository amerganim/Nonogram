package com.ganim.nonogram.data.session

import com.ganim.nonogram.game.GameEngine
import com.ganim.nonogram.game.GameState
import com.ganim.nonogram.game.GameStatus
import com.ganim.nonogram.game.PaintMode
import com.ganim.nonogram.puzzle.model.CellState
import com.ganim.nonogram.puzzle.model.Puzzle
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Build plan 5.3 and 6.5: the `boardSnapshot` blob must bring a puzzle back exactly as
 * it was left - board, timer, lives and undo.
 */
class BoardSnapshotCodecTest {

    private val puzzle = Puzzle.fromRenderedRows(listOf("##.#", ".##.", "#..#", "####"))
    private val otherPuzzle = Puzzle.fromRenderedRows(listOf("#.#", ".#.", "#.#"))

    /** A game part-way through, with fills, a cross, a mistake, a hint and elapsed time. */
    private fun midGame(): GameState {
        var state = GameState.newGame(puzzle)
        state = GameEngine.tick(state, 42_000)

        // A drag across the bottom row, as one undo step.
        state = GameEngine.beginGesture(state)
        (12..15).forEach { state = GameEngine.paint(state, it, CellState.FILLED) }
        state = GameEngine.endGesture(state)

        state = GameEngine.toggleMode(state)
        state = GameEngine.tap(state, 4)
        state = GameEngine.toggleMode(state)
        state = GameEngine.revealHint(state, 0)
        state = GameEngine.tap(state, 2) // empty in the solution, so a mistake

        return state
    }

    @Test
    @DisplayName("a mid-puzzle snapshot restores board, timer, lives and undo exactly")
    fun `round trip preserves the whole session`() {
        val original = midGame()
        val restored = BoardSnapshotCodec.decode(BoardSnapshotCodec.encode(original), puzzle)
            .shouldNotBeNull()

        restored.board shouldBe original.board
        restored.elapsedMs shouldBe original.elapsedMs
        restored.livesRemaining shouldBe original.livesRemaining
        restored.mistakeCells shouldBe original.mistakeCells
        restored.hintsUsed shouldBe original.hintsUsed
        restored.paintMode shouldBe original.paintMode
        restored.status shouldBe original.status
        restored.undoStack shouldBe original.undoStack
        restored.puzzle shouldBe original.puzzle
    }

    @Test
    fun `undo still works after a restore`() {
        val original = midGame()
        val restored = BoardSnapshotCodec.decode(BoardSnapshotCodec.encode(original), puzzle)
            .shouldNotBeNull()

        restored.canUndo shouldBe true
        GameEngine.undo(restored).board shouldBe GameEngine.undo(original).board
    }

    @Test
    fun `a drag saved as one gesture still undoes as one step`() {
        var restored = BoardSnapshotCodec.decode(BoardSnapshotCodec.encode(midGame()), puzzle)
            .shouldNotBeNull()

        // Unwind the hint, then the cross, then the four-cell drag.
        repeat(3) { restored = GameEngine.undo(restored) }
        (12..15).forEach { restored.board[it] shouldBe CellState.UNKNOWN }
    }

    @Test
    fun `a fresh game round trips`() {
        val fresh = GameState.newGame(puzzle)
        val restored = BoardSnapshotCodec.decode(BoardSnapshotCodec.encode(fresh), puzzle)
            .shouldNotBeNull()
        restored.board shouldBe fresh.board
        restored.canUndo shouldBe false
        restored.paintMode shouldBe PaintMode.FILL
        restored.status shouldBe GameStatus.PLAYING
    }

    @Test
    fun `an out of lives snapshot restores as out of lives`() {
        var state = GameState.newGame(puzzle)
        // Cells 2, 4 and 7 are empty in this solution.
        listOf(2, 4, 7).forEach { state = GameEngine.tap(state, it) }
        state.status shouldBe GameStatus.OUT_OF_LIVES

        val restored = BoardSnapshotCodec.decode(BoardSnapshotCodec.encode(state), puzzle)
            .shouldNotBeNull()
        restored.status shouldBe GameStatus.OUT_OF_LIVES
        restored.livesRemaining shouldBe 0
    }

    @Test
    @DisplayName("a truncated blob reads as no snapshot rather than crashing the launch")
    fun `a truncated blob is survivable`() {
        val bytes = BoardSnapshotCodec.encode(midGame())
        BoardSnapshotCodec.decode(bytes.copyOf(bytes.size / 2), puzzle).shouldBeNull()
    }

    @Test
    fun `random bytes read as no snapshot`() {
        BoardSnapshotCodec.decode(ByteArray(128) { (it * 7).toByte() }, puzzle).shouldBeNull()
    }

    @Test
    fun `an empty blob reads as no snapshot`() {
        BoardSnapshotCodec.decode(ByteArray(0), puzzle).shouldBeNull()
    }

    @Test
    @DisplayName("a snapshot is refused against the wrong puzzle")
    fun `a mismatched puzzle is refused`() {
        val bytes = BoardSnapshotCodec.encode(midGame())
        BoardSnapshotCodec.decode(bytes, otherPuzzle).shouldBeNull()
    }

    @Test
    fun `the puzzle id can be read without decoding the board`() {
        val bytes = BoardSnapshotCodec.encode(midGame())
        BoardSnapshotCodec.puzzleIdOf(bytes) shouldBe puzzle.id
        BoardSnapshotCodec.puzzleIdOf(ByteArray(4)).shouldBeNull()
    }

    @Test
    fun `a snapshot stays small enough to store per puzzle`() {
        val big = Puzzle.fromRenderedRows(List(20) { "#".repeat(20) })
        var state = GameState.newGame(big)
        state = GameEngine.beginGesture(state)
        (0 until 400).forEach { state = GameEngine.paint(state, it, CellState.FILLED) }
        state = GameEngine.endGesture(state)

        val size = BoardSnapshotCodec.encode(state).size
        assertTrue(size < 2_500) { "a full 20x20 snapshot with undo history is $size bytes" }
    }
}
