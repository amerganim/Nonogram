package com.ganim.nonogram.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's icon set, drawn rather than imported.
 *
 * Material's icon artifacts are deprecated out of Material 3, and a stock icon set would
 * pull the whole app towards the generic-app look. These are a dozen geometric marks on
 * a 24x24 grid - the same paths the design canvas uses, so the mockup and the app cannot
 * drift apart.
 *
 * They are stroked, not filled, except where a solid mark reads better at 18dp (the play
 * triangle, the fill-mode square). Stroke weight scales with the icon so a 14dp glyph in
 * a chip and a 24dp one in a toolbar look like the same family.
 */
enum class Glyph(val path: String, val filled: Boolean = false) {
    FLAME(
        "M12 2.6c3.7 3.9 6.1 6.6 6.1 10.3a6.1 6.1 0 0 1-12.2 0c0-2.4 1.2-4.3 3-6.4" +
            ".6 1.5 1.7 2 2.5 1.7-.4-1.6-1-3.7.6-5.6z",
    ),
    TROPHY(
        "M7 4h10v4.6a5 5 0 0 1-10 0z M7 5.4H4.6a2.6 2.6 0 0 0 2.6 4.4 " +
            "M17 5.4h2.4a2.6 2.6 0 0 1-2.6 4.4 M12 13.6V17 M8.6 20h6.8",
    ),
    CHECK("M4.5 12.4 9.6 17.6 19.6 6.6"),
    HEART("M12 20.2S4.6 15.4 4.6 10.5A3.9 3.9 0 0 1 12 8.2a3.9 3.9 0 0 1 7.4 2.3c0 4.9-7.4 9.7-7.4 9.7z"),
    CLOCK("M20.6 12a8.6 8.6 0 1 1-17.2 0 8.6 8.6 0 0 1 17.2 0z M12 6.8V12.2L15.8 14.2"),
    BACK("M14.6 4.8 7.6 12 14.6 19.2"),
    UNDO("M4.2 9.4h9.4a5.2 5.2 0 0 1 0 10.4H8.4 M8 4.8 3.4 9.4 8 14"),
    SPARK(
        "M11 3.2l1.9 4.7 4.7 1.9-4.7 1.9L11 16.4l-1.9-4.7L4.4 9.8l4.7-1.9z " +
            "M18.2 14.8l.8 2 2 .8-2 .8-.8 2-.8-2-2-.8 2-.8z",
    ),
    CROSS("M6.4 6.4 17.6 17.6 M17.6 6.4 6.4 17.6"),
    ARROW("M5 12h13.4 M13.4 6.8 18.6 12 13.4 17.2"),
    IMAGE(
        "M20.4 4.6H3.6v14.8h16.8z M10.8 10a1.8 1.8 0 1 1-3.6 0 1.8 1.8 0 0 1 3.6 0z " +
            "M20.4 16.2l-4.6-4.6-7.4 7.8",
    ),
    LOCK_OPEN("M7 11V7.6a5 5 0 0 1 9.6-1.9 M19.6 11H4.4v9.2h15.2z"),
    CALENDAR("M20.5 5h-17v15.5h17z M8 2.8v4 M16 2.8v4 M3.5 10.2h17"),
    GRID(
        "M11 3.6H3.6V11H11z M20.4 3.6H13V11h7.4z M11 13H3.6v7.4H11z M20.4 13H13v7.4h7.4z",
    ),
    SLIDERS(
        "M3.5 7h17 M3.5 12h17 M3.5 17h17 M11.6 7a2.6 2.6 0 1 1-5.2 0 2.6 2.6 0 0 1 5.2 0z " +
            "M18.1 12a2.6 2.6 0 1 1-5.2 0 2.6 2.6 0 0 1 5.2 0z " +
            "M10.1 17a2.6 2.6 0 1 1-5.2 0 2.6 2.6 0 0 1 5.2 0z",
    ),

    // Solid marks: at 18dp an outlined triangle or square turns to mush.
    PLAY("M8.4 5.2l11 6.8-11 6.8z", filled = true),
    SQUARE("M19.6 4.4H4.4v15.2h15.2z", filled = true),
    ;
}

/**
 * One glyph, at [size], in [tint].
 *
 * The path is parsed once per glyph and cached by `remember`, because re-parsing a path
 * string on every recomposition is exactly the kind of cost that only shows up as a
 * dropped frame somewhere unrelated.
 */
@Composable
fun GameIcon(
    glyph: Glyph,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    contentDescription: String? = null,
) {
    val path = remember(glyph) { PathParser().parsePathString(glyph.path).toPath() }
    val described = if (contentDescription != null) {
        modifier.semantics { this.contentDescription = contentDescription }
    } else {
        modifier
    }
    Canvas(described.then(Modifier.size(size))) {
        val factor = this.size.minDimension / VIEWPORT
        scale(factor, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            drawPath(
                path = path,
                color = tint,
                style = if (glyph.filled) {
                    Fill
                } else {
                    Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                },
            )
        }
    }
}

private const val VIEWPORT = 24f
