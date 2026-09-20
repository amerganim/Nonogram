package com.ganim.nonogram.ui.tutorial

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.ganim.nonogram.ui.components.GhostButton
import com.ganim.nonogram.ui.components.Glyph
import com.ganim.nonogram.ui.components.PrimaryButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ganim.nonogram.puzzle.tutorial.TutorialLesson
import com.ganim.nonogram.ui.theme.BoardColors
import com.ganim.nonogram.ui.theme.LocalBoardColors
import com.ganim.nonogram.ui.theme.LocalReduceMotion
import com.ganim.nonogram.ui.theme.Motion
import kotlinx.coroutines.delay

/**
 * How to play.
 *
 * ## Why this is an animation and not a page of rules
 *
 * Nonogram rules are short enough to write in a paragraph and useless in that form. The
 * thing a new player actually needs to see is *a deduction happening*: that the clue and
 * the squares already on the board leave exactly one possibility, and that this repeats
 * until the picture appears. Read as prose it sounds like arithmetic homework; watched
 * once, it clicks.
 *
 * So the screen plays a real solve of a real puzzle - [TutorialLesson], verified against
 * the solver so it cannot teach a move that is not forced - one deduction at a time, with
 * the reason on screen and the line being reasoned about highlighted. The rules and the
 * controls are underneath, for the player who wants them, rather than in front of the
 * one who does not.
 *
 * The board here is drawn by this file rather than reusing the game's `BoardCanvas`. The
 * real board is built for a 20x20 grid under a finger at 60fps and carries zoom, pan,
 * undo, mistakes and clue-greying with it; none of that applies to a 25-square
 * demonstration that nobody touches, and threading a "pretend" mode through it would
 * make the game's hot path carry tutorial concerns forever.
 */
@Composable
fun HowToPlayScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    doneLabel: String = "Start playing",
) {
    val colors = LocalBoardColors.current
    var stepIndex by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(true) }
    val lastStep = TutorialLesson.steps.lastIndex
    val step = TutorialLesson.steps[stepIndex]

    // Each step's new marks fade and scale in, so the eye is pulled to what just
    // changed rather than having to diff two static boards.
    val reduceMotion = LocalReduceMotion.current
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(stepIndex, reduceMotion) {
        reveal.snapTo(0f)
        reveal.animateTo(
            targetValue = 1f,
            animationSpec = tween(Motion.duration(Motion.MAXIMUM, reduceMotion), easing = LinearEasing),
        )
    }

    LaunchedEffect(stepIndex, playing) {
        if (!playing || stepIndex >= lastStep) return@LaunchedEffect
        // Reading time, not animation time: a caption with four marks needs longer on
        // screen than one with none.
        delay(1700L + 180L * step.moves.size + 22L * step.caption.length)
        stepIndex++
    }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.boardBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "How to play",
            style = MaterialTheme.typography.headlineSmall,
            color = colors.clueText,
        )
        Text(
            "Fill the squares the numbers describe. The finished grid is a picture.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
        )

        LessonBoard(
            stepIndex = stepIndex,
            reveal = reveal.value,
            colors = colors,
            modifier = Modifier
                .fillMaxWidth()
                // Tapping the board skips ahead. Someone who has understood step three
                // should not have to sit through step four.
                .clickable {
                    playing = false
                    stepIndex = if (stepIndex >= lastStep) 0 else stepIndex + 1
                },
        )

        // Fixed floor so the board does not jump up and down as captions change length.
        Box(Modifier.fillMaxWidth().heightIn(min = 76.dp)) {
            Text(
                step.caption,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.clueText,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        StepControls(
            stepIndex = stepIndex,
            lastStep = lastStep,
            playing = playing,
            colors = colors,
            onReplay = { stepIndex = 0; playing = true },
            onPlayPause = { playing = !playing },
            onBack = { playing = false; stepIndex = (stepIndex - 1).coerceAtLeast(0) },
        )

        Section("Controls", colors) {
            Bullet("Tap a square to fill it. Tap it again to clear it.", colors)
            Bullet(
                "Drag to paint a whole run at once. The first square you touch decides " +
                    "what the rest of the drag writes, and the line locks straight.",
                colors,
            )
            Bullet(
                "Hold, then drag, to mark empty squares without switching mode - or use " +
                    "the fill/cross toggle above the board.",
                colors,
            )
            Bullet("Pinch to zoom on the larger grids. Undo is on the toolbar.", colors)
        }

        Section("Good to know", colors) {
            Bullet(
                "You never have to guess. Every puzzle here has exactly one answer and " +
                    "can be reached by reasoning alone - it is checked before it ships.",
                colors,
            )
            Bullet(
                "Filling a square that should be empty costs a life. Crossing a square " +
                    "out never does, so mark what you have ruled out freely.",
                colors,
            )
            Bullet("A clue greys out once its row or column is right.", colors)
            Bullet("A new puzzle arrives daily. The archive holds the rest, none of it locked.", colors)
        }

        PrimaryButton(
            text = doneLabel,
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

@Composable
private fun StepControls(
    stepIndex: Int,
    lastStep: Int,
    playing: Boolean,
    colors: BoardColors,
    onReplay: () -> Unit,
    onPlayPause: () -> Unit,
    onBack: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GhostButton("Back", onBack, enabled = stepIndex > 0, glyph = Glyph.BACK)

        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(lastStep + 1) { index ->
                Box(
                    Modifier
                        .size(if (index == stepIndex) 8.dp else 6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (index <= stepIndex) colors.accent else colors.gridLine),
                )
            }
        }

        if (stepIndex >= lastStep) {
            GhostButton("Replay", onReplay, glyph = Glyph.UNDO, tint = colors.info)
        } else {
            GhostButton(
                text = if (playing) "Pause" else "Play",
                onClick = onPlayPause,
                glyph = if (playing) Glyph.SQUARE else Glyph.PLAY,
                tint = colors.info,
            )
        }
    }
}

/**
 * The demonstration board.
 *
 * Draws the state after [stepIndex] steps, with that step's own marks scaled in by
 * [reveal] and its line highlighted.
 */
@Composable
private fun LessonBoard(
    stepIndex: Int,
    reveal: Float,
    colors: BoardColors,
    modifier: Modifier = Modifier,
) {
    val size = TutorialLesson.SIZE
    val step = TutorialLesson.steps[stepIndex]
    val board = remember(stepIndex) { TutorialLesson.stateAfter(stepIndex) }
    // Marks from this step are drawn separately so they can animate; the rest are
    // already settled.
    val fresh = remember(stepIndex) {
        step.moves.associateBy { it.row * size + it.col }
    }
    val measurer = rememberTextMeasurer()

    val rowGutter = TutorialLesson.rowClues.maxOf { it.values.size }.coerceAtLeast(1)
    val colGutter = TutorialLesson.colClues.maxOf { it.values.size }.coerceAtLeast(1)

    Canvas(
        modifier
            .aspectRatio((size + rowGutter).toFloat() / (size + colGutter))
            .semantics {
                contentDescription = "Tutorial board, step ${stepIndex + 1} of " +
                    "${TutorialLesson.steps.size}"
            },
    ) {
        val cell = minOf(
            this.size.width / (size + rowGutter),
            this.size.height / (size + colGutter),
        )
        val originX = cell * rowGutter
        val originY = cell * colGutter
        val span = cell * size

        fun cellRect(row: Int, col: Int) = Offset(originX + col * cell, originY + row * cell)

        // --- the focused line ---------------------------------------------------------
        // Highlighted across the clue gutter too, because the point of the highlight is
        // to connect a number to the squares it governs.
        val glow = colors.accent.copy(alpha = 0.16f)
        step.focusRows.forEach { row ->
            drawRect(glow, Offset(0f, originY + row * cell), Size(originX + span, cell))
        }
        step.focusCols.forEach { col ->
            drawRect(glow, Offset(originX + col * cell, 0f), Size(cell, originY + span))
        }

        // --- clues --------------------------------------------------------------------
        val clueSize = (cell * 0.40f).toSp()
        TutorialLesson.rowClues.forEachIndexed { row, clue ->
            val text = clue.values.joinToString(" ")
            val laid = measurer.measure(
                text,
                TextStyle(fontSize = clueSize, fontWeight = FontWeight.Medium, color = colors.clueText),
            )
            drawText(
                laid,
                topLeft = Offset(
                    originX - laid.size.width - cell * 0.22f,
                    originY + row * cell + (cell - laid.size.height) / 2f,
                ),
            )
        }
        TutorialLesson.colClues.forEachIndexed { col, clue ->
            val text = clue.values.joinToString("\n")
            val laid = measurer.measure(
                text,
                TextStyle(fontSize = clueSize, fontWeight = FontWeight.Medium, color = colors.clueText),
            )
            drawText(
                laid,
                topLeft = Offset(
                    originX + col * cell + (cell - laid.size.width) / 2f,
                    originY - laid.size.height - cell * 0.14f,
                ),
            )
        }

        // --- squares ------------------------------------------------------------------
        for (row in 0 until size) {
            for (col in 0 until size) {
                val index = row * size + col
                val at = cellRect(row, col)
                val mark = board[index] ?: continue
                val new = fresh[index] != null
                if (new) {
                    // A pop rather than a fade: at 5x5 a fade alone is easy to miss.
                    val grow = 0.55f + 0.45f * reveal
                    scale(grow, pivot = Offset(at.x + cell / 2f, at.y + cell / 2f)) {
                        drawMark(mark, at, cell, colors, reveal)
                    }
                } else {
                    drawMark(mark, at, cell, colors, 1f)
                }
            }
        }

        // --- grid ---------------------------------------------------------------------
        for (i in 0..size) {
            val at = i * cell
            val weight = if (i == 0 || i == size) 2.2f else 1f
            drawLine(
                colors.gridLine,
                Offset(originX + at, originY),
                Offset(originX + at, originY + span),
                strokeWidth = weight,
            )
            drawLine(
                colors.gridLine,
                Offset(originX, originY + at),
                Offset(originX + span, originY + at),
                strokeWidth = weight,
            )
        }
    }
}

private fun DrawScope.drawMark(
    mark: TutorialLesson.Mark,
    at: Offset,
    cell: Float,
    colors: BoardColors,
    alpha: Float,
) {
    when (mark) {
        TutorialLesson.Mark.FILL -> drawRoundRect(
            color = colors.accent,
            topLeft = Offset(at.x + cell * 0.06f, at.y + cell * 0.06f),
            size = Size(cell * 0.88f, cell * 0.88f),
            cornerRadius = CornerRadius(cell * 0.14f),
            alpha = alpha,
        )

        TutorialLesson.Mark.CROSS -> {
            val inset = cell * 0.30f
            val stroke = Stroke(width = cell * 0.09f)
            val colour: Color = colors.cellCross
            drawLine(
                colour,
                Offset(at.x + inset, at.y + inset),
                Offset(at.x + cell - inset, at.y + cell - inset),
                strokeWidth = stroke.width,
                alpha = alpha,
            )
            drawLine(
                colour,
                Offset(at.x + cell - inset, at.y + inset),
                Offset(at.x + inset, at.y + cell - inset),
                strokeWidth = stroke.width,
                alpha = alpha,
            )
        }
    }
}

@Composable
private fun Section(title: String, colors: BoardColors, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.surface)
            .border(1.dp, colors.stroke, RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = colors.clueText,
        )
        content()
    }
}

@Composable
private fun Bullet(text: String, colors: BoardColors) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(5.dp)
                .clip(RoundedCornerShape(50))
                .background(colors.accent),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
    }
}
