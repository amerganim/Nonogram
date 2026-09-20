package com.ganim.nonogram.puzzle.pack

import com.ganim.nonogram.puzzle.generator.DifficultyRater
import com.ganim.nonogram.puzzle.generator.PuzzleGenerator
import com.ganim.nonogram.puzzle.model.Difficulty
import com.ganim.nonogram.puzzle.model.Grid
import com.ganim.nonogram.puzzle.model.Puzzle
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.random.Random

/**
 * Build plan 4.5 acceptance: "Generation tool emits the 5,000-puzzle pack; pack is under
 * 1 MB; a round-trip test loads all 5,000 and re-derives clues matching the originals."
 */
class PuzzlePackTest {

    // --- codec ------------------------------------------------------------------------

    @Test
    fun `round trip preserves every field`() {
        val puzzles = samplePuzzles()
        val decoded = PuzzlePack.decodeAll(PuzzlePack.encode(puzzles))

        decoded.size shouldBe puzzles.size
        puzzles.zip(decoded).forEach { (original, restored) ->
            restored shouldBe original
            restored.id shouldBe original.id
            restored.solution.toList() shouldBe original.solution.toList()
            restored.rowClues shouldBe original.rowClues
            restored.colClues shouldBe original.colClues
            restored.difficulty shouldBe original.difficulty
            restored.solveDepth shouldBe original.solveDepth
        }
    }

    @Test
    @DisplayName("clues are re-derived on decode, not read from the file")
    fun `decoded clues match clues derived from the grid`() {
        val puzzles = samplePuzzles()
        PuzzlePack.decodeAll(PuzzlePack.encode(puzzles)).forEach { puzzle ->
            puzzle.rowClues shouldBe Grid.rowClues(puzzle.width, puzzle.height, puzzle.solution)
            puzzle.colClues shouldBe Grid.columnClues(puzzle.width, puzzle.height, puzzle.solution)
        }
    }

    @Test
    fun `a single puzzle can be read without decoding the rest`() {
        val puzzles = samplePuzzles()
        val bytes = PuzzlePack.encode(puzzles)
        puzzles.indices.forEach { i ->
            PuzzlePack.decodeAt(bytes, i) shouldBe puzzles[i]
        }
    }

    @Test
    @DisplayName("format v2: a puzzle's name survives the round trip")
    fun `names round trip`() {
        val named = samplePuzzles().mapIndexed { i, puzzle ->
            // Names are variable length and sit between the header and the bitset, so a
            // mis-sized name field corrupts every record after it, not just its own.
            if (i % 2 == 0) puzzle.copy(name = "Picture $i") else puzzle
        }
        val bytes = PuzzlePack.encode(named)
        PuzzlePack.decodeAll(bytes).map { it.name } shouldBe named.map { it.name }
        named.indices.forEach { i -> PuzzlePack.decodeAt(bytes, i) shouldBe named[i] }
        PuzzlePack.estimatedSize(named) shouldBe bytes.size
    }

    @Test
    fun `header reports version and count`() {
        val puzzles = samplePuzzles()
        val header = PuzzlePack.readHeader(PuzzlePack.encode(puzzles))
        header shouldBe PuzzlePack.Header(PuzzlePack.VERSION, puzzles.size)
    }

    @Test
    fun `estimated size matches what encoding actually produces`() {
        val puzzles = samplePuzzles()
        PuzzlePack.estimatedSize(puzzles) shouldBe PuzzlePack.encode(puzzles).size
    }

    @Test
    fun `a file that is not a pack is rejected`() {
        val notAPack = ByteArray(64) { 0x7F }
        val error = runCatching { PuzzlePack.readHeader(notAPack) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException) { "expected rejection, got $error" }
    }

    @Test
    fun `an out of range index is rejected`() {
        val bytes = PuzzlePack.encode(samplePuzzles())
        val error = runCatching { PuzzlePack.decodeAt(bytes, 9_999) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException) { "expected rejection, got $error" }
    }

    @Test
    fun `bitset packing survives every grid size`() {
        val rng = Random(5)
        DifficultyRater.SHIPPED_SIZES.forEach { size ->
            repeat(20) {
                val cells = BooleanArray(size * size) { rng.nextBoolean() }
                val restored = Grid.fromBitset(Grid.toBitset(cells), cells.size)
                assertEquals(cells.toList(), restored.toList(), "bitset round trip failed at ${size}x$size")
            }
        }
    }

    // --- the real shipped pack --------------------------------------------------------

    @Test
    @DisplayName("the committed pack holds 5,000 puzzles, under 1 MB, all re-derivable")
    fun `shipped pack round trips`() {
        val file = shippedPack()
        assumeTrue(file != null) {
            "puzzles.bin not found - run 'gradlew generatePuzzlePack' first"
        }
        val bytes = file!!.readBytes()

        assertTrue(bytes.size <= SIZE_BUDGET) {
            "pack is ${bytes.size} bytes, over the 1 MB budget - the encoding is wrong"
        }

        val header = PuzzlePack.readHeader(bytes)
        assertEquals(EXPECTED_COUNT, header.count, "pack should hold $EXPECTED_COUNT puzzles")

        val puzzles = PuzzlePack.decodeAll(bytes)
        puzzles.size shouldBe EXPECTED_COUNT

        puzzles.forEach { puzzle ->
            // Clues must be re-derivable from the packed grid alone.
            puzzle.rowClues shouldBe Grid.rowClues(puzzle.width, puzzle.height, puzzle.solution)
            puzzle.colClues shouldBe Grid.columnClues(puzzle.width, puzzle.height, puzzle.solution)
            puzzle.id shouldBe Puzzle.stableId(puzzle.width, puzzle.height, puzzle.solution)
            assertTrue(puzzle.width in DifficultyRater.SHIPPED_SIZES) {
                "unexpected grid size ${puzzle.width}"
            }
        }

        val ids = puzzles.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "the pack contains duplicate puzzles")

        // Re-encoding what we decoded must reproduce the file byte for byte.
        assertTrue(PuzzlePack.encode(puzzles).contentEquals(bytes)) {
            "re-encoding the decoded pack did not reproduce the original bytes"
        }
    }

    @Test
    fun `the shipped pack covers every difficulty and size`() {
        val file = shippedPack()
        assumeTrue(file != null) { "puzzles.bin not found" }
        val puzzles = PuzzlePack.decodeAll(file!!.readBytes())

        Difficulty.entries.forEach { difficulty ->
            val count = puzzles.count { it.difficulty == difficulty }
            assertTrue(count > 0) { "no $difficulty puzzles in the pack" }
        }
        DifficultyRater.SHIPPED_SIZES.forEach { size ->
            val count = puzzles.count { it.width == size }
            assertTrue(count > 0) { "no ${size}x$size puzzles in the pack" }
        }
    }

    // --- helpers ----------------------------------------------------------------------

    private fun samplePuzzles(): List<Puzzle> =
        DifficultyRater.SHIPPED_SIZES.flatMap { size ->
            val generator = PuzzleGenerator(Random(size.toLong()))
            List(6) { generator.generateAny(size) ?: error("no puzzle at size $size") }
        }

    /** The committed pack, whether tests run from the module or the repo root. */
    private fun shippedPack(): File? =
        listOf("src/main/assets/puzzles.bin", "app/src/main/assets/puzzles.bin")
            .map(::File)
            .firstOrNull { it.isFile }

    private companion object {
        const val EXPECTED_COUNT = 5_000
        const val SIZE_BUDGET = 1024 * 1024
    }
}
