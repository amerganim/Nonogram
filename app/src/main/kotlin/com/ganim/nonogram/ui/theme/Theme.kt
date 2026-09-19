package com.ganim.nonogram.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Theme scaffolding for Phase 2.
 *
 * Phase 4 (build plan 7) owns the real visual design. This exists so nothing in Phase 2
 * hard-codes a colour: every board colour is a token here, so Phase 4 changes values in
 * one file rather than hunting through the canvas code.
 *
 * Direction already set by the plan: "Calm, precise, uncluttered... one confident accent
 * colour, high-contrast grid. The reference point is a well-made physical puzzle book."
 * Dark is fully specified rather than derived, because the plan warns it is the default
 * for many puzzle players and must not be an afterthought. Stock Material purple is
 * deliberately avoided.
 */

/**
 * Board colours the [androidx.compose.material3.ColorScheme] has no slot for.
 *
 * Kept separate because a nonogram grid needs distinctions Material does not model - a
 * filled cell, a cross, a wrong cell and a satisfied clue are not surfaces or containers.
 */
@Immutable
data class BoardColors(
    val boardBackground: Color,
    val cellEmpty: Color,
    val cellFilled: Color,
    val cellCross: Color,
    val cellMistake: Color,
    val gridLine: Color,
    val gridLineMajor: Color,
    val clueText: Color,
    val clueTextSatisfied: Color,
    val clueBackground: Color,
    val highlight: Color,
    val accent: Color,
)

private val InkLight = Color(0xFF1C2024)
private val PaperLight = Color(0xFFFAF9F6)
private val AccentTeal = Color(0xFF00696E)

private val InkDark = Color(0xFFE6E4E0)
private val PaperDark = Color(0xFF14171A)
private val AccentTealDark = Color(0xFF4FD8DE)

private val LightBoardColors = BoardColors(
    boardBackground = PaperLight,
    cellEmpty = Color(0xFFFFFFFF),
    cellFilled = InkLight,
    cellCross = Color(0xFF8A9199),
    cellMistake = Color(0xFFB3261E),
    gridLine = Color(0xFFD5D2CC),
    gridLineMajor = Color(0xFF8A8F96),
    clueText = InkLight,
    clueTextSatisfied = Color(0xFFB4B0A8),
    clueBackground = PaperLight,
    highlight = AccentTeal.copy(alpha = 0.10f),
    accent = AccentTeal,
)

private val DarkBoardColors = BoardColors(
    boardBackground = PaperDark,
    cellEmpty = Color(0xFF1E2226),
    cellFilled = Color(0xFFE6E4E0),
    cellCross = Color(0xFF6B737B),
    cellMistake = Color(0xFFF2B8B5),
    gridLine = Color(0xFF2E343A),
    gridLineMajor = Color(0xFF5A636B),
    clueText = InkDark,
    clueTextSatisfied = Color(0xFF555C63),
    clueBackground = PaperDark,
    highlight = AccentTealDark.copy(alpha = 0.14f),
    accent = AccentTealDark,
)

private val LightScheme = lightColorScheme(
    primary = AccentTeal,
    onPrimary = Color.White,
    surface = PaperLight,
    onSurface = InkLight,
    background = PaperLight,
    onBackground = InkLight,
    error = Color(0xFFB3261E),
)

private val DarkScheme = darkColorScheme(
    primary = AccentTealDark,
    onPrimary = Color(0xFF00363A),
    surface = PaperDark,
    onSurface = InkDark,
    background = PaperDark,
    onBackground = InkDark,
    error = Color(0xFFF2B8B5),
)

val LocalBoardColors = staticCompositionLocalOf { LightBoardColors }

/**
 * Typography note (plan 7): clue numbers need tabular figures, because proportional
 * digits make a column of clues look misaligned and broken. The canvas draws clues with
 * a monospace paint for exactly that reason.
 */
private val AppTypography = Typography(
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
    ),
)

@Composable
fun NonogramTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val boardColors = if (darkTheme) DarkBoardColors else LightBoardColors
    CompositionLocalProvider(LocalBoardColors provides boardColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = AppTypography,
            content = content,
        )
    }
}
