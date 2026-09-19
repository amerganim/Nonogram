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
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Build plan 5.3 and its acceptance criterion: "Kill the app mid-puzzle; relaunch
 * restores exact board state, timer, lives, and undo availability."
 */
class GameSessionStoreTest {

    @TempDir
    lateinit var dir: File

    private val puzzle = Puzzle.fromRenderedRows(listOf("##.#", ".##.", "#..#", "####"))

    private fun store() = GameSessionStore(File(dir, GameSessionStore.DEFAULT_FILE_NAME))

    private fun lookup(id: String): Puzzle? = if (id == puzzle.id) puzzle else null

    /** A game part-way through, with fills, a cross, a mistake, a hint and elapsed time. */
    private fun midGame(): GameState {
        var state = GameState.newGame(puzzle)
        state = GameEngine.tick(state, 42_000)

        // A drag across the bottom row, as one undo step.
        state = GameEngine.beginGesture(state)
        (12..15).forEach { state = GameEngine.paint(state, it, CellState.FILLED) }
        state = GameEngine.endGesture(state)

        // A note, a hint and a wrong fill.
        state = GameEngine.toggleMode(state)
        state = GameEngine.tap(state, 4)
        state = GameEngine.toggleMode(state)
        state = GameEngine.revealHint(state, 0)
        state = GameEngine.tap(state, 2) // empty in the solution, so a mistake

        return state
    }

    @Test
    @DisplayName("a mid-puzzle save restores board, timer, lives and undo exactly")
    fun `round trip preserves the whole session`() {
        val original = midGame()
        store().save(original)

        val restored = store().load(::lookup).shouldNotBeNull()

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
        store().save(original)
        val restored = store().load(::lookup).shouldNotBeNull()

        restored.canUndo shouldBe true
        val undoneFromRestore = GameEngine.undo(restored)
        val undoneFromOriginal = GameEngine.undo(original)
        undoneFromRestore.board shouldBe undoneFromOriginal.board
    }

    @Test
    fun `a drag saved as one gesture still undoes as one step`() {
        val original = midGame()
        store().save(original)
        var restored = store().load(::lookup).shouldNotBeNull()

        // Unwind the hint, then the cross, then the four-cell drag.
        repeat(3) { restored = GameEngine.undo(restored) }
        (12..15).forEach { restored.board[it] shouldBe CellState.UNKNOWN }
    }

    @Test
    fun `a fresh game round trips`() {
        val fresh = GameState.newGame(puzzle)
        store().save(fresh)
        val restored = store().load(::lookup).shouldNotBeNull()
        restored.board shouldBe fresh.board
        restored.canUndo shouldBe false
        restored.paintMode shouldBe PaintMode.FILL
        restored.status shouldBe GameStatus.PLAYING
    }

    @Test
    fun `no saved session reads as null`() {
        store().load(::lookup).shouldBeNull()
        store().hasSession shouldBe false
    }

    @Test
    fun `clearing removes the session`() {
        store().save(midGame())
        store().hasSession shouldBe true
        store().clear()
        store().hasSession shouldBe false
        store().load(::lookup).shouldBeNull()
    }

    @Test
    @DisplayName("a damaged file reads as no session rather than crashing the launch")
    fun `a corrupt file is survivable`() {
        val file = File(dir, GameSessionStore.DEFAULT_FILE_NAME)
        GameSessionStore(file).save(midGame())

        // Chop the file in half, as an interrupted write would.
        val bytes = file.readBytes()
        file.writeBytes(bytes.copyOf(bytes.size / 2))

        GameSessionStore(file).load(::lookup).shouldBeNull()
    }

    @Test
    fun `random bytes read as no session`() {
        val file = File(dir, GameSessionStore.DEFAULT_FILE_NAME)
        file.writeBytes(ByteArray(128) { (it * 7).toByte() })
        GameSessionStore(file).load(::lookup).shouldBeNull()
    }

    @Test
    @DisplayName("a session whose puzzle is no longer in the pack is discarded")
    fun `an unknown puzzle id reads as null`() {
        store().save(midGame())
        store().load { null }.shouldBeNull()
    }

    @Test
    fun `saving twice replaces rather than appends`() {
        val first = midGame()
        store().save(first)
        val sizeAfterFirst = File(dir, GameSessionStore.DEFAULT_FILE_NAME).length()

        store().save(first)
        File(dir, GameSessionStore.DEFAULT_FILE_NAME).length() shouldBe sizeAfterFirst
    }

    @Test
    fun `no temp file is left behind`() {
        store().save(midGame())
        dir.listFiles()?.none { it.name.endsWith(".tmp") } shouldBe true
    }

    @Test
    fun `an out of lives session restores as out of lives`() {
        var state = GameState.newGame(puzzle)
        // Cells 2, 4 and 7 are empty in this solution.
        listOf(2, 4, 7).forEach { state = GameEngine.tap(state, it) }
        state.status shouldBe GameStatus.OUT_OF_LIVES

        store().save(state)
        val restored = store().load(::lookup).shouldNotBeNull()
        restored.status shouldBe GameStatus.OUT_OF_LIVES
        restored.livesRemaining shouldBe 0
    }
}
