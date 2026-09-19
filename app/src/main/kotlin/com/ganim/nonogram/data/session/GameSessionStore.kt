package com.ganim.nonogram.data.session

import com.ganim.nonogram.game.CellChange
import com.ganim.nonogram.game.GameState
import com.ganim.nonogram.game.GameStatus
import com.ganim.nonogram.game.PaintMode
import com.ganim.nonogram.game.UndoEntry
import com.ganim.nonogram.puzzle.model.CellState
import com.ganim.nonogram.puzzle.model.Grid
import com.ganim.nonogram.puzzle.model.Puzzle
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException

/**
 * Persists the puzzle in progress (build plan 5.3: "Auto-save on every state change. The
 * app must survive being killed mid-puzzle with zero loss").
 *
 * ## Why a file and not Room yet
 *
 * The plan puts Room in Phase 3 (6.5), but the Phase 2 acceptance criterion already
 * requires surviving a kill. This is the smallest thing that satisfies it without
 * pulling Phase 3 forward, and it is not throwaway work: the board is packed with the
 * same bitset encoding 6.5 specifies for `PuzzleProgress.boardSnapshot`, so moving it
 * into Room later is a change of container, not of format.
 *
 * ## Why the write is atomic
 *
 * "Survive being killed" includes being killed *during the save*. Writing in place
 * would leave a half-written file and lose the puzzle - the exact failure the criterion
 * exists to prevent. So each save goes to a temp file and is then renamed over the real
 * one, which is atomic on every filesystem Android uses. A crash mid-write leaves the
 * previous save intact.
 *
 * A corrupt or unreadable file is treated as "no saved game" rather than thrown: losing
 * one puzzle is bad, but crashing on launch every time is worse.
 */
class GameSessionStore(private val file: File) {

    /** Writes [state] to disk, replacing any previous session. */
    fun save(state: GameState) {
        val temp = File(file.parentFile, file.name + ".tmp")
        try {
            temp.parentFile?.mkdirs()
            DataOutputStream(temp.outputStream().buffered()).use { out -> write(out, state) }
            if (!temp.renameTo(file)) {
                // renameTo can fail when the destination exists on some filesystems.
                file.delete()
                if (!temp.renameTo(file)) throw IOException("Could not move session into place")
            }
        } catch (e: IOException) {
            temp.delete()
            throw e
        }
    }

    /**
     * Reads back a saved session.
     *
     * [findPuzzle] resolves the stored puzzle id against the pack; the grid itself is
     * never saved, because it already ships in the pack and storing it again would let
     * the two disagree. Returns null when there is no session, when it was written by a
     * different format version, when the puzzle is not in the pack, or when the file is
     * damaged.
     */
    fun load(findPuzzle: (String) -> Puzzle?): GameState? = try {
        if (!file.isFile) null else DataInputStream(file.inputStream().buffered()).use { read(it, findPuzzle) }
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: IndexOutOfBoundsException) {
        null
    }

    fun clear() {
        file.delete()
    }

    val hasSession: Boolean get() = file.isFile

    // --- format ----------------------------------------------------------------------

    private fun write(out: DataOutputStream, state: GameState) {
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

        // Three bitsets in the pack's own encoding: what is filled, what is crossed,
        // and which cells are revealed mistakes.
        val n = state.puzzle.cellCount
        out.writeBitset(BooleanArray(n) { state.board[it] == CellState.FILLED })
        out.writeBitset(BooleanArray(n) { state.board[it] == CellState.CROSSED })
        out.writeBitset(BooleanArray(n) { it in state.mistakeCells })

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

    private fun read(input: DataInputStream, findPuzzle: (String) -> Puzzle?): GameState? {
        if (input.readInt() != MAGIC) return null
        if (input.readUnsignedShort() != VERSION) return null

        val puzzleId = input.readUTF()
        val width = input.readUnsignedByte()
        val height = input.readUnsignedByte()

        val puzzle = findPuzzle(puzzleId) ?: return null
        // Guard against a pack regeneration that changed a puzzle under a stale save.
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

    private fun DataOutputStream.writeBitset(cells: BooleanArray) {
        write(Grid.toBitset(cells))
    }

    private fun DataInputStream.readBitset(cellCount: Int): BooleanArray {
        val bytes = ByteArray((cellCount + 7) / 8)
        readFully(bytes)
        return Grid.fromBitset(bytes, cellCount)
    }

    companion object {
        /** "NNGS" - nonogram game session. */
        private const val MAGIC = 0x4E_4E_47_53
        private const val VERSION = 1

        const val DEFAULT_FILE_NAME = "current-session.bin"
    }
}
