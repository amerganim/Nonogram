package com.ganim.nonogram.game

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.ganim.nonogram.puzzle.model.CellState
import com.ganim.nonogram.ui.theme.BoardColors
import com.ganim.nonogram.ui.theme.LocalBoardColors

/**
 * Draws the whole board in one Canvas pass (build plan 5.1).
 *
 * > "Custom Compose `Canvas`, drawing the whole board in one pass. Do not build the grid
 * > from individual composables - it will not perform acceptably on low-end devices at
 * > 20x20."
 *
 * Three things keep the frame cheap enough to drag on at 20x20:
 *
 *  - **No per-cell composables.** 400 cells are 400 draw calls, not 400 layout nodes
 *    with their own measure, layout and recomposition.
 *  - **Clue text goes through the native canvas.** Compose text measurement per string
 *    per frame is far too slow for the ~240 clue numbers a 20x20 board shows. A reused
 *    native `Paint` and a char buffer draw them without measuring or allocating.
 *  - **Nothing allocates inside the draw loop.** Paints and the digit buffer are
 *    hoisted across frames; the loop only sets fields and draws.
 *
 * Culling to the visible rectangle keeps zoomed boards cheap too: at 4x zoom only a
 * fraction of the grid is on screen and the rest is skipped entirely.
 */
@Composable
fun BoardCanvas(
    state: GameState,
    metrics: BoardMetrics,
    highlight: CellRef?,
    gutterAlpha: Float,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBoardColors.current

    // Recomputed when the board changes, not on every frame.
    val clueProgress = remember(state.board) { boardClueProgress(state) }
    val painters = remember(colors) { BoardPainters(colors) }

    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            drawBoard(state, metrics, highlight, gutterAlpha, colors, painters, clueProgress)
        }
    }
}

/** Satisfied-flags for every row and column clue. */
internal class BoardClueProgress(
    val rows: List<List<Boolean>>,
    val columns: List<List<Boolean>>,
)

internal fun boardClueProgress(state: GameState): BoardClueProgress {
    val puzzle = state.puzzle
    val rows = List(puzzle.height) { r ->
        ClueProgress.satisfied(
            puzzle.rowClues[r],
            List(puzzle.width) { c -> state.board[r * puzzle.width + c] },
        )
    }
    val columns = List(puzzle.width) { c ->
        ClueProgress.satisfied(
            puzzle.colClues[c],
            List(puzzle.height) { r -> state.board[r * puzzle.width + c] },
        )
    }
    return BoardClueProgress(rows, columns)
}

/**
 * Paints reused across frames.
 *
 * Allocating a `Paint` inside a draw pass is the classic way to make custom drawing
 * stutter - it churns the heap sixty times a second.
 */
private class BoardPainters(colors: BoardColors) {
    val cluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // Monospace gives the tabular figures plan section 7 asks for: proportional
        // digits make a stack of clue numbers look misaligned and broken.
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        color = colors.clueText.toArgb()
    }

    /** Digits are written here rather than allocating a String per clue per frame. */
    val digits = CharArray(3)

    /** Writes [value] into [digits] and returns how many characters it used. */
    fun formatClue(value: Int): Int = when {
        value < 10 -> {
            digits[0] = '0' + value
            1
        }
        value < 100 -> {
            digits[0] = '0' + value / 10
            digits[1] = '0' + value % 10
            2
        }
        else -> {
            digits[0] = '0' + value / 100
            digits[1] = '0' + (value / 10) % 10
            digits[2] = '0' + value % 10
            3
        }
    }
}

private fun DrawScope.drawBoard(
    state: GameState,
    metrics: BoardMetrics,
    highlight: CellRef?,
    gutterAlpha: Float,
    colors: BoardColors,
    painters: BoardPainters,
    progress: BoardClueProgress,
) {
    if (metrics.cellSize <= 0f) return

    drawRect(colors.boardBackground)

    val cell = metrics.cellSize
    val hairline = (cell * MINOR_LINE_RATIO).coerceAtLeast(1f)
    val majorLine = (cell * MAJOR_LINE_RATIO).coerceAtLeast(hairline + 1f)

    val firstCol = (((0f - metrics.gridLeft) / cell).toInt() - 1).coerceIn(0, metrics.columns - 1)
    val lastCol = (((size.width - metrics.gridLeft) / cell).toInt() + 1).coerceIn(0, metrics.columns - 1)
    val firstRow = (((0f - metrics.gridTop) / cell).toInt() - 1).coerceIn(0, metrics.rows - 1)
    val lastRow = (((size.height - metrics.gridTop) / cell).toInt() + 1).coerceIn(0, metrics.rows - 1)

    // The grid is clipped so a panned board cannot spill into the pinned gutters.
    clipRect(
        left = metrics.rowGutter,
        top = metrics.colGutter,
        right = size.width,
        bottom = size.height,
    ) {
        drawCells(state, metrics, firstRow, lastRow, firstCol, lastCol, colors)
        drawHighlight(metrics, highlight, colors)
        drawGridLines(metrics, firstRow, lastRow, firstCol, lastCol, hairline, majorLine, colors)
    }

    if (gutterAlpha > 0.01f) {
        drawClues(
            state, metrics, highlight, gutterAlpha, colors, painters, progress,
            firstRow, lastRow, firstCol, lastCol,
        )
    }
}

private fun DrawScope.drawCells(
    state: GameState,
    metrics: BoardMetrics,
    firstRow: Int,
    lastRow: Int,
    firstCol: Int,
    lastCol: Int,
    colors: BoardColors,
) {
    val cell = metrics.cellSize
    val inset = cell * CROSS_INSET_RATIO
    val crossStroke = (cell * CROSS_STROKE_RATIO).coerceAtLeast(1.5f)
    val cellSize = Size(cell, cell)

    for (row in firstRow..lastRow) {
        val top = metrics.cellTop(row)
        val rowBase = row * metrics.columns
        for (col in firstCol..lastCol) {
            val left = metrics.cellLeft(col)
            val index = rowBase + col

            when (state.board[index]) {
                CellState.FILLED -> drawRect(colors.cellFilled, Offset(left, top), cellSize)

                CellState.CROSSED, CellState.EMPTY -> {
                    drawRect(colors.cellEmpty, Offset(left, top), cellSize)
                    val strokeColor =
                        if (index in state.mistakeCells) colors.cellMistake else colors.cellCross
                    drawLine(
                        strokeColor,
                        Offset(left + inset, top + inset),
                        Offset(left + cell - inset, top + cell - inset),
                        strokeWidth = crossStroke,
                    )
                    drawLine(
                        strokeColor,
                        Offset(left + cell - inset, top + inset),
                        Offset(left + inset, top + cell - inset),
                        strokeWidth = crossStroke,
                    )
                }

                CellState.UNKNOWN -> drawRect(colors.cellEmpty, Offset(left, top), cellSize)
            }
        }
    }
}

/** The row and column under the finger, so a long line stays easy to track (5.1). */
private fun DrawScope.drawHighlight(metrics: BoardMetrics, highlight: CellRef?, colors: BoardColors) {
    if (highlight == null) return
    drawRect(
        colors.highlight,
        Offset(metrics.gridLeft, metrics.cellTop(highlight.row)),
        Size(metrics.gridWidth, metrics.cellSize),
    )
    drawRect(
        colors.highlight,
        Offset(metrics.cellLeft(highlight.col), metrics.gridTop),
        Size(metrics.cellSize, metrics.gridHeight),
    )
}

/** Hairlines between cells, heavier every five (5.1). */
private fun DrawScope.drawGridLines(
    metrics: BoardMetrics,
    firstRow: Int,
    lastRow: Int,
    firstCol: Int,
    lastCol: Int,
    hairline: Float,
    majorLine: Float,
    colors: BoardColors,
) {
    for (col in firstCol..minOf(lastCol + 1, metrics.columns)) {
        val x = metrics.cellLeft(col)
        val major = col % MAJOR_EVERY == 0 || col == metrics.columns
        drawLine(
            color = if (major) colors.gridLineMajor else colors.gridLine,
            start = Offset(x, metrics.gridTop),
            end = Offset(x, metrics.gridTop + metrics.gridHeight),
            strokeWidth = if (major) majorLine else hairline,
        )
    }
    for (row in firstRow..minOf(lastRow + 1, metrics.rows)) {
        val y = metrics.cellTop(row)
        val major = row % MAJOR_EVERY == 0 || row == metrics.rows
        drawLine(
            color = if (major) colors.gridLineMajor else colors.gridLine,
            start = Offset(metrics.gridLeft, y),
            end = Offset(metrics.gridLeft + metrics.gridWidth, y),
            strokeWidth = if (major) majorLine else hairline,
        )
    }
}

/**
 * Clue gutters, pinned to the edges while the grid pans beneath them (5.1).
 *
 * Each gutter is clipped to its own strip and follows the grid along one axis only,
 * which keeps it aligned with the rows or columns it labels while staying glued to the
 * screen edge. A gutter that panned away with the grid would be useless - the clues are
 * the thing being read.
 */
private fun DrawScope.drawClues(
    state: GameState,
    metrics: BoardMetrics,
    highlight: CellRef?,
    alpha: Float,
    colors: BoardColors,
    painters: BoardPainters,
    progress: BoardClueProgress,
    firstRow: Int,
    lastRow: Int,
    firstCol: Int,
    lastCol: Int,
) {
    val cell = metrics.cellSize
    val paint = painters.cluePaint
    paint.textSize = cell * CLUE_TEXT_RATIO
    val baselineNudge = (paint.descent() + paint.ascent()) / 2f
    val slotWidth = cell * BoardMetrics.CLUE_SLOT_RATIO
    val background = colors.clueBackground.copy(alpha = alpha)

    // Row clues, down the left edge.
    drawRect(background, Offset(0f, metrics.colGutter), Size(metrics.rowGutter, size.height))
    clipRect(0f, metrics.colGutter, metrics.rowGutter, size.height) {
        drawIntoCanvas { canvas ->
            for (row in firstRow..lastRow) {
                val clue = state.puzzle.rowClues[row]
                if (clue.isBlank) continue
                val baseline = metrics.cellTop(row) + cell / 2f - baselineNudge
                val flags = progress.rows[row]
                clue.values.forEachIndexed { i, value ->
                    val slotsFromRight = clue.values.size - i
                    val x = metrics.rowGutter - (slotsFromRight - 0.5f) * slotWidth
                    paint.color = clueColor(colors, flags.getOrElse(i) { false }, alpha, highlight?.row == row)
                    canvas.nativeCanvas.drawText(painters.digits, 0, painters.formatClue(value), x, baseline, paint)
                }
            }
        }
    }

    // Column clues, along the top edge. The corner is painted last so neither strip
    // shows through it.
    drawRect(background, Offset(metrics.rowGutter, 0f), Size(size.width - metrics.rowGutter, metrics.colGutter))
    clipRect(metrics.rowGutter, 0f, size.width, metrics.colGutter) {
        drawIntoCanvas { canvas ->
            for (col in firstCol..lastCol) {
                val clue = state.puzzle.colClues[col]
                if (clue.isBlank) continue
                val centerX = metrics.cellLeft(col) + cell / 2f
                val flags = progress.columns[col]
                clue.values.forEachIndexed { i, value ->
                    val slotsFromBottom = clue.values.size - i
                    val baseline = metrics.colGutter - (slotsFromBottom - 0.5f) * slotWidth - baselineNudge
                    paint.color = clueColor(colors, flags.getOrElse(i) { false }, alpha, highlight?.col == col)
                    canvas.nativeCanvas.drawText(
                        painters.digits, 0, painters.formatClue(value), centerX, baseline, paint,
                    )
                }
            }
        }
    }
    drawRect(background, Offset.Zero, Size(metrics.rowGutter, metrics.colGutter))
}

/** Satisfied clues grey out (5.1); the line under the finger stays emphasised. */
private fun clueColor(
    colors: BoardColors,
    satisfied: Boolean,
    alpha: Float,
    emphasised: Boolean,
): Int {
    val base = when {
        satisfied -> colors.clueTextSatisfied
        emphasised -> colors.accent
        else -> colors.clueText
    }
    return base.copy(alpha = base.alpha * alpha).toArgb()
}

private const val MAJOR_EVERY = 5
private const val MINOR_LINE_RATIO = 0.02f
private const val MAJOR_LINE_RATIO = 0.06f
private const val CROSS_INSET_RATIO = 0.28f
private const val CROSS_STROKE_RATIO = 0.08f
private const val CLUE_TEXT_RATIO = 0.52f
