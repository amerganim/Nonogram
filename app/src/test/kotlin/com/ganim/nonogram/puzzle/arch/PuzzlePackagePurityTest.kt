package com.ganim.nonogram.puzzle.arch

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Build plan 3, hard rule: "the `puzzle` package must have no Android imports. It is
 * pure Kotlin so it can be unit-tested on the JVM at speed, and so the offline
 * generation tool can reuse it."
 *
 * Build plan 4 acceptance: "Zero Android imports in the `puzzle` package (enforce with a
 * test or lint rule)."
 *
 * This is the enforcement. It fails the build the moment someone reaches for a `Context`
 * inside the engine - which would be easy to do and would quietly cost the generator its
 * ability to run on a desktop JVM.
 */
class PuzzlePackagePurityTest {

    @Test
    @DisplayName("no Android or AndroidX imports anywhere under puzzle/")
    fun `puzzle package is pure kotlin`() {
        val sources = puzzleSources()
        assertTrue(sources.isNotEmpty()) { "found no sources under $PUZZLE_PATH - is the path right?" }

        val offences = sources.flatMap { file ->
            file.readLines()
                .mapIndexedNotNull { index, line ->
                    val trimmed = line.trim()
                    if (FORBIDDEN.any { trimmed.startsWith(it) }) {
                        "${file.path}:${index + 1}: $trimmed"
                    } else {
                        null
                    }
                }
        }

        assertTrue(offences.isEmpty()) {
            buildString {
                appendLine("The puzzle package must stay free of Android dependencies.")
                appendLine("Move the Android-facing code into data/ or game/ instead.")
                offences.forEach { appendLine("  $it") }
            }
        }
    }

    @Test
    fun `puzzle package does not reach into app packages either`() {
        // The engine is the bottom of the dependency graph. Anything it imports from a
        // sibling app package would be a cycle waiting to happen.
        val offences = puzzleSources().flatMap { file ->
            file.readLines()
                .mapIndexedNotNull { index, line ->
                    val trimmed = line.trim()
                    val reachesUp = trimmed.startsWith("import com.ganim.nonogram.") &&
                        !trimmed.startsWith("import com.ganim.nonogram.puzzle.")
                    if (reachesUp) "${file.path}:${index + 1}: $trimmed" else null
                }
        }
        assertTrue(offences.isEmpty()) {
            "puzzle/ may not depend on other app packages:\n" + offences.joinToString("\n")
        }
    }

    private fun puzzleSources(): List<File> =
        CANDIDATE_ROOTS.map(::File)
            .firstOrNull { it.isDirectory }
            ?.walkTopDown()
            ?.filter { it.isFile && it.extension == "kt" }
            ?.toList()
            .orEmpty()

    private companion object {
        const val PUZZLE_PATH = "src/main/kotlin/com/ganim/nonogram/puzzle"

        /** Tests may run from the module directory or the repo root. */
        val CANDIDATE_ROOTS = listOf(PUZZLE_PATH, "app/$PUZZLE_PATH")

        val FORBIDDEN = listOf(
            "import android.",
            "import androidx.",
            "import com.google.android.",
            "import dalvik.",
        )
    }
}
