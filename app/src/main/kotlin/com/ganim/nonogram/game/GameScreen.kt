package com.ganim.nonogram.game

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onExit) { Text("‹ Back") }
            Spacer(Modifier.width(4.dp))
            Text(
                text = formatTime(elapsedSeconds),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Spacer(Modifier.width(12.dp))
            LivesIndicator(
                remaining = livesRemaining,
                total = GameState.MAX_LIVES,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${sizeLabel}x$sizeLabel $difficultyLabel",
                style = MaterialTheme.typography.labelLarge,
                color = colors.textMuted,
                // At 200% font scale this label would otherwise shove the timer and the
                // lives off the screen. It is the least important thing in the row, so
                // it is the one that gives way.
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
                textAlign = TextAlign.End,
            )
        }

        Spacer(Modifier.size(8.dp))

        // At large accessibility font scales three buttons no longer fit across the
        // screen, and squeezing them breaks "Undo" onto two lines inside its own button.
        // Past 1.5x they get a row each instead. Section 7 requires the app stay usable
        // at 200%, and usable means the controls still read as controls.
        val stacked = LocalDensity.current.fontScale >= STACK_CONTROLS_FONT_SCALE

        val modeButton: @Composable (Modifier) -> Unit = { mod ->
            // The mode toggle is the most-used control, so it leads and takes most room.
            FilledTonalButton(onClick = onToggleMode, modifier = mod) {
                Text(if (paintMode == PaintMode.FILL) "Fill" else "Cross", maxLines = 1)
            }
        }
        val undoButton: @Composable (Modifier) -> Unit = { mod ->
            OutlinedButton(onClick = onUndo, enabled = canUndo, modifier = mod) {
                Text("Undo", maxLines = 1)
            }
        }
        val hintButton: @Composable (Modifier) -> Unit = { mod ->
            OutlinedButton(onClick = onHint, enabled = isPlayable && !hintBusy, modifier = mod) {
                // Showing the count makes the free-hint economy legible instead of the
                // button silently turning into an ad prompt.
                Text(if (hintsRemaining > 0) "Hint $hintsRemaining" else "Hint", maxLines = 1)
            }
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
                modeButton(Modifier.weight(1.4f))
                undoButton(Modifier.weight(1f))
                hintButton(Modifier.weight(1f))
            }
        }

    }
}

/**
 * The three lives, drawn rather than typed.
 *
 * A heart character renders as a colour emoji on most Android builds, which is loud,
 * ignores the theme entirely, and reads as a casual game - the exact look section 7
 * tells us to avoid. Two circles do the job and inherit the palette.
 */
@Composable
private fun LivesIndicator(remaining: Int, total: Int) {
    val colors = LocalBoardColors.current
    val spent = colors.textMuted
    val alive = if (remaining <= 1) MaterialTheme.colorScheme.error else colors.accent

    Canvas(
        Modifier
            .height(20.dp)
            .width((total * 18).dp)
            .semantics { contentDescription = "$remaining of $total lives remaining" },
    ) {
        val radius = size.height / 2.6f
        val step = size.width / total
        repeat(total) { index ->
            val centre = Offset(step * index + step / 2f, size.height / 2f)
            if (index < remaining) {
                drawCircle(alive, radius, centre)
            } else {
                drawCircle(spent, radius, centre, style = Stroke(width = radius * 0.36f))
            }
        }
    }
}

@Composable
private fun ResultsCard(state: GameChrome, onNext: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // For a hand-drawn picture, the name is the payoff - the moment the grid
            // you just filled turns out to be a thing. It leads, and the stats follow.
            if (state.pictureName.isNotEmpty()) {
                Text("You drew", style = MaterialTheme.typography.labelLarge)
                Text(state.pictureName, style = MaterialTheme.typography.headlineSmall)
            } else {
                Text("Solved", style = MaterialTheme.typography.titleMedium)
            }
            Text(formatTime(state.elapsedSeconds), style = MaterialTheme.typography.titleMedium)
            Text(
                "${state.mistakes} mistakes  ·  ${state.difficultyLabel}",
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(4.dp))
            Button(onClick = onNext) { Text("Done") }
        }
    }
}

@Composable
private fun OutOfLivesCard(
    onRestoreLife: () -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Out of lives", style = MaterialTheme.typography.titleMedium)
            // Phase 5 puts a rewarded ad behind this. Until then it simply grants the
            // life, which is also the no-fill fallback the plan requires (8.2).
            Button(onClick = onRestoreLife) { Text("Restore a life") }
            OutlinedButton(onClick = onRestart) { Text("Start over") }
        }
    }
}

private fun formatTime(totalSeconds: Int): String =
    "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)

/** Past this font scale the control row is stacked instead of squeezed. */
private const val STACK_CONTROLS_FONT_SCALE = 1.5f
