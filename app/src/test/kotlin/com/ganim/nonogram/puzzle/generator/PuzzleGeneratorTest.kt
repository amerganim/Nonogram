package com.ganim.nonogram.puzzle.generator

import com.ganim.nonogram.puzzle.model.Difficulty
import com.ganim.nonogram.puzzle.model.Grid
import com.ganim.nonogram.puzzle.model.Puzzle
import com.ganim.nonogram.puzzle.solver.BacktrackingVerifier
import com.ganim.nonogram.puzzle.solver.PuzzleSolver
import com.ganim.nonogram.puzzle.solver.SolveResult
import com.ganim.nonogram.puzzle.solver.Verification
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import kotlin.random.Random

/**
 * Build plan 4.4 acceptance: "Generator produces 100 puzzles at each difficulty, and an
 * independent backtracking verifier confirms every one has exactly one solution."
 */
class PuzzleGeneratorTest {

    private val verifier = BacktrackingVerifier()

    @TestFactory
    @DisplayName("100 puzzles per difficulty, each confirmed unique by independent search")
    fun `every generated puzzle has exactly one solution`(): List<DynamicTest> =
        Difficulty.entries.map { difficulty ->
            DynamicTest.dynamicTest(difficulty.name) {
                val puzzles = generateBatch(difficulty, PER_DIFFICULTY)
                assertEquals(
                    PER_DIFFICULTY, puzzles.size,
                    "generator could not produce $PER_DIFFICULTY $difficulty puzzles",
                )

                puzzles.forEach { puzzle ->
                    puzzle.difficulty shouldBe difficulty

                    // The stored grid must actually satisfy the stored clues.
                    Grid.rowClues(puzzle.width, puzzle.height, puzzle.solution) shouldBe puzzle.rowClues
                    Grid.columnClues(puzzle.width, puzzle.height, puzzle.solution) shouldBe puzzle.colClues

                    // Independent confirmation - no shared code with the line solver.
                    val counted = verifier.countSolutions(
                        puzzle.width, puzzle.height, puzzle.rowClues, puzzle.colClues,
                    )
                    assertEquals(
                        Verification.Counted(1, 2), counted,
                        "puzzle ${puzzle.id} (${puzzle.width}x${puzzle.height}) is not uniquely solvable:\n${puzzle.render()}",
                    )
                }
            }
        }

    @Test
    @DisplayName("every generated puzzle is solvable by logic alone, never by guessing")
    fun `generated puzzles are logic-solvable`() {
        val solver = PuzzleSolver()
        Difficulty.entries.forEach { difficulty ->
            generateBatch(difficulty, 40).forEach { puzzle ->
                val result = solver.solve(puzzle)
                assertTrue(result is SolveResult.Unique) {
                    "puzzle ${puzzle.id} rated $difficulty was $result:\n${puzzle.render()}"
                }
                assertEquals(
                    puzzle.solveDepth, (result as SolveResult.Unique).depth,
                    "stored solveDepth disagrees with a fresh solve for ${puzzle.id}",
                )
            }
        }
    }

    @Test
    fun `generated grids sit inside the plan's density band`() {
        val generator = PuzzleGenerator(Random(11))
        DifficultyRater.SHIPPED_SIZES.forEach { size ->
            repeat(25) {
                val puzzle = generator.generateAny(size) ?: error("no puzzle at size $size")
                val density = Grid.density(puzzle.solution)
                assertTrue(density in GridShaper.MIN_DENSITY..GridShaper.MAX_DENSITY) {
                    "density $density outside the 0.45-0.60 band for ${puzzle.id}"
                }
            }
        }
    }

    @Test
    fun `generation is reproducible for a given seed`() {
        val first = PuzzleGenerator(Random(99)).generateAny(10)
        val second = PuzzleGenerator(Random(99)).generateAny(10)
        first shouldBe second
    }

    @Test
    fun `puzzle ids are stable across rebuilds and unique across a batch`() {
        val puzzles = DifficultyRater.SHIPPED_SIZES.flatMap { size ->
            val generator = PuzzleGenerator(Random(size * 5L))
            List(40) { generator.generateAny(size) ?: error("no puzzle at size $size") }
        }
        val ids = puzzles.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicate puzzle ids in a single batch")

        puzzles.forEach { puzzle ->
            val recomputed = Puzzle.stableId(puzzle.width, puzzle.height, puzzle.solution)
            assertEquals(puzzle.id, recomputed, "id is not a pure function of the grid")
        }
    }

    @Test
    fun `asking a size for a difficulty it cannot produce is rejected`() {
        val generator = PuzzleGenerator(Random(1))
        val error = runCatching { generator.generate(5, Difficulty.EXPERT) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException) { "expected rejection, got $error" }
    }

    @Test
    fun `larger grids are shaped into blobs rather than noise`() {
        // Smoothed grids have far fewer isolated single cells than random ones.
        val rng = Random(2024)
        val blob = GridShaper.blob(20, 20, 0.5, rng)
        val noise = GridShaper.random(20, 20, 0.5, rng)
        assertTrue(isolatedCells(20, 20, blob) < isolatedCells(20, 20, noise)) {
            "smoothing did not reduce isolated cells: blob=${isolatedCells(20, 20, blob)} " +
                "noise=${isolatedCells(20, 20, noise)}"
        }
    }

    // --- helpers ---------------------------------------------------------------------

    /** Spreads generation across every size that can produce [difficulty]. */
    private fun generateBatch(difficulty: Difficulty, count: Int): List<Puzzle> {
        val sizes = DifficultyRater.sizesFor(difficulty)
        val generator = PuzzleGenerator(Random(difficulty.ordinal * 7919L + 13))
        return (0 until count).mapNotNull { i ->
            generator.generate(sizes[i % sizes.size], difficulty)
        }
    }

    /** Filled cells with no filled orthogonal neighbour. */
    private fun isolatedCells(width: Int, height: Int, cells: BooleanArray): Int {
        var count = 0
        for (r in 0 until height) {
            for (c in 0 until width) {
                if (!cells[r * width + c]) continue
                val neighbours = listOf(r - 1 to c, r + 1 to c, r to c - 1, r to c + 1)
                    .count { (nr, nc) ->
                        nr in 0 until height && nc in 0 until width && cells[nr * width + nc]
                    }
                if (neighbours == 0) count++
            }
        }
        return count
    }

    private companion object {
        const val PER_DIFFICULTY = 100
    }
}
