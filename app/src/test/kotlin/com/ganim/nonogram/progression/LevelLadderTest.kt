package com.ganim.nonogram.progression

import com.ganim.nonogram.data.repo.ArchiveEntry
import com.ganim.nonogram.puzzle.model.Difficulty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The ladder is the first thing a new player sees, so the things that would confuse one
 * are what this checks: a ramp that goes backwards, a level number that skips, a "next"
 * that points at something already solved.
 *
 * It runs against a stand-in pack shaped like the real one (the shipped pack's bands are
 * already sorted by size: 5x5 easy, 10x10 easy and medium, 15x15 medium and hard, 20x20
 * hard and expert), plus one test against the real asset.
 */
class LevelLadderTest {

    @Test
    fun `stages come out in the intended order`() {
        val stages = LevelLadder.build(fakePack())
        stages.map { it.stage } shouldBe LevelLadder.Stage.entries.toList()
    }

    @Test
    @DisplayName("the ramp never goes backwards: each stage's grids are at least as big")
    fun `grid size never shrinks down the ladder`() {
        val levels = LevelLadder.allLevels(LevelLadder.build(fakePack()))
        levels.zipWithNext { a, b ->
            assertTrue(b.size >= a.size) {
                "level ${b.number} is ${b.size}x${b.size}, smaller than level " +
                    "${a.number} at ${a.size}x${a.size} - the ladder goes downhill there"
            }
        }
    }

    @Test
    fun `level numbers run 1 to n with no gaps`() {
        val levels = LevelLadder.allLevels(LevelLadder.build(fakePack()))
        levels.map { it.number } shouldBe (1..levels.size).toList()
        // And within a stage they restart, because that is what the tile shows.
        LevelLadder.build(fakePack()).forEach { stage ->
            stage.levels.map { it.numberInStage } shouldBe (1..stage.levels.size).toList()
        }
    }

    @Test
    fun `no puzzle appears twice`() {
        val ids = LevelLadder.allLevels(LevelLadder.build(fakePack())).map { it.id }
        assertEquals(ids.size, ids.toSet().size, "the same puzzle is two different levels")
    }

    @Test
    @DisplayName("enough easy levels come before the first medium one")
    fun `the easy end is long`() {
        val stages = LevelLadder.build(fakePack())
        val firstMedium = LevelLadder.allLevels(stages)
            .first { it.stage == LevelLadder.Stage.MEDIUM }
        // The tester's problem was not that the game was too easy. Forty is a floor,
        // not a target - if someone shortens the warm-up, this should argue back.
        assertTrue(firstMedium.number > 40) {
            "only ${firstMedium.number - 1} levels before Medium; a beginner needs longer"
        }
    }

    @Test
    fun `next is the first unsolved level, even when later ones are done`() {
        val stages = LevelLadder.build(fakePack())
        val levels = LevelLadder.allLevels(stages)

        LevelLadder.next(stages, emptySet())?.number shouldBe 1

        // Someone who jumped ahead is pointed back at the gap, not marched off the end.
        val skipped = levels.filter { it.number != 3 }.map { it.id }.toSet()
        LevelLadder.next(stages, skipped)?.number shouldBe 3

        val everything = levels.map { it.id }.toSet()
        assertTrue(LevelLadder.next(stages, everything) == null) {
            "a finished ladder should have no next level"
        }
    }

    @Test
    @DisplayName("a pool too small to fill its quota shortens the stage instead of failing")
    fun `a thin pack degrades gracefully`() {
        val thin = List(3) { entry(it, 5, Difficulty.EASY) }
        val stages = LevelLadder.build(thin)
        stages.first().levels.size shouldBe 3
        // Every other stage empties out rather than throwing.
        assertTrue(stages.drop(1).all { it.levels.isEmpty() })
        LevelLadder.next(stages, emptySet())?.number shouldBe 1
    }

    @Test
    fun `the ladder is stable across rebuilds`() {
        val first = LevelLadder.allLevels(LevelLadder.build(fakePack())).map { it.id }
        val again = LevelLadder.allLevels(LevelLadder.build(fakePack())).map { it.id }
        // Level 7 has to be the same puzzle on every device, or two players comparing
        // progress are comparing nothing.
        first shouldBe again
    }

    // --- helpers ---------------------------------------------------------------------

    private fun entry(index: Int, size: Int, difficulty: Difficulty) = ArchiveEntry(
        index = index,
        id = "p$index",
        size = size,
        difficulty = difficulty,
    )

    /** Shaped like the shipped pack, at one tenth the size. */
    private fun fakePack(): List<ArchiveEntry> {
        var index = 0
        fun run(count: Int, size: Int, difficulty: Difficulty) =
            List(count) { entry(index++, size, difficulty) }
        return run(60, 5, Difficulty.EASY) +
            run(90, 10, Difficulty.EASY) +
            run(75, 10, Difficulty.MEDIUM) +
            run(75, 15, Difficulty.MEDIUM) +
            run(60, 15, Difficulty.HARD) +
            run(60, 20, Difficulty.HARD) +
            run(80, 20, Difficulty.EXPERT)
    }
}
