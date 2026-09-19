package com.ganim.nonogram.game

/** A cell address on the board. */
data class CellRef(val row: Int, val col: Int)

/** Which way a drag has committed. */
enum class DragAxis { UNDECIDED, HORIZONTAL, VERTICAL }

/**
 * Turns a raw drag into the straight line the player meant (build plan 5.2).
 *
 * > "Drag should snap to a straight line once the gesture has clearly committed to an
 * > axis. Players drag across rows constantly and finger wobble should not paint
 * > diagonally."
 *
 * The axis commits the moment the finger leaves the starting cell, or once it has moved
 * past a pixel threshold - whichever happens first. Waiting longer would let a wobble
 * through before the snap engaged; committing sooner would lock the wrong axis on a
 * gesture that had barely begun.
 *
 * After committing, every reported cell is projected onto the locked row or column, so
 * a drift of two rows while sweeping across a line paints the line the player was
 * actually following.
 *
 * Pure and frame-independent: no Compose, no Android, directly unit-tested.
 */
class DragTracker(private val axisLockThresholdPx: Float = DEFAULT_AXIS_LOCK_PX) {

    var origin: CellRef? = null
        private set

    var axis: DragAxis = DragAxis.UNDECIDED
        private set

    val isActive: Boolean get() = origin != null

    fun begin(cell: CellRef) {
        origin = cell
        axis = DragAxis.UNDECIDED
    }

    /**
     * Reports the finger at [cell], [dxPx]/[dyPx] from where the drag began, and returns
     * the cell that should actually be painted - or null if the drag is not running.
     */
    fun resolve(cell: CellRef, dxPx: Float, dyPx: Float): CellRef? {
        val start = origin ?: return null

        if (axis == DragAxis.UNDECIDED) {
            val leftStartCell = cell != start
            val movedFar = maxOf(kotlin.math.abs(dxPx), kotlin.math.abs(dyPx)) >= axisLockThresholdPx
            if (leftStartCell || movedFar) {
                axis = chooseAxis(start, cell, dxPx, dyPx)
            }
        }

        return when (axis) {
            DragAxis.HORIZONTAL -> CellRef(start.row, cell.col)
            DragAxis.VERTICAL -> CellRef(cell.row, start.col)
            DragAxis.UNDECIDED -> start
        }
    }

    fun end() {
        origin = null
        axis = DragAxis.UNDECIDED
    }

    /**
     * Cell distance decides the axis, because that is what the player sees. Pixels only
     * break a tie - including the diagonal case, where the finger has moved into a cell
     * one row and one column away and neither is more "correct" by cell count.
     */
    private fun chooseAxis(start: CellRef, cell: CellRef, dxPx: Float, dyPx: Float): DragAxis {
        val dRow = kotlin.math.abs(cell.row - start.row)
        val dCol = kotlin.math.abs(cell.col - start.col)
        return when {
            dCol > dRow -> DragAxis.HORIZONTAL
            dRow > dCol -> DragAxis.VERTICAL
            kotlin.math.abs(dxPx) >= kotlin.math.abs(dyPx) -> DragAxis.HORIZONTAL
            else -> DragAxis.VERTICAL
        }
    }

    companion object {
        /**
         * Roughly a third of a finger width at mdpi. Small enough that the snap engages
         * before a wobble can paint a stray cell, large enough that it does not fire on
         * the jitter of a stationary finger.
         */
        const val DEFAULT_AXIS_LOCK_PX = 16f
    }
}
