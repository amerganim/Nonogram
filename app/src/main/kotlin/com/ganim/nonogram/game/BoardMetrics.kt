package com.ganim.nonogram.game

/**
 * Board layout arithmetic: where every cell and clue lands, and how zoom and pan move
 * them (build plan 5.1).
 *
 * > "Pinch-to-zoom and pan for 15x15 and 20x20. On a 5-inch screen a 20x20 grid with
 * > clues is unusable at fit-to-screen. Clamp zoom to sensible bounds and keep clue
 * > gutters pinned during pan."
 *
 * Gutters are pinned like frozen panes in a spreadsheet: the row-clue gutter stays
 * glued to the left edge and only scrolls vertically, the column-clue gutter stays at
 * the top and only scrolls horizontally. Panning a grid whose clues scroll away with it
 * is useless - the clues are what you are reading.
 *
 * Deliberately free of Compose and Android types so the arithmetic is unit-tested
 * directly rather than through a rendered frame.
 */
data class BoardMetrics(
    val columns: Int,
    val rows: Int,
    val cellSize: Float,
    /** Width of the left gutter holding row clues. */
    val rowGutter: Float,
    /** Height of the top gutter holding column clues. */
    val colGutter: Float,
    val panX: Float,
    val panY: Float,
    val zoom: Float,
    val viewportWidth: Float,
    val viewportHeight: Float,
) {
    /** Left edge of column 0, in viewport coordinates. */
    val gridLeft: Float get() = rowGutter + panX

    /** Top edge of row 0, in viewport coordinates. */
    val gridTop: Float get() = colGutter + panY

    val gridWidth: Float get() = columns * cellSize
    val gridHeight: Float get() = rows * cellSize

    fun cellLeft(col: Int): Float = gridLeft + col * cellSize
    fun cellTop(row: Int): Float = gridTop + row * cellSize

    /** The cell under a viewport point, or null if the point is off the grid. */
    fun cellAt(x: Float, y: Float): CellRef? {
        if (cellSize <= 0f) return null
        val col = ((x - gridLeft) / cellSize).toInt()
        val row = ((y - gridTop) / cellSize).toInt()
        if (x < gridLeft || y < gridTop) return null
        if (row !in 0 until rows || col !in 0 until columns) return null
        return CellRef(row, col)
    }

    /**
     * The same as [cellAt] but clamped to the board instead of returning null, for
     * continuing a drag whose finger has strayed past the edge. Mid-drag the player
     * means "keep painting this line", not "stop".
     */
    fun nearestCell(x: Float, y: Float): CellRef {
        if (cellSize <= 0f) return CellRef(0, 0)
        val col = ((x - gridLeft) / cellSize).toInt().coerceIn(0, columns - 1)
        val row = ((y - gridTop) / cellSize).toInt().coerceIn(0, rows - 1)
        return CellRef(row, col)
    }

    /** Whether the board overflows its viewport and therefore needs panning at all. */
    val needsPanning: Boolean
        get() = rowGutter + gridWidth > viewportWidth + 0.5f ||
            colGutter + gridHeight > viewportHeight + 0.5f

    /** Re-clamps pan so the grid cannot be dragged away from the gutters or off-screen. */
    fun withPan(newPanX: Float, newPanY: Float): BoardMetrics = copy(
        panX = clamp(newPanX, viewportWidth - rowGutter, gridWidth),
        panY = clamp(newPanY, viewportHeight - colGutter, gridHeight),
    )

    fun withZoom(newZoom: Float, baseCellSize: Float): BoardMetrics {
        val z = newZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val scaled = baseCellSize * z
        return copy(
            zoom = z,
            cellSize = scaled,
            rowGutter = rowGutterCells * scaled,
            colGutter = colGutterCells * scaled,
        ).withPan(panX, panY)
    }

    /** Gutter widths are carried in cell units so zoom scales them with the grid. */
    private val rowGutterCells: Float get() = if (cellSize > 0f) rowGutter / cellSize else 0f
    private val colGutterCells: Float get() = if (cellSize > 0f) colGutter / cellSize else 0f

    companion object {
        /** Fit-to-screen. Below this the board would be smaller than it needs to be. */
        const val MIN_ZOOM = 1.0f

        /** Beyond this a single cell fills an absurd share of the screen. */
        const val MAX_ZOOM = 4.0f

        /** A clue number occupies this fraction of a cell along the gutter. */
        const val CLUE_SLOT_RATIO = 0.62f

        /** Gutters never shrink below this many cells, or single-digit clues look cramped. */
        const val MIN_GUTTER_CELLS = 1.2f

        /**
         * Lays the board out to fit [viewportWidth] x [viewportHeight] at zoom 1.
         *
         * [longestRowClue] and [longestColClue] are the most numbers any single clue
         * holds; they decide how much room the gutters need.
         */
        fun fit(
            columns: Int,
            rows: Int,
            longestRowClue: Int,
            longestColClue: Int,
            viewportWidth: Float,
            viewportHeight: Float,
            zoom: Float = MIN_ZOOM,
            panX: Float = 0f,
            panY: Float = 0f,
        ): BoardMetrics {
            val rowGutterCells = maxOf(longestRowClue * CLUE_SLOT_RATIO, MIN_GUTTER_CELLS)
            val colGutterCells = maxOf(longestColClue * CLUE_SLOT_RATIO, MIN_GUTTER_CELLS)

            val baseCell = minOf(
                viewportWidth / (columns + rowGutterCells),
                viewportHeight / (rows + colGutterCells),
            ).coerceAtLeast(0f)

            val clampedZoom = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
            val cell = baseCell * clampedZoom

            return BoardMetrics(
                columns = columns,
                rows = rows,
                cellSize = cell,
                rowGutter = rowGutterCells * cell,
                colGutter = colGutterCells * cell,
                panX = 0f,
                panY = 0f,
                zoom = clampedZoom,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
            ).withPan(panX, panY)
        }

        /** The cell size this layout would use at zoom 1, needed when re-zooming. */
        fun baseCellSize(metrics: BoardMetrics): Float =
            if (metrics.zoom <= 0f) metrics.cellSize else metrics.cellSize / metrics.zoom

        /** Overflow smaller than this is float noise from the fit arithmetic, not real. */
        private const val PAN_EPSILON = 0.5f

        /**
         * Pan is negative (content pushed left/up). It may not exceed what actually
         * overflows, and is pinned to zero when the content already fits.
         *
         * The epsilon matters: a fitted board computes an overflow of "zero" that can
         * land a hair either side of it, and without the guard a board that visibly
         * fits would still creep by a fraction of a pixel when dragged.
         */
        private fun clamp(value: Float, available: Float, content: Float): Float {
            val overflow = content - available
            if (overflow <= PAN_EPSILON) return 0f
            return value.coerceIn(-overflow, 0f)
        }
    }
}
