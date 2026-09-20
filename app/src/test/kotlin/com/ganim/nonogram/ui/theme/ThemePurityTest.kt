package com.ganim.nonogram.ui.theme

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Build plan section 7 acceptance: "Light and dark themes complete across every screen,
 * **no hardcoded colours outside the theme file**."
 *
 * A stray `Color(0xFF...)` in a screen is invisible in whichever theme the author had
 * open and wrong in the other. This finds them.
 */
class ThemePurityTest {

    @Test
    @DisplayName("no colour literals outside the theme package")
    fun `colours live only in the theme`() {
        val offences = uiSources().flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                val code = line.substringBefore("//").trim()
                if (COLOUR_LITERAL.containsMatchIn(code)) {
                    "${file.path}:${index + 1}: ${line.trim()}"
                } else {
                    null
                }
            }
        }

        assertTrue(offences.isEmpty()) {
            buildString {
                appendLine("Colours belong in ui/theme/Color.kt, where both themes define them.")
                appendLine("A literal here is wrong in whichever theme the author did not have open.")
                offences.forEach { appendLine("  $it") }
            }
        }
    }

    @Test
    @DisplayName("screens read colours from the theme rather than from Material defaults only")
    fun `screens use the board palette`() {
        // Every screen that draws the board or the calendar needs the palette that
        // Material has no slots for. If one stops reading it, it has started inventing
        // its own colours somewhere.
        val required = listOf(
            "game/BoardCanvas.kt",
            "game/GameScreen.kt",
            "daily/DailyScreen.kt",
            "archive/ArchiveScreen.kt",
            "ui/SettingsScreen.kt",
        )
        val root = sourceRoot()
        required.forEach { relative ->
            val file = File(root, relative)
            assertTrue(file.isFile) { "expected $relative to exist" }
            assertTrue(file.readText().contains("LocalBoardColors")) {
                "$relative does not read LocalBoardColors"
            }
        }
    }

    @Test
    fun `animation durations are not hardcoded in screens`() {
        val offences = uiSources().flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                val code = line.substringBefore("//").trim()
                if (TWEEN_LITERAL.containsMatchIn(code)) "${file.path}:${index + 1}: ${line.trim()}" else null
            }
        }
        assertTrue(offences.isEmpty()) {
            "Durations belong in Motion, so reduce-motion applies to all of them:\n" +
                offences.joinToString("\n")
        }
    }

    private fun sourceRoot(): File =
        listOf(
            "src/main/kotlin/com/ganim/nonogram",
            "app/src/main/kotlin/com/ganim/nonogram",
        ).map(::File).first { it.isDirectory }

    private fun uiSources(): List<File> =
        sourceRoot().walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            // The theme package is where colours are supposed to be.
            .filterNot { it.path.replace('\\', '/').contains("/ui/theme/") }
            .toList()

    private companion object {
        /**
         * Matches `Color(0xFF123456)` and `Color.Red`, but not `Color.Transparent` or
         * `Color.Unspecified` - those are the absence of a colour rather than a design
         * decision, and forcing them through the palette would be noise.
         */
        val COLOUR_LITERAL = Regex(
            """Color\(\s*0x[0-9A-Fa-f]{6,8}|Color\.(?!Transparent|Unspecified)[A-Z]\w+""",
        )

        /** `tween(240)` in a screen dodges the reduce-motion setting. */
        val TWEEN_LITERAL = Regex("""tween\(\s*\d+""")
    }
}
