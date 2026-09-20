package com.ganim.nonogram.game

import android.graphics.Paint
import android.graphics.Picture
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
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
 * Four things keep the frame cheap enough to drag on at 20x20. The first two were there
 * from the start; the last two came out of profiling a Galaxy A15, where the naive
 * version spent about 20ms a frame and visibly stuttered.
 *
 *  - **No per-cell composables.** 400 cells are draw calls, not 400 layout nodes with
 *    their own measure, layout and recomposition.
 *  - **Nothing allocates inside the draw loop.**
 *  - **Only non-blank cells are drawn.** The grid gets one background rect and then only
 *    the cells that differ from it. A board is emptiest exactly when the player is
 *    dragging fastest, so this is cheapest when it matters most.
 *  - **Clue numbers are recorded once and replayed.** They were the largest single cost -
 *    roughly 240 text layouts per frame at 20x20 - and they only change when a clue
 *    becomes satisfied. Recording them into a [Picture] turns each gutter into one call.
 *
 * Culling to the visible rectangle keeps zoomed boards cheap too: at 4x zoom only a
 * fraction of the grid is on screen and the rest is skipped entirely.
 */
@Composable
fun BoardCanvas(
    boardState: State<GameState>,
    metrics: BoardMetrics,
    highlightState: State<CellRef?>,
    gutterAlpha: Float,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBoardColors.current
    val cache = remember(colors) { BoardRenderCache(colors) }

    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            // The board and the highlight are read *here*, inside the draw lambda, and
            // nowhere in composition. That makes a painted cell invalidate only the draw
            // phase instead of recomposing the screen.
            //
            // This was the whole performance problem. Measured on a Galaxy A15, a 10x10
            // board cost the same ~18ms per update as a 20x20 - the work was not
            // proportional to the number of cells, so it was never the drawing. It was
            // recomposition, triggered once per painted cell by reading the state during
            // composition.
            drawBoard(boardState.value, metrics, highlightState.value, gutterAlpha, colors, cache)
        }
    }
}

/**
 * Everything the renderer keeps between frames.
 *
 * Lives outside composition because the draw phase cannot call `remember`. It caches by
 * value, so a drag that changes no clue's state redraws without re-recording anything.
 */
internal class BoardRenderCache(private val colors: BoardColors) {
    private var cachedBoard: Board? = null
    private var cachedProgress: BoardClueProgress? = null

    private var cachedCellSize = 0f
    private var cachedPuzzleId: String? = null
    private var cachedPictures: CluePictures? = null

    fun progressFor(state: GameState): BoardClueProgress {
        if (cachedBoard !== state.board || cachedProgress == null) {
            cachedBoard = state.board
            cachedProgress = boardClueProgress(state)
        }
        return cachedProgress!!
    }

    fun picturesFor(state: GameState, metrics: BoardMetrics): CluePictures? {
        val progress = progressFor(state)
        val stale = cachedPictures == null ||
            cachedPuzzleId != state.puzzle.id ||
            cachedCellSize != metrics.cellSize ||
            cachedRecordedProgress != progress
        if (stale) {
            cachedPuzzleId = state.puzzle.id
            cachedCellSize = metrics.cellSize
            cachedRecordedProgress = progress
            cachedPictures = recordClues(state, progress, metrics, colors)
        }
        return cachedPictures
    }

    private var cachedRecordedProgress: BoardClueProgress? = null
}

/**
 * Satisfied-flags for every row and column clue.
 *
 * A `data class` on purpose. This is the cache key for the recorded clue pictures, and
 * with identity equality a fresh instance on every painted cell invalidated them every
 * time - which moved the cost of the clue text from drawing to re-recording and saved
 * nothing. Compared by value, a drag that does not complete any clue leaves the pictures
 * untouched, which is the overwhelmingly common case.
 */
internal data class BoardClueProgress(
    val rows: List<List<Boolean>>,
    val columns: List<List<Boolean>>,
)

internal fun boardClueProgress(state: GameState): BoardClueProgress {
    val puzzle = state.puzzle
    val board = state.board
    val width = puzzle.width
    val rows = List(puzzle.height) { r ->
        val offset = r * width
        ClueProgress.satisfied(puzzle.rowClues[r], width) { board[offset + it] }
    }
    val columns = List(width) { c ->
        ClueProgress.satisfied(puzzle.colClues[c], puzzle.height) { board[it * width + c] }
    }
    return BoardClueProgress(rows, columns)
}

private fun DrawScope.drawBoard(
    state: GameState,
    metrics: BoardMetrics,
    highlight: CellRef?,
    gutterAlpha: Float,
    colors: BoardColors,
    cache: BoardRenderCache,
) {
    if (metrics.cellSize <= 0f) return
    val cluePictures = cache.picturesFor(state, metrics)

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
        left = metrics.gutterRight,
        top = metrics.gutterBottom,
        right = size.width,
        bottom = size.height,
    ) {
        drawCells(state, metrics, firstRow, lastRow, firstCol, lastCol, colors)
        drawHighlight(metrics, highlight, colors)
        drawGridLines(metrics, firstRow, lastRow, firstCol, lastCol, hairline, majorLine, colors)
    }

    if (gutterAlpha > 0.01f && cluePictures != null) {
        drawClues(metrics, highlight, gutterAlpha, colors, cluePictures)
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

    // One rect for the whole visible grid, then only the cells that differ from it.
    drawRect(
        colors.cellEmpty,
        Offset(metrics.cellLeft(firstCol), metrics.cellTop(firstRow)),
        Size((lastCol - firstCol + 1) * cell, (lastRow - firstRow + 1) * cell),
    )


    for (row in firstRow..lastRow) {
        val top = metrics.cellTop(row)
        val rowBase = row * metrics.columns
        for (col in firstCol..lastCol) {
            val index = rowBase + col
            val cellState = state.board[index]
            if (cellState == CellState.UNKNOWN) continue
            val left = metrics.cellLeft(col)

            if (cellState == CellState.FILLED) {
                drawRect(colors.cellFilled, Offset(left, top), cellSize)
            } else {
                // Two plain lines. Collecting every cross into one stroked path was
                // tried and measured slower on the target device - the path is rebuilt
                // each frame and stroking it with caps costs more than the draws saved.
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
 * Each gutter is a pre-recorded [Picture] blitted at the board's current offset, so
 * panning and painting cost one draw call per gutter rather than re-rendering every
 * number. A gutter follows the grid along one axis only, which keeps it aligned with the
 * rows or columns it labels while staying glued to the screen edge - one that panned
 * away with the grid would be useless, since the clues are the thing being read.
 */
private fun DrawScope.drawClues(
    metrics: BoardMetrics,
    highlight: CellRef?,
    alpha: Float,
    colors: BoardColors,
    pictures: CluePictures,
) {
    val background = colors.clueBackground.copy(alpha = alpha)
    val alpha255 = (alpha * 255).toInt().coerceIn(0, 255)
    val opaque = alpha >= 0.999f

    // Row clues, down the left edge of the board.
    drawRect(
        background,
        Offset(metrics.gutterLeft, metrics.gutterBottom),
        Size(metrics.rowGutter, size.height - metrics.gutterBottom),
    )
    clipRect(metrics.gutterLeft, metrics.gutterBottom, metrics.gutterRight, size.height) {
        // The line under the finger is marked by tinting its strip rather than by
        // recolouring each number, which would invalidate the recorded picture on every
        // frame of a drag.
        highlight?.let {
            drawRect(
                colors.highlight,
                Offset(metrics.gutterLeft, metrics.cellTop(it.row)),
                Size(metrics.rowGutter, metrics.cellSize),
            )
        }
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            // saveLayerAlpha allocates an offscreen buffer and composites it back. That
            // is a fixed cost per frame regardless of board size, and it is only needed
            // while the gutters are fading out on completion. The rest of the time -
            // which is all of the time the player is actually dragging - it is skipped.
            val checkpoint = if (opaque) {
                native.save()
            } else {
                native.saveLayerAlpha(
                    metrics.gutterLeft,
                    metrics.gutterBottom,
                    metrics.gutterRight,
                    size.height,
                    alpha255,
                )
            }
            native.translate(metrics.gutterLeft, metrics.gridTop)
            native.drawPicture(pictures.rows)
            native.restoreToCount(checkpoint)
        }
    }

    // Column clues, along the top edge.
    drawRect(
        background,
        Offset(metrics.gutterRight, metrics.gutterTop),
        Size(size.width - metrics.gutterRight, metrics.colGutter),
    )
    clipRect(metrics.gutterRight, metrics.gutterTop, size.width, metrics.gutterBottom) {
        highlight?.let {
            drawRect(
                colors.highlight,
                Offset(metrics.cellLeft(it.col), metrics.gutterTop),
                Size(metrics.cellSize, metrics.colGutter),
            )
        }
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            val checkpoint = if (opaque) {
                native.save()
            } else {
                native.saveLayerAlpha(
                    metrics.gutterRight,
                    metrics.gutterTop,
                    size.width,
                    metrics.gutterBottom,
                    alpha255,
                )
            }
            native.translate(metrics.gridLeft, metrics.gutterTop)
            native.drawPicture(pictures.columns)
            native.restoreToCount(checkpoint)
        }
    }

    // The corner last, so neither strip shows through it.
    drawRect(
        background,
        Offset(metrics.gutterLeft, metrics.gutterTop),
        Size(metrics.rowGutter, metrics.colGutter),
    )
}

/** The two clue gutters, recorded once and replayed each frame. */
internal class CluePictures(val rows: Picture, val columns: Picture)

/**
 * Lays out every clue number once and records the result.
 *
 * Recorded at full opacity and without the highlight, so neither the completion fade nor
 * a moving finger invalidates it. Positions are relative to each gutter's own origin, so
 * panning is just a translate.
 */
internal fun recordClues(
    state: GameState,
    progress: BoardClueProgress,
    metrics: BoardMetrics,
    colors: BoardColors,
): CluePictures? {
    val cell = metrics.cellSize
    if (cell <= 0f || metrics.rowGutter <= 0f || metrics.colGutter <= 0f) return null

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // Monospace gives the tabular figures plan section 7 asks for: proportional
        // digits make a stack of clue numbers look misaligned and broken.
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        textSize = cell * CLUE_TEXT_RATIO
    }
    val baselineNudge = (paint.descent() + paint.ascent()) / 2f
    val slotWidth = cell * BoardMetrics.CLUE_SLOT_RATIO
    val digits = CharArray(3)

    fun colourFor(satisfied: Boolean) =
        (if (satisfied) colors.clueTextSatisfied else colors.clueText).toArgb()

    val rows = Picture()
    val rowsCanvas = rows.beginRecording(
        metrics.rowGutter.toInt().coerceAtLeast(1),
        (metrics.rows * cell).toInt().coerceAtLeast(1),
    )
    for (row in 0 until metrics.rows) {
        val clue = state.puzzle.rowClues[row]
        if (clue.isBlank) continue
        val baseline = row * cell + cell / 2f - baselineNudge
        val flags = progress.rows[row]
        clue.values.forEachIndexed { i, value ->
            val slotsFromRight = clue.values.size - i
            val x = metrics.rowGutter - (slotsFromRight - 0.5f) * slotWidth
            paint.color = colourFor(flags.getOrElse(i) { false })
            rowsCanvas.drawText(digits, 0, formatClue(digits, value), x, baseline, paint)
        }
    }
    rows.endRecording()

    val columns = Picture()
    val columnsCanvas = columns.beginRecording(
        (metrics.columns * cell).toInt().coerceAtLeast(1),
        metrics.colGutter.toInt().coerceAtLeast(1),
    )
    for (col in 0 until metrics.columns) {
        val clue = state.puzzle.colClues[col]
        if (clue.isBlank) continue
        val centerX = col * cell + cell / 2f
        val flags = progress.columns[col]
        clue.values.forEachIndexed { i, value ->
            val slotsFromBottom = clue.values.size - i
            val baseline = metrics.colGutter - (slotsFromBottom - 0.5f) * slotWidth - baselineNudge
            paint.color = colourFor(flags.getOrElse(i) { false })
            columnsCanvas.drawText(digits, 0, formatClue(digits, value), centerX, baseline, paint)
        }
    }
    columns.endRecording()

    return CluePictures(rows, columns)
}

/** Writes [value] into [digits] and returns how many characters it used. */
private fun formatClue(digits: CharArray, value: Int): Int = when {
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

private const val MAJOR_EVERY = 5
private const val MINOR_LINE_RATIO = 0.02f
private const val MAJOR_LINE_RATIO = 0.06f
private const val CROSS_INSET_RATIO = 0.28f
private const val CROSS_STROKE_RATIO = 0.08f
private const val CLUE_TEXT_RATIO = 0.52f
