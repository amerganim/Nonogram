package com.ganim.nonogram.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Every colour the app draws with.
 *
 * ## Arcade Night
 *
 * The build plan (section 7) asked for "a well-made physical puzzle book, not a casual
 * game", and that is what the first four phases built: paper, ink, one teal accent. It
 * was correct and it was quiet. The brief changed - this should feel like a game - so
 * the palette did too.
 *
 * What replaced it is not "the old palette with more saturation". It is **one colour per
 * meaning**, held to consistently enough that a glance tells you what a thing does
 * before you read it:
 *
 * - **accent** (gold on night, deep amber on paper) - the thing to press. Nothing else
 *   is this colour.
 * - **cellMistake** (coral) - what costs you something: a wrong square, a spent life, a
 *   streak about to break.
 * - **success** (mint) - what is confirmed and safe: a solved day, hints in hand, a
 *   crossed-off square being free.
 * - **info** (sky) - what is being explained: a hint, the line the tutorial is
 *   reasoning about.
 * - **collection** (violet) - the hand-drawn pictures, wherever they appear.
 *
 * ## The board stays quiet
 *
 * Everything above is chrome. Inside the grid there are two colours - filled and empty -
 * and a muted cross. A nonogram is *read*, not just looked at: a tinted square competes
 * with the clue beside it, and a player who miscounts because of decoration blames the
 * game. The colour is spent on the frame, not the picture.
 *
 * The filled square is whichever colour is furthest from the board behind it: gold on
 * night, indigo ink on paper. That asymmetry is deliberate - "highest contrast available"
 * is the rule, not "gold".
 *
 * ## The two rules this file lives under
 *
 * This is the only place a colour literal may appear. `ThemePurityTest` fails the build
 * if one shows up anywhere else, because a stray `Color(0xFF...)` in a screen is
 * invisible until someone switches theme and finds black text on black.
 *
 * Every pair below is contrast-checked by `ThemeContrastTest` against WCAG AA. Saturated
 * palettes fail that far more easily than quiet ones - the first draft of the light
 * theme used the same gold as dark, which is 1.5:1 on white and unreadable - so the
 * light accents are darkened versions of the same hues rather than the same hex values.
 */

// --- light: warm paper, indigo ink --------------------------------------------------------

private val PaperLight = Color(0xFFFDF6EA)
private val SurfaceLight = Color(0xFFFFFFFF)
private val RaisedLight = Color(0xFFFFFFFF)
private val StrokeLight = Color(0xFFEADDC6)
private val InkLight = Color(0xFF241C52)
private val InkMutedLight = Color(0xFF5C5570)
private val AccentLight = Color(0xFF9A5B00)
private val AccentDeepLight = Color(0xFF6E4100)
private val OnAccentLight = Color(0xFFFFFFFF)
private val SuccessLight = Color(0xFF12795A)
private val InfoLight = Color(0xFF0E6E8F)
private val CollectionLight = Color(0xFF6D3BBF)
private val MistakeLight = Color(0xFFC2372F)
private val CrossLight = Color(0xFF6E6683)
private val GridMinorLight = Color(0xFFE6DCCB)
private val GridMajorLight = Color(0xFF8A8098)
private val ClueDoneLight = Color(0xFF6E6683)

// --- dark: deep indigo, gold ---------------------------------------------------------------
// The plan warns that dark is the default for many puzzle players, so it is specified
// outright rather than derived by inverting the light palette. This is the direction's
// home ground: the light theme is the translation, not the other way round.

private val PaperDark = Color(0xFF140F2E)
private val SurfaceDark = Color(0xFF211A4A)
private val RaisedDark = Color(0xFF2C2460)
private val StrokeDark = Color(0xFF3C3180)
private val InkDark = Color(0xFFFFF3DC)
private val InkMutedDark = Color(0xFFADA3DE)
private val AccentDark = Color(0xFFFFC145)
private val AccentDeepDark = Color(0xFFC98A16)
private val OnAccentDark = Color(0xFF140F2E)
private val SuccessDark = Color(0xFF3DDC97)
private val InfoDark = Color(0xFF5AC8FA)
private val CollectionDark = Color(0xFFC77DFF)
private val MistakeDark = Color(0xFFFF6B6B)
private val CrossDark = Color(0xFF9A90DC)
private val CellEmptyDark = Color(0xFF2A2456)
private val GridMinorDark = Color(0xFF3A3172)
private val GridMajorDark = Color(0xFF9084E0)
private val ClueDoneDark = Color(0xFF9A90DC)

/**
 * Colours Material's [androidx.compose.material3.ColorScheme] has no slot for.
 *
 * A filled cell, a cross, a wrong cell and a satisfied clue are not surfaces or
 * containers, so forcing them into Material roles would mean picking a role for its
 * colour rather than its meaning - and then a restyling breaks the board.
 */
@Immutable
data class BoardColors(
    val boardBackground: Color,
    val surface: Color,
    /** A card that sits above [surface] - the daily hero, the results card. */
    val raised: Color,
    /** The hairline around a card. Carries the whole layout when the fills are this close. */
    val stroke: Color,
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
    /** Sits under a pressed-looking button as its bevel. Never used for text. */
    val accentDeep: Color,
    val onAccent: Color,
    /** Confirmed and safe: a solved day, a hint in hand, a free action. */
    val success: Color,
    /** Being explained: a hint, the line the tutorial is working on. */
    val info: Color,
    /** The hand-drawn picture collection, wherever it shows up. */
    val collection: Color,
    val textMuted: Color,
)

val LightBoardColors = BoardColors(
    boardBackground = PaperLight,
    surface = SurfaceLight,
    raised = RaisedLight,
    stroke = StrokeLight,
    cellEmpty = SurfaceLight,
    cellFilled = InkLight,
    cellCross = CrossLight,
    cellMistake = MistakeLight,
    gridLine = GridMinorLight,
    gridLineMajor = GridMajorLight,
    clueText = InkLight,
    clueTextSatisfied = ClueDoneLight,
    clueBackground = PaperLight,
    // Alpha is low on purpose: a *subtle* row/column highlight. Anything stronger
    // competes with the filled cells for attention, which is the one thing the board
    // may not do.
    highlight = InfoLight.copy(alpha = 0.12f),
    accent = AccentLight,
    accentDeep = AccentDeepLight,
    onAccent = OnAccentLight,
    success = SuccessLight,
    info = InfoLight,
    collection = CollectionLight,
    textMuted = InkMutedLight,
)

val DarkBoardColors = BoardColors(
    boardBackground = PaperDark,
    surface = SurfaceDark,
    raised = RaisedDark,
    stroke = StrokeDark,
    // Not the same as the surface: an empty square has to read as *part of the board*
    // rather than as a hole in it, which one step of lightness is enough to do.
    cellEmpty = CellEmptyDark,
    cellFilled = AccentDark,
    cellCross = CrossDark,
    cellMistake = MistakeDark,
    gridLine = GridMinorDark,
    gridLineMajor = GridMajorDark,
    clueText = InkDark,
    clueTextSatisfied = ClueDoneDark,
    clueBackground = PaperDark,
    highlight = InfoDark.copy(alpha = 0.16f),
    accent = AccentDark,
    accentDeep = AccentDeepDark,
    onAccent = OnAccentDark,
    success = SuccessDark,
    info = InfoDark,
    collection = CollectionDark,
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
    outline = StrokeLight,
)

internal val DarkScheme = darkColorScheme(
    primary = AccentDark,
    onPrimary = OnAccentDark,
    secondary = InkMutedDark,
    onSecondary = PaperDark,
    surface = SurfaceDark,
    onSurface = InkDark,
    surfaceVariant = RaisedDark,
    onSurfaceVariant = InkMutedDark,
    background = PaperDark,
    onBackground = InkDark,
    error = MistakeDark,
    onError = PaperDark,
    outline = StrokeDark,
)

/**
 * Colour pairs the contrast test checks, named so a failure says which one broke.
 *
 * Text needs 4.5:1 for AA; a UI component or graphical object needs 3:1. Satisfied clue
 * text is held to the full 4.5 even though it is deliberately de-emphasised - it is
 * still something the player reads.
 *
 * The four meaning colours are checked on all three backgrounds they actually land on.
 * A saturated palette makes it easy to pick a colour that reads beautifully on the page
 * ground and disappears on a card.
 */
internal fun contrastPairs(colors: BoardColors): List<ContrastPair> = listOf(
    ContrastPair("clue text on board", colors.clueText, colors.boardBackground, 4.5),
    ContrastPair("clue text on surface", colors.clueText, colors.surface, 4.5),
    ContrastPair("clue text on raised", colors.clueText, colors.raised, 4.5),
    ContrastPair("satisfied clue on board", colors.clueTextSatisfied, colors.boardBackground, 4.5),
    ContrastPair("satisfied clue on surface", colors.clueTextSatisfied, colors.surface, 4.5),
    ContrastPair("satisfied clue on raised", colors.clueTextSatisfied, colors.raised, 4.5),
    ContrastPair("muted text on board", colors.textMuted, colors.boardBackground, 4.5),
    ContrastPair("muted text on surface", colors.textMuted, colors.surface, 4.5),
    ContrastPair("muted text on raised", colors.textMuted, colors.raised, 4.5),
    ContrastPair("accent on board", colors.accent, colors.boardBackground, 4.5),
    ContrastPair("accent on surface", colors.accent, colors.surface, 4.5),
    ContrastPair("accent on raised", colors.accent, colors.raised, 4.5),
    ContrastPair("text on accent", colors.onAccent, colors.accent, 4.5),
    ContrastPair("mistake on board", colors.cellMistake, colors.boardBackground, 4.5),
    ContrastPair("mistake on surface", colors.cellMistake, colors.surface, 4.5),
    ContrastPair("success on board", colors.success, colors.boardBackground, 4.5),
    ContrastPair("success on surface", colors.success, colors.surface, 4.5),
    ContrastPair("info on board", colors.info, colors.boardBackground, 4.5),
    ContrastPair("info on surface", colors.info, colors.surface, 4.5),
    ContrastPair("collection on board", colors.collection, colors.boardBackground, 4.5),
    ContrastPair("collection on surface", colors.collection, colors.surface, 4.5),
    ContrastPair("collection on raised", colors.collection, colors.raised, 4.5),
    // Graphical, not text.
    ContrastPair("filled cell against empty cell", colors.cellFilled, colors.cellEmpty, 3.0),
    ContrastPair("cross against empty cell", colors.cellCross, colors.cellEmpty, 3.0),
    ContrastPair("mistake cell against empty cell", colors.cellMistake, colors.cellEmpty, 3.0),
    ContrastPair("major grid line on empty cell", colors.gridLineMajor, colors.cellEmpty, 3.0),
)

internal data class ContrastPair(
    val name: String,
    val foreground: Color,
    val background: Color,
    val minimumRatio: Double,
)
