package com.ganim.nonogram.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Typography.
 *
 * ## Two faces, and why the display one earns its place
 *
 * The plan asked for "one clean sans-serif". That is right for a puzzle book and wrong
 * for a game: a game's numbers have presence. So there are two.
 *
 * - **Fredoka** for display - the timer, the streak, the screen titles, the picture
 *   reveal. Its rounded terminals do the game-feel work, which is precisely what lets
 *   the board itself stay disciplined. Without it the colour would have to carry the
 *   whole personality, and colour inside a nonogram grid is a bad idea.
 * - **Outfit** for body - labels, captions, buttons. Open apertures, so it survives at
 *   12sp in a dense row.
 *
 * ## Downloadable, with a real fallback
 *
 * Both come from the Play Services font provider rather than being committed as binary
 * files: it keeps the APK small and avoids vendoring 500 KB of TTF into git. The cost is
 * that a device without Play Services, or a cold start with no network, gets the system
 * sans instead. Nothing here depends on the download landing: sizes are chosen so the
 * fallback fits the same boxes, because the app has to be legible on the first frame of
 * the first launch, which is exactly when the font is least likely to have arrived.
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
 * All of them, so the system font scale applies. The app has to stay usable at 200%,
 * which means nothing here may be pinned in dp to "keep the layout tidy" - a tidy layout
 * that ignores the accessibility setting is not tidy, it is broken.
 */

private const val TabularFigures = "tnum"

private val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    // The app uses non-transitive R classes, so the certificate list is named from
    // the library that ships it rather than through the app's own R.
    certificates = androidx.compose.ui.text.googlefonts.R.array.com_google_android_gms_fonts_certs,
)

private val Fredoka = GoogleFont("Fredoka")
private val Outfit = GoogleFont("Outfit")

/**
 * Display face.
 *
 * If the provider cannot supply it, Compose's resolver falls through to the platform
 * default and the layout still holds - every size here is set in sp against metrics the
 * system sans also satisfies, so a failed download costs the personality, not the
 * screen.
 */
val DisplayFamily = FontFamily(
    Font(googleFont = Fredoka, fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = Fredoka, fontProvider = fontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = Fredoka, fontProvider = fontProvider, weight = FontWeight.Bold),
)

val BodyFamily = FontFamily(
    Font(googleFont = Outfit, fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = Outfit, fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = Outfit, fontProvider = fontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = Outfit, fontProvider = fontProvider, weight = FontWeight.Bold),
)

/** Trims the extra leading Compose adds, so dense rows do not drift apart. */
private val TightLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

val AppTypography = Typography(
    // The reveal: "Heart", the streak number, a screen title.
    displaySmall = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        lineHeightStyle = TightLineHeight,
        fontFeatureSettings = TabularFigures,
    ),
    headlineSmall = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 25.sp,
        lineHeight = 31.sp,
        lineHeightStyle = TightLineHeight,
        fontFeatureSettings = TabularFigures,
    ),
    // Stat tiles and the timer: display face, because these are numbers first.
    titleLarge = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        lineHeightStyle = TightLineHeight,
        fontFeatureSettings = TabularFigures,
    ),
    titleMedium = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        lineHeightStyle = TightLineHeight,
        fontFeatureSettings = TabularFigures,
    ),
    titleSmall = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        lineHeightStyle = TightLineHeight,
        fontFeatureSettings = TabularFigures,
    ),
    bodyLarge = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp,
        lineHeightStyle = TightLineHeight,
        fontFeatureSettings = TabularFigures,
    ),
    bodyMedium = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        lineHeightStyle = TightLineHeight,
        fontFeatureSettings = TabularFigures,
    ),
    labelLarge = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        lineHeightStyle = TightLineHeight,
        fontFeatureSettings = TabularFigures,
    ),
    labelMedium = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        lineHeightStyle = TightLineHeight,
        fontFeatureSettings = TabularFigures,
    ),
)
