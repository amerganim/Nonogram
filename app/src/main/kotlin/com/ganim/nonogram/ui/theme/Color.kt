package com.ganim.nonogram.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Every colour the app draws with (build plan section 7).
 *
 * > "Calm, precise, uncluttered. Generous whitespace, one confident accent colour,
 * > high-contrast grid. The reference point is a well-made physical puzzle book, not a
 * > casual game with gradients and bubble letters."
 *
 * So: warm off-white paper and near-black ink in light, deep neutral and bone-white in
 * dark, and exactly one accent - a deep teal. No gradients. Stock Material purple is
 * deliberately absent.
 *
 * This file is the only place a colour literal may appear. `ThemePurityTest` fails the
 * build if one shows up anywhere else, because a stray `Color(0xFF...)` in a screen is
 * invisible until someone switches to dark mode and finds black text on black.
 *
 * Every pair here is contrast-checked by `ThemeContrastTest` against WCAG AA, which is
 * the Phase 4 acceptance criterion made executable rather than asserted.
 */

// --- light: paper and ink ---------------------------------------------------------------

private val PaperLight = Color(0xFFFAF9F7)
private val SurfaceLight = Color(0xFFFFFFFF)
private val InkLight = Color(0xFF16191C)
private val InkMutedLight = Color(0xFF585F66)
private val AccentLight = Color(0xFF00696E)
private val OnAccentLight = Color(0xFFFFFFFF)
private val MistakeLight = Color(0xFFA8201A)
private val CrossLight = Color(0xFF6B7178)
private val GridMinorLight = Color(0xFFDAD6CF)
private val GridMajorLight = Color(0xFF6E747A)
private val ClueDoneLight = Color(0xFF697077)

// --- dark: not an afterthought ------------------------------------------------------------
// The plan warns that dark is the default for many puzzle players, so it is specified
// outright rather than derived by inverting the light palette.

private val PaperDark = Color(0xFF121517)
private val SurfaceDark = Color(0xFF1A1E21)
private val InkDark = Color(0xFFE8E5E1)
private val InkMutedDark = Color(0xFFA7AEB5)
private val AccentDark = Color(0xFF5BD9DF)
private val OnAccentDark = Color(0xFF00363A)
private val MistakeDark = Color(0xFFF2B8B5)
private val CrossDark = Color(0xFF7C858D)
private val GridMinorDark = Color(0xFF2C3237)
private val GridMajorDark = Color(0xFF69737C)
private val ClueDoneDark = Color(0xFF7E868E)

/**
 * Colours Material's [androidx.compose.material3.ColorScheme] has no slot for.
 *
 * A filled cell, a cross, a wrong cell and a satisfied clue are not surfaces or
 * containers, so forcing them into Material roles would mean picking a role for its
 * colour rather than its meaning - and then Phase 7's restyling breaks the board.
 */
@Immutable
data class BoardColors(
    val boardBackground: Color,
    val surface: Color,
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
    val onAccent: Color,
    val textMuted: Color,
)

val LightBoardColors = BoardColors(
    boardBackground = PaperLight,
    surface = SurfaceLight,
    cellEmpty = SurfaceLight,
    cellFilled = InkLight,
    cellCross = CrossLight,
    cellMistake = MistakeLight,
    gridLine = GridMinorLight,
    gridLineMajor = GridMajorLight,
    clueText = InkLight,
    clueTextSatisfied = ClueDoneLight,
    clueBackground = PaperLight,
    // Alpha is low on purpose: the plan asks for a *subtle* row/column highlight, and
    // anything stronger competes with the filled cells for attention.
    highlight = AccentLight.copy(alpha = 0.10f),
    accent = AccentLight,
    onAccent = OnAccentLight,
    textMuted = InkMutedLight,
)

val DarkBoardColors = BoardColors(
    boardBackground = PaperDark,
    surface = SurfaceDark,
    cellEmpty = SurfaceDark,
    cellFilled = InkDark,
    cellCross = CrossDark,
    cellMistake = MistakeDark,
    gridLine = GridMinorDark,
    gridLineMajor = GridMajorDark,
    clueText = InkDark,
    clueTextSatisfied = ClueDoneDark,
    clueBackground = PaperDark,
    highlight = AccentDark.copy(alpha = 0.14f),
    accent = AccentDark,
    onAccent = OnAccentDark,
    textMuted = InkMutedDark,
)

internal val LightScheme = lightColorScheme(
    primary = AccentLight,
    onPrimary = OnAccentLight,
    secondary = InkMutedLight,
    onSecondary = SurfaceLight,
    surface = SurfaceLight,
    onSurface = InkLight,
    surfaceVariant = PaperLight,
    onSurfaceVariant = InkMutedLight,
    background = PaperLight,
    onBackground = InkLight,
    error = MistakeLight,
    onError = SurfaceLight,
    outline = GridMajorLight,
)

internal val DarkScheme = darkColorScheme(
    primary = AccentDark,
    onPrimary = OnAccentDark,
    secondary = InkMutedDark,
    onSecondary = PaperDark,
    surface = SurfaceDark,
    onSurface = InkDark,
    surfaceVariant = PaperDark,
    onSurfaceVariant = InkMutedDark,
    background = PaperDark,
    onBackground = InkDark,
    error = MistakeDark,
    onError = PaperDark,
    outline = GridMajorDark,
)

/**
 * Colour pairs the contrast test checks, named so a failure says which one broke.
 *
 * Text needs 4.5:1 for AA; a UI component or graphical object needs 3:1. Satisfied clue
 * text is held to the full 4.5 even though it is deliberately de-emphasised - it is
 * still something the player reads, and the plan asks for AA "for all text".
 */
internal fun contrastPairs(colors: BoardColors): List<ContrastPair> = listOf(
    ContrastPair("clue text on board", colors.clueText, colors.boardBackground, 4.5),
    ContrastPair("clue text on surface", colors.clueText, colors.surface, 4.5),
    ContrastPair("satisfied clue on board", colors.clueTextSatisfied, colors.boardBackground, 4.5),
    ContrastPair("satisfied clue on surface", colors.clueTextSatisfied, colors.surface, 4.5),
    ContrastPair("muted text on board", colors.textMuted, colors.boardBackground, 4.5),
    ContrastPair("muted text on surface", colors.textMuted, colors.surface, 4.5),
    ContrastPair("accent on board", colors.accent, colors.boardBackground, 4.5),
    ContrastPair("accent on surface", colors.accent, colors.surface, 4.5),
    ContrastPair("text on accent", colors.onAccent, colors.accent, 4.5),
    ContrastPair("mistake on board", colors.cellMistake, colors.boardBackground, 4.5),
    // Graphical, not text.
    ContrastPair("filled cell against empty cell", colors.cellFilled, colors.cellEmpty, 3.0),
    ContrastPair("cross against empty cell", colors.cellCross, colors.cellEmpty, 3.0),
    ContrastPair("major grid line on empty cell", colors.gridLineMajor, colors.cellEmpty, 3.0),
)

internal data class ContrastPair(
    val name: String,
    val foreground: Color,
    val background: Color,
    val minimumRatio: Double,
)
