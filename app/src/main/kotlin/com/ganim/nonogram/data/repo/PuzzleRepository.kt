package com.ganim.nonogram.data.repo

import com.ganim.nonogram.daily.DailySchedule
import com.ganim.nonogram.daily.DailySelector
import com.ganim.nonogram.daily.DailySpec
import com.ganim.nonogram.data.assets.PuzzlePackLoader
import com.ganim.nonogram.puzzle.model.Difficulty
import com.ganim.nonogram.puzzle.model.Puzzle
import java.time.LocalDate

/** Which collection a puzzle comes from. */
enum class PuzzleCollection { GENERATED, PICTURE }

/** One entry in the archive listing: enough to draw a thumbnail without decoding a grid. */
data class ArchiveEntry(
    val index: Int,
    val id: String,
    val size: Int,
    val difficulty: Difficulty,
    val collection: PuzzleCollection = PuzzleCollection.GENERATED,
    /** What the finished picture shows. Empty for generated puzzles. */
    val name: String = "",
)

/**
 * Reads puzzles out of the bundled pack and answers "which puzzle is this?" questions
 * (build plan 3, `data/repo`).
 *
 * Builds one index of the whole pack on first use - 5,000 small records, a few hundred
 * kilobytes - so the archive can filter and count without decoding grids, and the daily
 * selector has a stable pool to index into. Decoding a full [Puzzle] happens only when
 * one is actually opened.
 */
class PuzzleRepository(
    private val loader: PuzzlePackLoader,
    private val pictureLoader: PuzzlePackLoader,
) {

    /**
     * The archive index, in pack order.
     *
     * Pack order is stable because the pack is a committed, deterministically generated
     * file. Every daily assignment ever made depends on that staying true.
     */
    val entries: List<ArchiveEntry> by lazy {
        loader.summaries().map { summary ->
            ArchiveEntry(
                index = summary.index,
                id = summary.id,
                size = summary.width,
                difficulty = summary.difficulty,
                collection = PuzzleCollection.GENERATED,
            )
        }
    }

    /**
     * The hand-drawn pictures, in their own pack.
     *
     * Deliberately not merged into [entries]. The daily selector draws from pools
     * filtered out of the generated pack, so folding these in would change every pool
     * size and therefore every daily ever assigned - and twenty-three drawings would be
     * used up in a month. They are a place to go, not part of the rotation.
     */
    val pictures: List<ArchiveEntry> by lazy {
        pictureLoader.summaries().map { summary ->
            ArchiveEntry(
                index = summary.index,
                id = summary.id,
                size = summary.width,
                difficulty = summary.difficulty,
                collection = PuzzleCollection.PICTURE,
                name = summary.name,
            )
        }
    }

    /** Id to pack position, so a saved session or daily record resolves in constant time. */
    private val indexById: Map<String, Int> by lazy {
        entries.associate { it.id to it.index }
    }

    private val pictureIndexById: Map<String, Int> by lazy {
        pictures.associate { it.id to it.index }
    }

    val count: Int get() = entries.size

    fun puzzleAt(index: Int): Puzzle = loader.puzzleAt(index)

    /** Resolves an id in either collection. */
    fun puzzleById(id: String): Puzzle? =
        indexById[id]?.let { loader.puzzleAt(it) }
            ?: pictureIndexById[id]?.let { pictureLoader.puzzleAt(it) }

    fun pictureAt(index: Int): Puzzle = pictureLoader.puzzleAt(index)

    val pictureCount: Int get() = pictures.size

    /**
     * Every pool, grouped once.
     *
     * The daily calendar asks which puzzle each of its 42 cells belongs to, and each of
     * those questions used to re-filter all 5,000 entries: 210,000 comparisons and 42
     * list allocations per redraw, on the main thread. Measured on a Galaxy A15 that was
     * a 300ms stall on the first visit to Daily. There are only eight pools; grouping
     * them once turns every later question into a map lookup.
     */
    private val pools: Map<Pair<Int, Difficulty>, List<ArchiveEntry>> by lazy {
        entries.groupBy { it.size to it.difficulty }
    }

    /** Archive filters (6.4): by size, difficulty, or both. Nothing is ever locked. */
    fun filter(size: Int? = null, difficulty: Difficulty? = null): List<ArchiveEntry> = when {
        size != null && difficulty != null -> pools[size to difficulty].orEmpty()
        size == null && difficulty == null -> entries
        else -> entries.filter { entry ->
            (size == null || entry.size == size) && (difficulty == null || entry.difficulty == difficulty)
        }
    }

    /** The pool the daily selector draws from for a given day. */
    fun poolFor(spec: DailySpec): List<ArchiveEntry> = filter(spec.size, spec.difficulty)

    /**
     * The puzzle for a given date (6.2).
     *
     * Deterministic: the same date yields the same puzzle on every device and every
     * fresh install, with nothing stored and nothing fetched.
     */
    fun dailyFor(date: LocalDate): Puzzle? {
        val pool = poolFor(DailySchedule.specFor(date))
        if (pool.isEmpty()) return null
        return loader.puzzleAt(pool[DailySelector.indexInPool(date, pool.size)].index)
    }

    /** The daily's id without decoding the grid, for calendar rows. */
    fun dailyIdFor(date: LocalDate): String? {
        val pool = poolFor(DailySchedule.specFor(date))
        if (pool.isEmpty()) return null
        return pool[DailySelector.indexInPool(date, pool.size)].id
    }

    /** Sizes present in the pack, for the archive filter chips. */
    val availableSizes: List<Int> by lazy { entries.map { it.size }.distinct().sorted() }
}
