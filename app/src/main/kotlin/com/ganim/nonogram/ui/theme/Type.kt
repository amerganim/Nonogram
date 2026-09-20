package com.ganim.nonogram.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Typography (build plan section 7).
 *
 * > "Typography: one clean sans-serif, tabular figures for clue numbers (misaligned clue
 * > digits look broken)."
 *
 * ## Tabular figures
 *
 * `fontFeatureSettings = "tnum"` forces every digit to the same advance width. It matters
 * anywhere numbers change in place: a timer ticking 1:09 to 1:10 visibly jitters with
 * proportional digits, and a column of streak counts looks ragged. The clue numbers on
 * the board get the same treatment a different way - `BoardCanvas` draws them with a
 * monospace paint, because it bypasses Compose text entirely for speed.
 *
 * ## Sizes are in sp
 *
 * All of them, so the system font scale applies. The plan requires the app to stay
 * usable at 200%, which means nothing here may be pinned in dp to "keep the layout
 * tidy" - a tidy layout that ignores the accessibility setting is not tidy, it is broken.
 */

private val TabularFigures = "tnum"

private val SansSerif = FontFamily.SansSerif

/** Trims the extra leading Compose adds, so dense rows do not drift apart. */
private val TightLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

val AppTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        lineHeightStyle = TightLineHeight,
        fontFeatureSettings = TabularFigures,
    ),
    titleMedium = TextStyle(
        fontFamily = SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        lineHeightStyle = TightLineHeight,
        fontFeatureSettings = TabularFigures,
    ),
    bodyMedium = TextStyle(
        fontFamily = SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        lineHeightStyle = TightLineHeight,
        fontFeatureSettings = TabularFigures,
    ),
    labelLarge = TextStyle(
        fontFamily = SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        lineHeightStyle = TightLineHeight,
        fontFeatureSettings = TabularFigures,
    ),
    labelMedium = TextStyle(
        fontFamily = SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        lineHeightStyle = TightLineHeight,
        fontFeatureSettings = TabularFigures,
    ),
)
