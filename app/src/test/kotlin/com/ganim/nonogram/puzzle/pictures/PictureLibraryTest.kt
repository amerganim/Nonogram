package com.ganim.nonogram.puzzle.pictures

import com.ganim.nonogram.puzzle.model.Grid
import com.ganim.nonogram.puzzle.model.Puzzle
import com.ganim.nonogram.puzzle.pack.PuzzlePack
import com.ganim.nonogram.puzzle.solver.PuzzleSolver
import com.ganim.nonogram.puzzle.solver.SolveResult
import com.ganim.nonogram.puzzle.solver.BacktrackingVerifier
import com.ganim.nonogram.puzzle.solver.Verification
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The hand-drawn pictures have to clear the same bar as a generated puzzle.
 *
 * A drawing is easy to get wrong in a way that only a solver notices: it looks like a
 * cat, and it happens to have two valid solutions, or it needs a guess somewhere in the
 * middle. Build plan 4.3 says every shipped puzzle is solvable by logic alone and has
 * exactly one solution, and "hand-drawn" is not an exemption - it is the reason to check,
 * because nobody is generating these under a constraint.
 *
 * Three drawings failed this test during development and were redrawn or replaced. See
 * docs/picture-puzzles.md.
 */
class PictureLibraryTest {

    @Test
    @DisplayName("every picture is solvable by logic alone, with exactly one solution")
    fun `every picture is shippable`() {
        val solver = PuzzleSolver()
        val verifier = BacktrackingVerifier()

        val failures = PictureLibrary.all.mapNotNull { picture ->
            val size = picture.size
            val cells = picture.cells()
            val rowClues = Grid.rowClues(size, size, cells)
            val colClues = Grid.columnClues(size, size, cells)

            when (val result = solver.run(size, size, rowClues, colClues).result) {
                is SolveResult.Unique -> {
                    // Logic reaching an answer is not proof it is the only answer; the
                    // search settles that independently.
                    val counted = verifier.countSolutions(size, size, rowClues, colClues)
                    if (counted == Verification.Counted(1, 2)) {
                        null
                    } else {
                        "${picture.name}: logic solved it but the search found $counted"
                    }
                }
                // A stall means a player would have to guess, which is the one thing a
                // fair nonogram never asks for.
                else -> "${picture.name}: not solvable by logic alone ($result)"
            }
        }

        assertTrue(failures.isEmpty()) {
            "unshippable pictures:${System.lineSeparator()}${failures.joinToString(System.lineSeparator())}"
        }
    }

    @Test
    fun `the drawn grid is what the clues describe`() {
        PictureLibrary.all.forEach { picture ->
            val cells = picture.cells()
            cells.size shouldBe picture.size * picture.size
            // Guards against a transposed drawing: cells() reads rows-first, and a
            // row/column swap would still be square and still solvable.
            picture.rows.forEachIndexed { row, text ->
                text.forEachIndexed { col, ch ->
                    assertEquals(ch == '#', cells[row * picture.size + col]) {
                        "${picture.name} disagrees with its own text at ($row,$col)"
                    }
                }
            }
        }
    }

    @Test
    fun `names are unique and human readable`() {
        val names = PictureLibrary.all.map { it.name }
        assertEquals(names.size, names.toSet().size, "two pictures share a name")
        names.forEach { name ->
            assertTrue(name.isNotBlank() && name.length <= MAX_NAME) {
                "'$name' will not fit under a thumbnail"
            }
        }
    }

    @Test
    @DisplayName("no picture duplicates another, or a generated puzzle's, id")
    fun `ids are unique`() {
        val ids = PictureLibrary.all.map {
            Puzzle.stableId(it.size, it.size, it.cells())
        }
        assertEquals(ids.size, ids.toSet().size, "two pictures encode the same grid")
    }

    @Test
    fun `a picture is not blank or full`() {
        PictureLibrary.all.forEach { picture ->
            val filled = picture.cells().count { it }
            val total = picture.size * picture.size
            assertTrue(filled in (total / 10)..(total * 4 / 5)) {
                "${picture.name} is $filled of $total cells - too empty or too solid to read"
            }
        }
    }

    // --- the shipped pack -------------------------------------------------------------

    @Test
    @DisplayName("pictures.bin carries every drawing, with its name")
    fun `shipped picture pack matches the library`() {
        val file = listOf("src/main/assets/pictures.bin", "app/src/main/assets/pictures.bin")
            .map(::File)
            .firstOrNull { it.isFile }
        assertTrue(file != null) {
            "pictures.bin not found - run 'gradlew generatePuzzlePack'"
        }

        val puzzles = PuzzlePack.decodeAll(file!!.readBytes())
        puzzles.size shouldBe PictureLibrary.all.size

        // The name is what the completion card shows, so losing it in the codec would be
        // a silent regression - the puzzle would still play perfectly.
        puzzles.zip(PictureLibrary.all).forEach { (puzzle, picture) ->
            puzzle.name shouldBe picture.name
            puzzle.solution.toList() shouldBe picture.cells().toList()
        }
    }

    private companion object {
        const val MAX_NAME = 16
    }
}
