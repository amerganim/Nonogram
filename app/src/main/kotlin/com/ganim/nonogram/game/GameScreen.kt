package com.ganim.nonogram.game

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.ganim.nonogram.ui.components.Capsule
import com.ganim.nonogram.ui.components.GameIcon
import com.ganim.nonogram.ui.components.GhostButton
import com.ganim.nonogram.ui.components.Glyph
import com.ganim.nonogram.ui.components.PrimaryButton
import com.ganim.nonogram.ui.components.ScoreRow
import com.ganim.nonogram.ui.theme.LocalBoardColors
import com.ganim.nonogram.ui.theme.LocalReduceMotion
import com.ganim.nonogram.ui.theme.Motion

/**
 * The play screen (build plan 5).
 *
 * Board first: the controls are a single compact strip so the grid gets essentially the
 * whole screen. The plan is explicit that this is where players spend all their engaged
 * time and that polish here is the product's only competitive advantage.
 */
@Composable
fun GameScreen(
    viewModel: GameViewModel,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    /** Offers a rewarded ad and reports whether the reward was granted (8.2). */
    onWatchAdForHint: suspend () -> Boolean = { false },
    /** Offers a rewarded ad to restore a life (8.2). */
    onWatchAdForLife: suspend () -> Boolean = { false },
    /** Called when the results card is dismissed - the only interstitial moment (8.2). */
    onResultsDismissed: suspend () -> Unit = {},
    hintsRemaining: Int = 0,
) {
    // Held as State, not read with `by`. Reading the board during composition is what
    // made every painted cell recompose the whole screen; the canvas reads it in the
    // draw phase instead. Only the coarse values below are read here, each behind a
    // derivedStateOf so the toolbar recomposes when the timer ticks, not when a cell
    // changes.
    val boardState = viewModel.state.collectAsStateWithLifecycle()
    val highlightState = viewModel.highlight.collectAsStateWithLifecycle()
    val state by remember { derivedStateOf { boardState.value.chrome() } }
    val colors = LocalBoardColors.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var offeringAdForHint by remember { mutableStateOf(false) }

    // Build plan 5.2: haptics on every cell state change, disableable in settings.
    LaunchedEffect(viewModel) {
        viewModel.haptics.collect { kind ->
            val constant = when (kind) {
                HapticKind.CELL -> HapticFeedbackConstants.CLOCK_TICK
                HapticKind.CONFIRM -> HapticFeedbackConstants.KEYBOARD_TAP
                HapticKind.MISTAKE -> HapticFeedbackConstants.LONG_PRESS
            }
            view.performHapticFeedback(constant)
        }
    }

    var viewportWidth by remember { mutableStateOf(0) }
    var viewportHeight by remember { mutableStateOf(0) }
    var zoom by remember(state.puzzleId) { mutableStateOf(1f) }
    var pan by remember(state.puzzleId) { mutableStateOf(Offset.Zero) }

    val metrics = remember(state.puzzleId, viewportWidth, viewportHeight, zoom, pan) {
        if (viewportWidth == 0 || viewportHeight == 0) {
            BoardMetrics.fit(state.width, state.height, 1, 1, 1f, 1f)
        } else {
            BoardMetrics.fit(
                columns = state.width,
                rows = state.height,
                longestRowClue = state.longestRowClue,
                longestColClue = state.longestColClue,
                viewportWidth = viewportWidth.toFloat(),
                viewportHeight = viewportHeight.toFloat(),
                zoom = zoom,
                panX = pan.x,
                panY = pan.y,
            )
        }
    }

    // Completion (5.3): the clue gutters fade out so the revealed picture stands on its
    // own, and only then does the results card arrive. Sequencing them rather than
    // running both at once is what makes the moment read as a reveal.
    val reduceMotion = LocalReduceMotion.current
    val gutterAlpha = remember { Animatable(1f) }
    val resultsReveal = remember { Animatable(0f) }
    LaunchedEffect(state.status, reduceMotion) {
        if (state.status == GameStatus.COMPLETE) {
            gutterAlpha.animateTo(0f, tween(Motion.duration(Motion.STANDARD, reduceMotion)))
            resultsReveal.animateTo(1f, tween(Motion.duration(Motion.STANDARD, reduceMotion)))
        } else {
            resultsReveal.snapTo(0f)
            gutterAlpha.snapTo(1f)
        }
    }

    Column(modifier.fillMaxSize().background(colors.boardBackground).safeDrawingPadding()) {
        // Only the fields the toolbar actually shows are passed in, as primitives.
        // Handing it the whole GameState made it recompose on every painted cell during
        // a drag - three buttons, two text nodes and a Canvas, sixty times a second, for
        // values that had not changed. Measured on a Galaxy A15 that was a large part of
        // the 20x20 drag cost.
        GameToolbar(
            elapsedSeconds = state.elapsedSeconds,
            livesRemaining = state.livesRemaining,
            paintMode = state.paintMode,
            canUndo = state.canUndo,
            isPlayable = state.isPlayable,
            sizeLabel = state.width,
            difficultyLabel = state.difficultyLabel,
            onToggleMode = viewModel::toggleMode,
            onUndo = viewModel::undo,
            onHint = {
                scope.launch {
                    when (viewModel.useHint()) {
                        // Out of hints: offer a rewarded ad. If no ad is available the
                        // reward is granted anyway (8.2), so this never dead-ends.
                        HintResult.NeedsMoreHints -> {
                            offeringAdForHint = true
                            if (onWatchAdForHint()) viewModel.useRewardedHint()
                            offeringAdForHint = false
                        }
                        HintResult.Revealed, HintResult.NothingToReveal -> Unit
                    }
                }
            },
            hintsRemaining = hintsRemaining,
            hintBusy = offeringAdForHint,
            onExit = onExit,
        )

        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged {
                    viewportWidth = it.width
                    viewportHeight = it.height
                }
                .boardInput(
                    metrics = { metrics },
                    enabled = state.isPlayable,
                    restartKey = state.puzzleId,
                    onTap = viewModel::onTap,
                    onLongPress = { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) },
                    onDragStart = viewModel::onDragStart,
                    onDragTo = viewModel::onDragTo,
                    onDragEnd = viewModel::onDragEnd,
                    onHighlight = viewModel::onHighlight,
                    onTransform = { zoomDelta, panDelta ->
                        // Re-clamp the pan against the *new* zoom, so zooming out never
                        // leaves the board parked off-screen.
                        val zoomed = metrics.withZoom(zoom * zoomDelta, BoardMetrics.baseCellSize(metrics))
                        val panned = zoomed.withPan(pan.x + panDelta.x, pan.y + panDelta.y)
                        zoom = panned.zoom
                        pan = Offset(panned.panX, panned.panY)
                    },
                ),
        ) {
            BoardCanvas(
                boardState = boardState,
                metrics = metrics,
                highlightState = highlightState,
                gutterAlpha = gutterAlpha.value,
                modifier = Modifier.fillMaxSize(),
            )

            // Composes nothing unless a screen reader is exploring by touch (9).
            BoardAccessibilityOverlay(
                boardState = boardState,
                metrics = metrics,
                onCellActivated = viewModel::onTap,
                modifier = Modifier.fillMaxSize(),
            )

            if (resultsReveal.value > 0.01f) {
                ResultsCard(
                    state = state,
                    onNext = {
                        // The interstitial placement from 8.2 - on results dismiss,
                        // never mid-puzzle. AdPolicy decides whether one actually shows.
                        scope.launch {
                            onResultsDismissed()
                            onExit()
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer {
                            alpha = resultsReveal.value
                            val scale = 0.92f + 0.08f * resultsReveal.value
                            scaleX = scale
                            scaleY = scale
                        },
                )
            }

            if (state.status == GameStatus.OUT_OF_LIVES) {
                OutOfLivesCard(
                    onRestoreLife = {
                        scope.launch {
                            // Watching restores a life; a no-fill restores it too.
                            onWatchAdForLife()
                            viewModel.restoreLife()
                        }
                    },
                    onRestart = viewModel::restart,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

@Composable
private fun GameToolbar(
    elapsedSeconds: Int,
    livesRemaining: Int,
    paintMode: PaintMode,
    canUndo: Boolean,
    isPlayable: Boolean,
    sizeLabel: Int,
    difficultyLabel: String,
    onToggleMode: () -> Unit,
    onUndo: () -> Unit,
    onHint: () -> Unit,
    onExit: () -> Unit,
    hintsRemaining: Int,
    hintBusy: Boolean,
) {
    val colors = LocalBoardColors.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.stroke, RoundedCornerShape(15.dp))
                    .clickable(onClick = onExit),
                contentAlignment = Alignment.Center,
            ) {
                GameIcon(Glyph.BACK, colors.clueText, size = 19.dp, contentDescription = "Back")
            }

            Capsule(Glyph.CLOCK, formatTime(elapsedSeconds), colors.textMuted)

            Spacer(Modifier.weight(1f))

            LivesIndicator(remaining = livesRemaining, total = GameState.MAX_LIVES)
        }

        Spacer(Modifier.size(10.dp))

        // At large accessibility font scales four buttons no longer fit across the
        // screen, and squeezing them breaks a label onto two lines inside its own
        // button. Past 1.5x they get a row each instead. Section 7 requires the app stay
        // usable at 200%, and usable means the controls still read as controls.
        val stacked = LocalDensity.current.fontScale >= STACK_CONTROLS_FONT_SCALE

        val modeButton: @Composable (Modifier) -> Unit = { mod ->
            // The mode toggle is the most-used control, so it leads, takes most room,
            // and is the only one wearing the accent: at a glance you can tell what a
            // tap is about to write.
            val filling = paintMode == PaintMode.FILL
            ToolButton(
                label = if (filling) "Fill" else "Cross",
                glyph = if (filling) Glyph.SQUARE else Glyph.CROSS,
                onClick = onToggleMode,
                enabled = isPlayable,
                active = true,
                modifier = mod,
            )
        }
        val undoButton: @Composable (Modifier) -> Unit = { mod ->
            ToolButton("Undo", Glyph.UNDO, onUndo, enabled = canUndo, modifier = mod)
        }
        val hintButton: @Composable (Modifier) -> Unit = { mod ->
            ToolButton(
                label = "Hint",
                glyph = Glyph.SPARK,
                onClick = onHint,
                enabled = isPlayable && !hintBusy,
                // Showing the count makes the free-hint economy legible instead of the
                // button silently turning into an ad prompt.
                badge = hintsRemaining.takeIf { it > 0 }?.toString(),
                modifier = mod,
            )
        }

        if (stacked) {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                modeButton(Modifier.fillMaxWidth())
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    undoButton(Modifier.weight(1f))
                    hintButton(Modifier.weight(1f))
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                modeButton(Modifier.weight(1.3f))
                undoButton(Modifier.weight(1f))
                hintButton(Modifier.weight(1f))
            }
        }
    }
}

/**
 * One control in the play toolbar.
 *
 * [active] wears the accent; everything else is a quiet outlined key. Only one control
 * may be active at a time, which is what lets the toolbar answer "what will a tap do?"
 * without being read.
 */
@Composable
private fun ToolButton(
    label: String,
    glyph: Glyph,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    badge: String? = null,
) {
    val colors = LocalBoardColors.current
    val shape = RoundedCornerShape(16.dp)
    val face = when {
        !enabled -> colors.surface
        active -> colors.accentFill
        else -> colors.surface
    }
    val content = when {
        !enabled -> colors.stroke
        active -> colors.onAccentFill
        else -> colors.clueText
    }
    Row(
        modifier
            .height(48.dp)
            .clip(shape)
            .background(face)
            // The accent edge belongs to a live accent face. A disabled control that
            // keeps it still looks pressable, which is the thing the bevel rules are
            // trying to avoid.
            .border(1.dp, if (active && enabled) colors.accentDeep else colors.stroke, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameIcon(glyph, content, size = 18.dp)
        Text(label, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
        if (badge != null) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(colors.success)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            ) {
                Text(badge, style = MaterialTheme.typography.labelMedium, color = colors.onAccentFill)
            }
        }
    }
}

/**
 * The three lives.
 *
 * A heart character renders as a colour emoji on most Android builds, which is loud,
 * ignores the theme entirely and looks nothing like the rest of the app. This is the
 * same path the icon set uses, so it inherits the palette.
 *
 * A spent life keeps its outline rather than vanishing: "one left" and "one of three
 * left" are different pieces of information, and only the second says how much trouble
 * you are in.
 */
@Composable
private fun LivesIndicator(remaining: Int, total: Int) {
    val colors = LocalBoardColors.current
    Row(
        Modifier.semantics { contentDescription = "$remaining of $total lives remaining" },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(total) { index ->
            // Solid for a life you still have, outline for one you spent: at 21dp
            // an outline alone is easy to miscount at a glance, and a glance is
            // the only look this gets mid-drag.
            GameIcon(
                glyph = if (index < remaining) Glyph.HEART_SOLID else Glyph.HEART,
                tint = if (index < remaining) colors.cellMistake else colors.stroke,
                size = 21.dp,
            )
        }
    }
}

@Composable
private fun ResultsCard(state: GameChrome, onNext: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalBoardColors.current
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier
            .clip(shape)
            .background(colors.raised)
            .border(1.dp, colors.stroke, shape)
            .padding(horizontal = 24.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // The card sits over the board, so it shows the finished picture itself -
        // without the grid lines, clue gutters and half-drawn crosses that were
        // scaffolding for solving it and are clutter now that it is solved.
        SolvedPicture(state)

        // For a hand-drawn picture, the name is the payoff - the moment the grid you
        // just filled turns out to be a thing. It leads, and the stats follow.
        if (state.pictureName.isNotEmpty()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "YOU DREW",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted,
                )
                Text(
                    state.pictureName,
                    style = MaterialTheme.typography.displaySmall,
                    color = colors.clueText,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            Text("Solved", style = MaterialTheme.typography.headlineSmall, color = colors.clueText)
        }

        ScoreRow(
            listOf(
                Triple("Time", formatTime(state.elapsedSeconds), colors.clueText),
                Triple(
                    "Mistakes",
                    state.mistakes.toString(),
                    if (state.mistakes == 0) colors.success else colors.cellMistake,
                ),
                Triple("Level", state.difficultyLabel, colors.info),
            ),
        )

        PrimaryButton("Done", onNext, Modifier.fillMaxWidth(), glyph = Glyph.CHECK)
    }
}

/** The completed grid, drawn plainly, at a size that fits inside the results card. */
@Composable
private fun SolvedPicture(state: GameChrome) {
    val colors = LocalBoardColors.current
    Canvas(
        Modifier
            .size(132.dp)
            .semantics {
                contentDescription = if (state.pictureName.isNotEmpty()) {
                    "The finished picture: ${state.pictureName}"
                } else {
                    "The finished picture"
                }
            },
    ) {
        val cell = minOf(size.width / state.width, size.height / state.height)
        val originX = (size.width - cell * state.width) / 2f
        val originY = (size.height - cell * state.height) / 2f
        for (row in 0 until state.height) {
            for (col in 0 until state.width) {
                if (!state.solution[row * state.width + col]) continue
                drawRect(
                    colors.cellFilled,
                    Offset(originX + col * cell, originY + row * cell),
                    Size(cell, cell),
                )
            }
        }
    }
}

@Composable
private fun OutOfLivesCard(
    onRestoreLife: () -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBoardColors.current
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier
            .clip(shape)
            .background(colors.raised)
            .border(1.dp, colors.stroke, shape)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(GameState.MAX_LIVES) { GameIcon(Glyph.HEART, colors.stroke, size = 26.dp) }
        }
        Text("Out of lives", style = MaterialTheme.typography.headlineSmall, color = colors.clueText)
        Text(
            "Every square you got right is still there.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
            textAlign = TextAlign.Center,
        )
        // Phase 5 puts a rewarded ad behind this. It grants the life either way, which
        // is also the no-fill fallback the plan requires (8.2).
        PrimaryButton(
            text = "Restore a life",
            onClick = onRestoreLife,
            modifier = Modifier.fillMaxWidth(),
            glyph = Glyph.HEART,
            face = colors.cellMistake,
            bevel = colors.stroke,
            onFace = colors.onAccent,
        )
        GhostButton("Start over", onRestart, Modifier.fillMaxWidth(), glyph = Glyph.UNDO)
    }
}

private fun formatTime(totalSeconds: Int): String =
    "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)

/** Past this font scale the control row is stacked instead of squeezed. */
private const val STACK_CONTROLS_FONT_SCALE = 1.5f
