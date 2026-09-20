package com.ganim.nonogram.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * The app theme (build plan section 7).
 *
 * Light and dark are both fully specified in [Color.kt]; nothing is derived by inverting
 * the other. Animation timing and the reduce-motion setting live here too, so a screen
 * never has to decide for itself how long something should take.
 */

val LocalBoardColors = staticCompositionLocalOf { LightBoardColors }

/**
 * True when the player has asked the system to reduce animation.
 *
 * Read from `ANIMATOR_DURATION_SCALE`, which is what the accessibility setting and
 * developer options both write to. Compose has no first-class API for this, and the
 * plan requires respecting it, so it is read directly and published here rather than
 * each animation reaching for a ContentResolver.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/**
 * Animation durations.
 *
 * > "All animations under 300ms. Respect the reduce-motion system setting."
 *
 * Going through [Motion.duration] rather than hard-coding a number at each call site is
 * what makes the reduce-motion setting actually work: one branch, applied everywhere.
 */
object Motion {
    /** A state change the eye should barely register - a colour, a fade. */
    const val QUICK = 140

    /** A deliberate transition, e.g. the clue gutters fading on completion. */
    const val STANDARD = 260

    /** The ceiling the plan sets. Nothing may exceed it. */
    const val MAXIMUM = 300

    /** Zero when the player has asked for reduced motion, so transitions land instantly. */
    fun duration(base: Int, reduceMotion: Boolean): Int = if (reduceMotion) 0 else base
}

@Composable
fun NonogramTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val reduceMotion = remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }

    CompositionLocalProvider(
        LocalBoardColors provides if (darkTheme) DarkBoardColors else LightBoardColors,
        LocalReduceMotion provides reduceMotion,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = AppTypography,
            content = content,
        )
    }
}
