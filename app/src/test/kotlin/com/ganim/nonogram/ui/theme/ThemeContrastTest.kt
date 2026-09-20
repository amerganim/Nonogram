package com.ganim.nonogram.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import kotlin.math.pow

/**
 * Build plan section 7 acceptance: "Contrast ratios meet WCAG AA for all text."
 *
 * Asserting that in a review comment is worth nothing - a palette tweak six months from
 * now silently breaks it. Computing it here means the build fails instead.
 *
 * The maths is WCAG 2.1: relative luminance with the sRGB transfer function, then
 * `(lighter + 0.05) / (darker + 0.05)`. AA wants 4.5:1 for body text and 3:1 for
 * graphical objects and UI components.
 */
class ThemeContrastTest {

    @TestFactory
    @DisplayName("light theme meets WCAG AA")
    fun lightThemeContrast(): List<DynamicTest> = contrastTests("light", LightBoardColors)

    @TestFactory
    @DisplayName("dark theme meets WCAG AA")
    fun darkThemeContrast(): List<DynamicTest> = contrastTests("dark", DarkBoardColors)

    private fun contrastTests(theme: String, colors: BoardColors): List<DynamicTest> =
        contrastPairs(colors).map { pair ->
            DynamicTest.dynamicTest("$theme: ${pair.name}") {
                val ratio = contrastRatio(pair.foreground, pair.background)
                assertTrue(ratio >= pair.minimumRatio) {
                    "$theme ${pair.name} is ${"%.2f".format(ratio)}:1, " +
                        "needs ${pair.minimumRatio}:1"
                }
            }
        }

    @Test
    @DisplayName("the row and column highlight is subtle, not a second fill colour")
    fun `the highlight stays subtle`() {
        listOf("light" to LightBoardColors, "dark" to DarkBoardColors).forEach { (name, colors) ->
            assertTrue(colors.highlight.alpha <= 0.2f) {
                "$name highlight alpha is ${colors.highlight.alpha}; the plan asks for a subtle one"
            }
            assertTrue(colors.highlight.alpha > 0.03f) {
                "$name highlight alpha is ${colors.highlight.alpha}, too faint to see"
            }
        }
    }

    @Test
    @DisplayName("a satisfied clue is visibly quieter than an unsatisfied one")
    fun `satisfied clues are de-emphasised`() {
        listOf("light" to LightBoardColors, "dark" to DarkBoardColors).forEach { (name, colors) ->
            val active = contrastRatio(colors.clueText, colors.boardBackground)
            val satisfied = contrastRatio(colors.clueTextSatisfied, colors.boardBackground)
            assertTrue(satisfied < active * 0.6) {
                "$name satisfied clues (${"%.1f".format(satisfied)}:1) are not distinct enough " +
                    "from active ones (${"%.1f".format(active)}:1)"
            }
        }
    }

    @Test
    fun `the two themes are genuinely different, not one inverted`() {
        // A dark theme built by inverting a light one looks washed out. The plan calls
        // that out specifically, so this pins that the palettes were authored separately.
        assertTrue(LightBoardColors.accent != DarkBoardColors.accent)
        assertTrue(LightBoardColors.boardBackground != DarkBoardColors.boardBackground)
        val lightBg = relativeLuminance(LightBoardColors.boardBackground)
        val darkBg = relativeLuminance(DarkBoardColors.boardBackground)
        assertTrue(lightBg > 0.7 && darkBg < 0.1) {
            "backgrounds are not clearly light and dark: $lightBg / $darkBg"
        }
    }

    @Test
    fun `every animation stays under the plan's 300ms ceiling`() {
        listOf(Motion.QUICK, Motion.STANDARD).forEach { duration ->
            assertTrue(duration < Motion.MAXIMUM) { "$duration ms exceeds the 300ms ceiling" }
        }
    }

    @Test
    fun `reduce motion collapses every duration to zero`() {
        listOf(Motion.QUICK, Motion.STANDARD).forEach { duration ->
            Motion.duration(duration, reduceMotion = true).let {
                assertTrue(it == 0) { "reduce-motion left a $it ms animation" }
            }
            assertTrue(Motion.duration(duration, reduceMotion = false) == duration)
        }
    }

    // --- WCAG 2.1 maths ---------------------------------------------------------------

    private fun contrastRatio(foreground: Color, background: Color): Double {
        // A translucent foreground is what the viewer actually sees composited over the
        // background, so flatten it first or the ratio is a fiction.
        val flattened = flatten(foreground, background)
        val a = relativeLuminance(flattened)
        val b = relativeLuminance(background)
        val lighter = maxOf(a, b)
        val darker = minOf(a, b)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun flatten(foreground: Color, background: Color): Color {
        if (foreground.alpha >= 1f) return foreground
        val a = foreground.alpha
        return Color(
            red = foreground.red * a + background.red * (1 - a),
            green = foreground.green * a + background.green * (1 - a),
            blue = foreground.blue * a + background.blue * (1 - a),
            alpha = 1f,
        )
    }

    private fun relativeLuminance(color: Color): Double {
        fun channel(value: Float): Double {
            val c = value.toDouble()
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
    }
}
