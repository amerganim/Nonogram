package com.ganim.nonogram.data.repo

import com.ganim.nonogram.daily.DailySchedule
import com.ganim.nonogram.puzzle.model.Difficulty
import com.ganim.nonogram.puzzle.pack.PuzzlePack
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File
import java.time.LocalDate

/**
 * Checks the archive index and the daily selector against the real shipped pack.
 *
 * Runs against the pack bytes directly rather than through `PuzzlePackLoader`, which
 * needs an Android `AssetManager`.
 */
class PuzzleRepositoryTest {

    private val packBytes: ByteArray? = listOf(
        "src/main/assets/puzzles.bin",
        "app/src/main/assets/puzzles.bin",
    ).map(::File).firstOrNull { it.isFile }?.readBytes()

    @Test
    @DisplayName("the fast summary index agrees exactly with full decoding")
    fun `summaries match decoded puzzles`() {
        assumeTrue(packBytes != null) { "puzzles.bin not found - run 'gradlew generatePuzzlePack'" }
        val bytes = packBytes!!

        val summaries = PuzzlePack.summaries(bytes)
        val decoded = PuzzlePack.decodeAll(bytes)

        summaries.size shouldBe decoded.size
        summaries.forEachIndexed { i, summary ->
            val puzzle = decoded[i]
            assertEquals(puzzle.id, summary.id, "id disagrees at index $i")
            assertEquals(puzzle.width, summary.width, "width disagrees at index $i")
            assertEquals(puzzle.height, summary.height, "height disagrees at index $i")
            assertEquals(puzzle.difficulty, summary.difficulty, "difficulty disagrees at index $i")
            assertEquals(puzzle.solveDepth, summary.solveDepth, "solveDepth disagrees at index $i")
            assertEquals(i, summary.index)
        }
    }

    @Test
    fun `summaries are unique and cover the whole pack`() {
        assumeTrue(packBytes != null)
        val summaries = PuzzlePack.summaries(packBytes!!)
        summaries.size shouldBe 5_000
        summaries.map { it.id }.toSet().size shouldBe summaries.size
    }

    @Test
    @DisplayName("every day of the rotation has a non-empty pool in the shipped pack")
    fun `the daily rotation is satisfiable`() {
        assumeTrue(packBytes != null)
        val summaries = PuzzlePack.summaries(packBytes!!)

        DailySchedule.allSpecs.forEach { spec ->
            val pool = summaries.filter { it.width == spec.size && it.difficulty == spec.difficulty }
            assertTrue(pool.isNotEmpty()) {
                "no ${spec.difficulty} ${spec.size}x${spec.size} puzzles for the daily rotation"
            }
            // A pool this small would repeat a puzzle within a couple of months.
            assertTrue(pool.size >= 300) {
                "${spec.difficulty} ${spec.size}x${spec.size} pool is only ${pool.size} puzzles"
            }
        }
    }

    @Test
    @DisplayName("a year of dailies is deterministic and rarely repeats")
    fun `daily selection over a year`() {
        assumeTrue(packBytes != null)
        val summaries = PuzzlePack.summaries(packBytes!!)

        fun dailyIdFor(date: LocalDate): String {
            val spec = DailySchedule.specFor(date)
            val pool = summaries.filter { it.width == spec.size && it.difficulty == spec.difficulty }
            return pool[com.ganim.nonogram.daily.DailySelector.indexInPool(date, pool.size)].id
        }

        val start = LocalDate.of(2026, 1, 1)
        val year = (0 until 365).map { start.plusDays(it.toLong()) }

        // Deterministic: computing twice gives the same answer.
        year.forEach { date -> dailyIdFor(date) shouldBe dailyIdFor(date) }

        // Some repeats are unavoidable. `hash(date) % poolSize` has no memory, so each
        // day is an independent draw and the birthday paradox applies: 104 Mondays and
        // Tuesdays drawing from 900 easy 10x10 puzzles collide about six times a year on
        // their own, and summed across the week the expectation is ~19 repeats per year.
        //
        // This asserts the hash is not doing anything *worse* than chance. Measured, it
        // does slightly better - 14 repeats across 2026. A hash that clumped would show
        // up here as far fewer distinct puzzles.
        val distinct = year.map(::dailyIdFor).toSet().size
        assertTrue(distinct >= 335) {
            "only $distinct distinct puzzles across 365 days, worse than chance would give"
        }
    }

    @Test
    fun `each day gets the size and difficulty its weekday calls for`() {
        assumeTrue(packBytes != null)
        val summaries = PuzzlePack.summaries(packBytes!!).associateBy { it.id }
        val byPool = PuzzlePack.summaries(packBytes!!)

        val start = LocalDate.of(2026, 4, 6) // a Monday
        (0 until 14).forEach { offset ->
            val date = start.plusDays(offset.toLong())
            val spec = DailySchedule.specFor(date)
            val pool = byPool.filter { it.width == spec.size && it.difficulty == spec.difficulty }
            val chosen = pool[com.ganim.nonogram.daily.DailySelector.indexInPool(date, pool.size)]
            val summary = summaries[chosen.id].shouldNotBeNull()
            summary.width shouldBe spec.size
            summary.difficulty shouldBe spec.difficulty
        }
    }

    @Test
    fun `the pack covers every difficulty the archive filters on`() {
        assumeTrue(packBytes != null)
        val summaries = PuzzlePack.summaries(packBytes!!)
        Difficulty.entries.forEach { difficulty ->
            assertTrue(summaries.any { it.difficulty == difficulty }) {
                "archive offers a $difficulty filter that would return nothing"
            }
        }
        summaries.map { it.width }.distinct().sorted() shouldBe listOf(5, 10, 15, 20)
    }
}
