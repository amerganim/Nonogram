package com.ganim.nonogram.data.session

import com.ganim.nonogram.game.CellChange
import com.ganim.nonogram.game.GameState
import com.ganim.nonogram.game.GameStatus
import com.ganim.nonogram.game.PaintMode
import com.ganim.nonogram.game.UndoEntry
import com.ganim.nonogram.puzzle.model.CellState
import com.ganim.nonogram.puzzle.model.Grid
import com.ganim.nonogram.puzzle.model.Puzzle
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * Packs a puzzle in progress into the `boardSnapshot` blob of build plan 6.5.
 *
 * > "`boardSnapshot` is a packed bitset of in-progress state, same encoding as the
 * > puzzle pack."
 *
 * The board is three bitsets in the pack's own encoding - what is filled, what is
 * crossed, and which cells are revealed mistakes - followed by the counters and the undo
 * stack. Two bits per cell would be tighter, but three parallel bitsets reuse
 * [Grid.toBitset] exactly and a 20x20 snapshot is 150 bytes either way.
 *
 * The puzzle's own grid is never stored. It already ships in the pack, and writing it
 * twice would let the two disagree after a pack regeneration. Only the id goes in, and
 * it is checked on the way out.
 *
 * A snapshot that cannot be read is treated as "no saved game" rather than throwing.
 * Losing one puzzle is bad; crashing on every launch is worse.
 */
object BoardSnapshotCodec {

    fun encode(state: GameState): ByteArray {
        val bytes = ByteArrayOutputStream(256)
        DataOutputStream(bytes).use { out ->
            out.writeInt(MAGIC)
            out.writeShort(VERSION)

            out.writeUTF(state.puzzle.id)
            out.writeByte(state.puzzle.width)
            out.writeByte(state.puzzle.height)

            out.writeByte(state.livesRemaining)
            out.writeByte(state.paintMode.ordinal)
            out.writeByte(state.status.ordinal)
            out.writeShort(state.hintsUsed)
            out.writeLong(state.elapsedMs)

            val n = state.puzzle.cellCount
            out.write(Grid.toBitset(BooleanArray(n) { state.board[it] == CellState.FILLED }))
            out.write(Grid.toBitset(BooleanArray(n) { state.board[it] == CellState.CROSSED }))
            out.write(Grid.toBitset(BooleanArray(n) { it in state.mistakeCells }))

            out.writeShort(state.undoStack.size)
            state.undoStack.forEach { entry ->
                out.writeShort(entry.changes.size)
                entry.changes.forEach { change ->
                    out.writeShort(change.index)
                    out.writeByte(change.from.ordinal)
                    out.writeByte(change.to.ordinal)
                }
            }
        }
        return bytes.toByteArray()
    }

    /**
     * Restores a snapshot against the puzzle it belongs to.
     *
     * Returns null when the blob is damaged, was written by another format version, or
     * does not describe [puzzle].
     */
    fun decode(bytes: ByteArray, puzzle: Puzzle): GameState? = try {
        DataInputStream(ByteArrayInputStream(bytes)).use { input -> read(input, puzzle) }
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: IndexOutOfBoundsException) {
        null
    }

    /** The puzzle id a snapshot refers to, without decoding the rest of it. */
    fun puzzleIdOf(bytes: ByteArray): String? = try {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            if (input.readInt() != MAGIC) null
            else if (input.readUnsignedShort() != VERSION) null
            else input.readUTF()
        }
    } catch (_: IOException) {
        null
    }

    private fun read(input: DataInputStream, puzzle: Puzzle): GameState? {
        if (input.readInt() != MAGIC) return null
        if (input.readUnsignedShort() != VERSION) return null

        if (input.readUTF() != puzzle.id) return null
        val width = input.readUnsignedByte()
        val height = input.readUnsignedByte()
        // Guards against a pack regeneration that changed a puzzle under a stale save.
        if (puzzle.width != width || puzzle.height != height) return null

        val lives = input.readUnsignedByte()
        val paintMode = PaintMode.entries.getOrNull(input.readUnsignedByte()) ?: return null
        val status = GameStatus.entries.getOrNull(input.readUnsignedByte()) ?: return null
        val hintsUsed = input.readUnsignedShort()
        val elapsedMs = input.readLong()

        val n = puzzle.cellCount
        val filled = input.readBitset(n)
        val crossed = input.readBitset(n)
        val mistakes = input.readBitset(n)

        val board = List(n) { i ->
            when {
                filled[i] -> CellState.FILLED
                crossed[i] -> CellState.CROSSED
                else -> CellState.UNKNOWN
            }
        }

        val undoCount = input.readUnsignedShort()
        val undoStack = ArrayList<UndoEntry>(undoCount)
        repeat(undoCount) {
            val changeCount = input.readUnsignedShort()
            val changes = ArrayList<CellChange>(changeCount)
            repeat(changeCount) {
                val index = input.readUnsignedShort()
                val from = CellState.entries.getOrNull(input.readUnsignedByte()) ?: return null
                val to = CellState.entries.getOrNull(input.readUnsignedByte()) ?: return null
                if (index !in 0 until n) return null
                changes.add(CellChange(index, from, to))
            }
            undoStack.add(UndoEntry(changes))
        }

        return GameState(
            puzzle = puzzle,
            board = board,
            mistakeCells = (0 until n).filter { mistakes[it] }.toSet(),
            livesRemaining = lives,
            hintsUsed = hintsUsed,
            elapsedMs = elapsedMs,
            paintMode = paintMode,
            status = status,
            undoStack = undoStack,
            openGesture = null,
        )
    }

    private fun DataInputStream.readBitset(cellCount: Int): BooleanArray {
        val bytes = ByteArray((cellCount + 7) / 8)
        readFully(bytes)
        return Grid.fromBitset(bytes, cellCount)
    }

    /** "NNGS" - nonogram game snapshot. */
    private const val MAGIC = 0x4E_4E_47_53
    private const val VERSION = 1
}
