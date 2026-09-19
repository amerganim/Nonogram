package com.ganim.nonogram.game

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ganim.nonogram.ui.theme.LocalBoardColors
import kotlin.math.roundToInt

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
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val highlight by viewModel.highlight.collectAsStateWithLifecycle()
    val colors = LocalBoardColors.current
    val view = LocalView.current

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
    var zoom by remember(state.puzzle.id) { mutableStateOf(1f) }
    var pan by remember(state.puzzle.id) { mutableStateOf(Offset.Zero) }

    val metrics = remember(state.puzzle.id, viewportWidth, viewportHeight, zoom, pan) {
        if (viewportWidth == 0 || viewportHeight == 0) {
            BoardMetrics.fit(state.width, state.height, 1, 1, 1f, 1f)
        } else {
            BoardMetrics.fit(
                columns = state.width,
                rows = state.height,
                longestRowClue = state.puzzle.rowClues.maxOf { it.values.size }.coerceAtLeast(1),
                longestColClue = state.puzzle.colClues.maxOf { it.values.size }.coerceAtLeast(1),
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
    val gutterAlpha = remember { Animatable(1f) }
    val resultsReveal = remember { Animatable(0f) }
    LaunchedEffect(state.status) {
        if (state.status == GameStatus.COMPLETE) {
            gutterAlpha.animateTo(0f, tween(GUTTER_FADE_MS))
            resultsReveal.animateTo(1f, tween(RESULTS_FADE_MS))
        } else {
            resultsReveal.snapTo(0f)
            gutterAlpha.snapTo(1f)
        }
    }

    Column(modifier.fillMaxSize().background(colors.boardBackground).safeDrawingPadding()) {
        GameToolbar(
            state = state,
            onToggleMode = viewModel::toggleMode,
            onUndo = viewModel::undo,
            onHint = { viewModel.useHint() },
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
                    restartKey = state.puzzle.id,
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
                state = state,
                metrics = metrics,
                highlight = highlight,
                gutterAlpha = gutterAlpha.value,
                modifier = Modifier.fillMaxSize(),
            )

            if (resultsReveal.value > 0.01f) {
                ResultsCard(
                    state = state,
                    onNext = onExit,
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
                    onRestoreLife = viewModel::restoreLife,
                    onRestart = viewModel::restart,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

@Composable
private fun GameToolbar(
    state: GameState,
    onToggleMode: () -> Unit,
    onUndo: () -> Unit,
    onHint: () -> Unit,
    onExit: () -> Unit,
) {
    val colors = LocalBoardColors.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onExit) { Text("‹ Back") }
            Spacer(Modifier.width(4.dp))
            Text(
                text = formatTime(state.elapsedMs),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "♥".repeat(state.livesRemaining) + "♡".repeat(GameState.MAX_LIVES - state.livesRemaining),
                color = if (state.livesRemaining <= 1) MaterialTheme.colorScheme.error else colors.clueText,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${state.width}x${state.height} ${state.puzzle.difficulty.name.lowercase()}",
                style = MaterialTheme.typography.labelLarge,
                color = colors.clueTextSatisfied,
            )
        }

        Spacer(Modifier.size(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            // The mode toggle is the most-used control, so it is the widest and leftmost.
            FilledTonalButton(onClick = onToggleMode, modifier = Modifier.weight(1.4f)) {
                Text(if (state.paintMode == PaintMode.FILL) "Fill" else "Cross")
            }
            OutlinedButton(onClick = onUndo, enabled = state.canUndo, modifier = Modifier.weight(1f)) {
                Text("Undo")
            }
            OutlinedButton(onClick = onHint, enabled = state.isPlayable, modifier = Modifier.weight(1f)) {
                Text("Hint")
            }
        }

    }
}

@Composable
private fun ResultsCard(state: GameState, onNext: () -> Unit, modifier: Modifier = Modifier) {
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
            Text("Solved", style = MaterialTheme.typography.titleMedium)
            Text(formatTime(state.elapsedMs), style = MaterialTheme.typography.titleMedium)
            Text(
                "${GameState.MAX_LIVES - state.livesRemaining} mistakes  ·  " +
                    state.puzzle.difficulty.name.lowercase(),
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

private fun formatTime(elapsedMs: Long): String {
    val totalSeconds = (elapsedMs / 1000.0).roundToInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

/** Plan section 7: every animation under 300ms. */
private const val GUTTER_FADE_MS = 260
private const val RESULTS_FADE_MS = 240
