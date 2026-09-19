package com.ganim.nonogram.data.assets

import android.content.res.AssetManager
import com.ganim.nonogram.puzzle.model.Difficulty
import com.ganim.nonogram.puzzle.model.Puzzle
import com.ganim.nonogram.puzzle.pack.PuzzlePack

/**
 * Reads the bundled puzzle pack out of `assets/` (build plan 3, `data/assets`).
 *
 * The whole pack is 169 KB, so it is held in memory as bytes and individual puzzles are
 * decoded on demand through [PuzzlePack.decodeAt]. Decoding all 5,000 up front would
 * mean 5,000 grids and 50,000 clue lists resident for an archive screen that shows
 * twenty at a time.
 *
 * Clues are derived on decode rather than stored, so a decoded [Puzzle] costs a little
 * CPU and no disk.
 */
class PuzzlePackLoader(
    private val assets: AssetManager,
    private val assetName: String = DEFAULT_ASSET,
) {
    private val bytes: ByteArray by lazy {
        assets.open(assetName).use { it.readBytes() }
    }

    private val header by lazy { PuzzlePack.readHeader(bytes) }

    val count: Int get() = header.count

    /** Decodes one puzzle by its position in the pack. */
    fun puzzleAt(index: Int): Puzzle = PuzzlePack.decodeAt(bytes, index)

    /**
     * Decodes the whole pack.
     *
     * Only for the archive index and tests. Everything on a hot path should use
     * [puzzleAt].
     */
    fun decodeAll(): List<Puzzle> = PuzzlePack.decodeAll(bytes)

    /**
     * Finds a puzzle by id, decoding until it matches.
     *
     * Linear, and only used when restoring a saved session at launch - once, against a
     * 5,000 entry pack. If the archive ever needs this per frame it should build an
     * id-to-index map instead.
     */
    fun findById(id: String): Puzzle? {
        for (i in 0 until count) {
            val puzzle = puzzleAt(i)
            if (puzzle.id == id) return puzzle
        }
        return null
    }

    /** Pack positions matching a size and difficulty, for the archive filters (6.4). */
    fun indicesMatching(size: Int? = null, difficulty: Difficulty? = null): List<Int> =
        (0 until count).filter { i ->
            val puzzle = puzzleAt(i)
            (size == null || puzzle.width == size) && (difficulty == null || puzzle.difficulty == difficulty)
        }

    companion object {
        const val DEFAULT_ASSET = "puzzles.bin"
    }
}
