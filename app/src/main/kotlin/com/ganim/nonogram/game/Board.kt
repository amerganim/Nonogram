package com.ganim.nonogram.game

import com.ganim.nonogram.puzzle.model.CellState

/**
 * The player's board: one byte per cell, presented as a `List<CellState>`.
 *
 * ## Why not just a List
 *
 * Every painted cell produces a new board, because the engine is a set of pure functions
 * over an immutable state. An `ArrayList<CellState>` of 400 entries is 400 object
 * references - roughly 3 KB on a 64-bit VM plus the list header - and copying it copies
 * all of them. A 20x20 board here is 400 bytes, and copying is one `System.arraycopy`
 * over primitives. That is less work per cell and, more importantly, far less garbage
 * during a drag, when boards are created faster than anywhere else in the app.
 *
 * It also makes the snapshot codec and the undo stack cheaper, and it is the natural
 * shape for the packed `boardSnapshot` blob the schema already stores.
 *
 * Extending [AbstractList] rather than wrapping one keeps every existing call site -
 * `board[i]`, `board.count { }`, `board.indices`, equality in tests - working unchanged,
 * while the storage underneath is primitive. Element-wise equality comes from
 * [AbstractList], which is what the persistence tests compare on.
 *
 * ## Mutation
 *
 * [with] copies. [mutate] copies once and applies several writes, for callers like undo
 * that reverse a whole gesture at a time.
 */
class Board private constructor(private val cells: ByteArray) : AbstractList<CellState>() {

    override val size: Int get() = cells.size

    override fun get(index: Int): CellState = STATES[cells[index].toInt()]

    /** A copy with one cell changed. Returns this board unchanged if it already matches. */
    fun with(index: Int, state: CellState): Board {
        if (cells[index].toInt() == state.ordinal) return this
        val copy = cells.copyOf()
        copy[index] = state.ordinal.toByte()
        return Board(copy)
    }

    /** One copy, then any number of writes. */
    fun mutate(block: Mutator.() -> Unit): Board {
        val copy = cells.copyOf()
        Mutator(copy).block()
        return Board(copy)
    }

    /** How many cells hold [state]. Counted over the bytes, without an iterator. */
    fun countOf(state: CellState): Int {
        val wanted = state.ordinal.toByte()
        var count = 0
        for (cell in cells) if (cell == wanted) count++
        return count
    }

    /** The raw bytes, for the snapshot codec. Copied, so callers cannot reach inside. */
    fun toByteArray(): ByteArray = cells.copyOf()

    class Mutator internal constructor(private val cells: ByteArray) {
        operator fun set(index: Int, state: CellState) {
            cells[index] = state.ordinal.toByte()
        }

        operator fun get(index: Int): CellState = STATES[cells[index].toInt()]
    }

    companion object {
        /**
         * Indexed by ordinal, so a cell is one array lookup rather than a `when`.
         *
         * The on-disk snapshot does not use these ordinals - it writes explicit codes -
         * so reordering [CellState] cannot silently reinterpret a saved game.
         */
        private val STATES = CellState.entries.toTypedArray()

        fun blank(size: Int): Board = Board(ByteArray(size) { CellState.UNKNOWN.ordinal.toByte() })

        fun of(states: List<CellState>): Board =
            Board(ByteArray(states.size) { states[it].ordinal.toByte() })

        fun of(size: Int, stateAt: (Int) -> CellState): Board =
            Board(ByteArray(size) { stateAt(it).ordinal.toByte() })
    }
}
