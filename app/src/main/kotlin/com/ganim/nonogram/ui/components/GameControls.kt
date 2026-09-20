package com.ganim.nonogram.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ganim.nonogram.ui.theme.LocalBoardColors
import com.ganim.nonogram.ui.theme.LocalReduceMotion
import com.ganim.nonogram.ui.theme.Motion

/**
 * The controls every screen is built from.
 *
 * ## Why these exist rather than Material's
 *
 * Material's `Button` and `Card` are correct and characterless. A game's primary action
 * wants to look pressable before it is pressed, which is what the bevel below does: a
 * strip of the accent's darker shade showing under the face, so the button reads as a
 * physical key. On press the face drops onto the bevel and the key bottoms out.
 *
 * That is the whole trick, and it is worth one small component because it appears on
 * every screen. Everything else here - panels, chips, stat tiles, meters - is shared so
 * that a radius or a border weight is decided once instead of eight times.
 *
 * All colours come from `LocalBoardColors`; nothing in this file names one.
 */

private val ButtonShape = RoundedCornerShape(18.dp)
private val PanelShape = RoundedCornerShape(22.dp)

/** The bevel's depth. Also how far the face travels when pressed. */
private val Depth = 4.dp

/**
 * The one button a screen is steering you towards.
 *
 * Disabled loses the bevel as well as the colour: a key that still looks pressable but
 * does nothing is worse than one that plainly is not.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    glyph: Glyph? = Glyph.PLAY,
    face: Color? = null,
    bevel: Color? = null,
    onFace: Color? = null,
) {
    val colors = LocalBoardColors.current
    val reduceMotion = LocalReduceMotion.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val faceColor = if (enabled) face ?: colors.accentFill else colors.stroke
    val bevelColor = if (enabled) bevel ?: colors.accentDeep else colors.stroke
    val labelColor = if (enabled) onFace ?: colors.onAccentFill else colors.textMuted

    val drop by animateDpAsState(
        targetValue = if (pressed && enabled) Depth else 0.dp,
        animationSpec = tween(Motion.duration(Motion.QUICK, reduceMotion)),
        label = "buttonPress",
    )

    Box(modifier.height(56.dp + Depth)) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(56.dp)
                .clip(ButtonShape)
                .background(bevelColor),
        )
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = drop)
                .fillMaxWidth()
                .height(56.dp)
                .clip(ButtonShape)
                .background(faceColor)
                .clickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (glyph != null) GameIcon(glyph, labelColor, size = 20.dp)
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                color = labelColor,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** A secondary action: outlined, no bevel, never competing with the primary. */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    glyph: Glyph? = null,
    tint: Color? = null,
) {
    val colors = LocalBoardColors.current
    val content = if (enabled) tint ?: colors.textMuted else colors.stroke
    Row(
        modifier
            .height(50.dp)
            .clip(ButtonShape)
            .border(BorderStroke(1.dp, colors.stroke), ButtonShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) GameIcon(glyph, content, size = 18.dp)
        Text(text, style = MaterialTheme.typography.labelLarge, color = content)
    }
}

/**
 * A card.
 *
 * [tint] washes the panel in a meaning colour - violet for the picture collection, sky
 * for a hint - at an alpha low enough to stay a hint rather than a highlight.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    tint: Color? = null,
    shape: Shape = PanelShape,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScopeAlias.() -> Unit,
) {
    val colors = LocalBoardColors.current
    val fill = tint?.copy(alpha = TINT_FILL) ?: colors.surface
    val edge = tint?.copy(alpha = TINT_EDGE) ?: colors.stroke
    Column(
        modifier
            .clip(shape)
            .background(fill)
            .border(BorderStroke(1.dp, edge), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        content = content,
    )
}

/** Compose's own ColumnScope, aliased so callers of [Panel] read naturally. */
typealias ColumnScopeAlias = androidx.compose.foundation.layout.ColumnScope

/**
 * A small rounded label: a difficulty, a size, a state.
 *
 * Outlined only. The label is [color] and the fill is [color] at a low alpha, so any
 * palette colour passed here reads correctly against any surface - which is the point,
 * since callers pass difficulty tints, the collection violet and the info blue.
 */
@Composable
fun Chip(text: String, color: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .height(26.dp)
            .clip(shape)
            .background(color.copy(alpha = TINT_FILL))
            .border(BorderStroke(1.dp, color.copy(alpha = TINT_EDGE)), shape)
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/**
 * The solid chip, which is always the accent.
 *
 * Deliberately not an option on [Chip]. A solid chip needs a *bright* fill so its dark
 * label can read, and the first version let a caller pass any colour: the daily screen
 * passed `accent` - the light theme's dark orange - and shipped ink-on-dark-orange at
 * 2.88:1. The contrast test could not catch it, because the palette pair it checks
 * (`onAccentFill` on `accentFill`) was fine; only the call site was wrong. So the call
 * site no longer gets to choose.
 */
@Composable
fun AccentChip(text: String, modifier: Modifier = Modifier) {
    val colors = LocalBoardColors.current
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .height(26.dp)
            .clip(shape)
            .background(colors.accentFill)
            .border(BorderStroke(1.dp, colors.accentDeep), shape)
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = colors.onAccentFill)
    }
}

/** One number worth looking at, with the glyph that says what it counts. */
@Composable
fun StatTile(
    label: String,
    value: String,
    glyph: Glyph,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBoardColors.current
    Column(
        modifier
            .clip(PanelShape)
            .background(colors.surface)
            .border(BorderStroke(1.dp, colors.stroke), PanelShape)
            .padding(vertical = 13.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        GameIcon(glyph, tint, size = 19.dp)
        Text(value, style = MaterialTheme.typography.titleLarge, color = colors.clueText)
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = colors.textMuted,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * A progress meter.
 *
 * [fraction] is clamped rather than trusted: a count that briefly exceeds its total
 * during a state change should overfill nothing.
 */
@Composable
fun Meter(
    fraction: Float,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBoardColors.current
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .fillMaxWidth()
            .height(9.dp)
            .clip(shape)
            .background(colors.boardBackground)
            .border(BorderStroke(1.dp, colors.stroke), shape),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxSize()
                .clip(shape)
                .background(tint),
        )
    }
}

/** A circular glyph badge - a lives pip, a solved day, a count in a corner. */
@Composable
fun Pip(
    glyph: Glyph?,
    tint: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    diameter: androidx.compose.ui.unit.Dp = 32.dp,
    contentDescription: String? = null,
) {
    val colors = LocalBoardColors.current
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .size(diameter)
            .clip(shape)
            .background(if (filled) tint else tint.copy(alpha = TINT_FILL))
            .then(if (filled) Modifier else Modifier.border(BorderStroke(2.dp, tint), shape)),
        contentAlignment = Alignment.Center,
    ) {
        if (glyph != null) {
            GameIcon(
                glyph,
                if (filled) colors.onAccentFill else tint,
                size = diameter * 0.5f,
                contentDescription = contentDescription,
            )
        }
    }
}

/** A row of numbers and their labels, as used under the finished picture. */
@Composable
fun ScoreRow(entries: List<Triple<String, String, Color>>, modifier: Modifier = Modifier) {
    val colors = LocalBoardColors.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        entries.forEach { (label, value, tint) ->
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.surface)
                    .border(BorderStroke(1.dp, colors.stroke), RoundedCornerShape(18.dp))
                    .padding(vertical = 11.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(value, style = MaterialTheme.typography.titleMedium, color = tint)
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted,
                )
            }
        }
    }
}

/** A capsule holding a glyph and a value - the timer, the streak badge. */
@Composable
fun Capsule(
    glyph: Glyph,
    text: String,
    tint: Color,
    modifier: Modifier = Modifier,
    washed: Boolean = false,
) {
    val colors = LocalBoardColors.current
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .height(44.dp)
            .widthIn(min = 44.dp)
            .clip(shape)
            .background(if (washed) tint.copy(alpha = TINT_FILL) else colors.surface)
            .border(
                BorderStroke(1.dp, if (washed) tint.copy(alpha = TINT_EDGE) else colors.stroke),
                shape,
            )
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameIcon(glyph, tint, size = 18.dp)
        Text(text, style = MaterialTheme.typography.titleMedium, color = if (washed) tint else colors.clueText)
    }
}

/**
 * Alphas for a meaning colour used as a surface.
 *
 * Shared so that a violet panel and a sky panel are washed to the same strength; two
 * panels tinted by eye at different alphas look like a bug rather than a system.
 */
private const val TINT_FILL = 0.12f
private const val TINT_EDGE = 0.38f
