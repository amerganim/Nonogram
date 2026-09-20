package com.ganim.nonogram.game

import com.ganim.nonogram.puzzle.model.CellState.CROSSED
import com.ganim.nonogram.puzzle.model.CellState.EMPTY
import com.ganim.nonogram.puzzle.model.CellState.FILLED
import com.ganim.nonogram.puzzle.model.CellState.UNKNOWN
import com.ganim.nonogram.puzzle.model.Clue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Build plan 5.1 and 5.2: drag axis snapping, clue greying, and board layout under zoom
 * and pan.
 *
 * Acceptance: "Drag-paint mode-locking and axis-snapping behave as specified."
 */
class InputAndLayoutTest {

    @Nested
    inner class Drag {

        @Test
        @DisplayName("a drag across a row snaps to that row despite finger wobble")
        fun `horizontal drag ignores vertical drift`() {
            val tracker = DragTracker()
            tracker.begin(CellRef(5, 2))

            tracker.resolve(CellRef(5, 3), dxPx = 40f, dyPx = 2f) shouldBe CellRef(5, 3)
            tracker.axis shouldBe DragAxis.HORIZONTAL

            // Finger drifts two rows down; the paint stays on row 5.
            tracker.resolve(CellRef(7, 6), dxPx = 160f, dyPx = 70f) shouldBe CellRef(5, 6)
        }

        @Test
        fun `a vertical drag snaps to its column`() {
            val tracker = DragTracker()
            tracker.begin(CellRef(2, 4))
            tracker.resolve(CellRef(3, 4), dxPx = 1f, dyPx = 40f) shouldBe CellRef(3, 4)
            tracker.axis shouldBe DragAxis.VERTICAL
            tracker.resolve(CellRef(8, 1), dxPx = -90f, dyPx = 200f) shouldBe CellRef(8, 4)
        }

        @Test
        fun `the axis stays undecided while the finger has not left the first cell`() {
            val tracker = DragTracker(axisLockThresholdPx = 100f)
            tracker.begin(CellRef(1, 1))
            tracker.resolve(CellRef(1, 1), dxPx = 3f, dyPx = 2f) shouldBe CellRef(1, 1)
            tracker.axis shouldBe DragAxis.UNDECIDED
        }

        @Test
        fun `moving far enough locks the axis even inside one big cell`() {
            val tracker = DragTracker(axisLockThresholdPx = 10f)
            tracker.begin(CellRef(1, 1))
            tracker.resolve(CellRef(1, 1), dxPx = 12f, dyPx = 1f)
            tracker.axis shouldBe DragAxis.HORIZONTAL
        }

        @Test
        @DisplayName("an exactly diagonal move is broken by pixel distance, not left to chance")
        fun `diagonal ties are broken by pixels`() {
            val horizontal = DragTracker().apply { begin(CellRef(0, 0)) }
            horizontal.resolve(CellRef(1, 1), dxPx = 50f, dyPx = 30f)
            horizontal.axis shouldBe DragAxis.HORIZONTAL

            val vertical = DragTracker().apply { begin(CellRef(0, 0)) }
            vertical.resolve(CellRef(1, 1), dxPx = 30f, dyPx = 50f)
            vertical.axis shouldBe DragAxis.VERTICAL
        }

        @Test
        fun `once locked the axis never changes for the rest of the drag`() {
            val tracker = DragTracker()
            tracker.begin(CellRef(0, 0))
            tracker.resolve(CellRef(0, 1), dxPx = 40f, dyPx = 0f)
            tracker.axis shouldBe DragAxis.HORIZONTAL
            // A long downward sweep must not flip the axis mid-gesture.
            tracker.resolve(CellRef(9, 1), dxPx = 40f, dyPx = 400f) shouldBe CellRef(0, 1)
            tracker.axis shouldBe DragAxis.HORIZONTAL
        }

        @Test
        fun `ending the drag clears the tracker`() {
            val tracker = DragTracker()
            tracker.begin(CellRef(0, 0))
            tracker.resolve(CellRef(0, 3), dxPx = 90f, dyPx = 0f)
            tracker.end()
            tracker.isActive shouldBe false
            tracker.axis shouldBe DragAxis.UNDECIDED
            tracker.resolve(CellRef(1, 1), 0f, 0f) shouldBe null
        }
    }

    @Nested
    inner class Clues {

        @Test
        fun `a finished group is marked satisfied`() {
            ClueProgress.satisfied(Clue.of(3), listOf(FILLED, FILLED, FILLED, CROSSED, UNKNOWN)) shouldBe
                listOf(true)
        }

        @Test
        @DisplayName("an unfinished run proves nothing, even though it could grow into the clue")
        fun `an unfinished run proves nothing`() {
            // Two of a three-run. Filling one more cell would satisfy it, so nothing is
            // settled yet.
            ClueProgress.satisfied(Clue.of(3), listOf(FILLED, FILLED, UNKNOWN, UNKNOWN, UNKNOWN)) shouldBe
                listOf(false)
        }

        @Test
        @DisplayName("a complete run counts even with unknowns beside it")
        fun `a complete run beside unknowns is satisfied`() {
            // This used to assert the opposite. A run of exactly three against a clue of
            // three cannot be extended without breaking that clue, so the group is
            // placed - and refusing to say so meant a player who never crosses got no
            // feedback at all, which is how it looked on a device.
            ClueProgress.satisfied(Clue.of(3), listOf(FILLED, FILLED, FILLED, UNKNOWN, UNKNOWN)) shouldBe
                listOf(true)
        }

        @Test
        fun `a run closed by the board edge counts`() {
            ClueProgress.satisfied(Clue.of(2), listOf(UNKNOWN, UNKNOWN, CROSSED, FILLED, FILLED)) shouldBe
                listOf(true)
        }

        @Test
        fun `groups are matched from both ends`() {
            // Left group done, right group done, middle still open.
            val line = listOf(FILLED, CROSSED, UNKNOWN, UNKNOWN, CROSSED, FILLED, FILLED)
            ClueProgress.satisfied(Clue.of(1, 1, 2), line) shouldBe listOf(true, false, true)
        }

        @Test
        fun `a wrong length does not mark the group`() {
            ClueProgress.satisfied(Clue.of(3), listOf(FILLED, FILLED, CROSSED, UNKNOWN, UNKNOWN)) shouldBe
                listOf(false)
        }

        @Test
        @DisplayName("a solved line marks every group exactly once")
        fun `a fully solved line marks all groups`() {
            val line = listOf(FILLED, CROSSED, FILLED, FILLED, CROSSED, FILLED)
            ClueProgress.satisfied(Clue.of(1, 2, 1), line) shouldBe listOf(true, true, true)
            ClueProgress.isComplete(Clue.of(1, 2, 1), line) shouldBe true
        }

        @Test
        fun `a solver-derived empty reads the same as a players cross`() {
            val withCross = ClueProgress.satisfied(Clue.of(2), listOf(FILLED, FILLED, CROSSED))
            val withEmpty = ClueProgress.satisfied(Clue.of(2), listOf(FILLED, FILLED, EMPTY))
            withCross shouldBe withEmpty
        }

        @Test
        @DisplayName("a correctly filled line greys its clue even with nothing crossed")
        fun `a complete line without crosses is satisfied`() {
            // Seen on a device: rows filled correctly but never crossed kept their clues
            // lit, so finishing a row gave no feedback at all.
            ClueProgress.satisfied(Clue.of(2), listOf(FILLED, FILLED, UNKNOWN, UNKNOWN)) shouldBe
                listOf(true)
            ClueProgress.satisfied(
                Clue.of(1, 2),
                listOf(FILLED, UNKNOWN, FILLED, FILLED, UNKNOWN),
            ) shouldBe listOf(true, true)
        }

        @Test
        fun `a partially filled line is still not satisfied`() {
            // One filled cell of a two-cell run must not grey the clue.
            ClueProgress.satisfied(Clue.of(2), listOf(FILLED, UNKNOWN, UNKNOWN)) shouldBe listOf(false)
            // Too many runs for the clue.
            ClueProgress.satisfied(
                Clue.of(2),
                listOf(FILLED, FILLED, UNKNOWN, FILLED, UNKNOWN),
            ) shouldBe listOf(false)
        }

        @Test
        fun `a blank clue has no groups`() {
            ClueProgress.satisfied(Clue.EMPTY, listOf(UNKNOWN, UNKNOWN)) shouldBe emptyList()
            ClueProgress.isComplete(Clue.EMPTY, listOf(UNKNOWN, UNKNOWN)) shouldBe true
        }
    }

    @Nested
    inner class Layout {

        @Test
        fun `a fitted board sits inside its viewport`() {
            val m = BoardMetrics.fit(
                columns = 20, rows = 20,
                longestRowClue = 6, longestColClue = 6,
                viewportWidth = 1080f, viewportHeight = 1600f,
            )
            assertTrue(m.rowGutter + m.gridWidth <= 1080f + 0.5f) { "board is wider than the viewport" }
            assertTrue(m.colGutter + m.gridHeight <= 1600f + 0.5f) { "board is taller than the viewport" }
            m.zoom shouldBe BoardMetrics.MIN_ZOOM
        }

        @Test
        fun `a fitted board needs no panning`() {
            val m = BoardMetrics.fit(20, 20, 6, 6, 1080f, 1600f)
            m.needsPanning shouldBe false
            m.withPan(-500f, -500f).panX shouldBe 0f
        }

        @Test
        @DisplayName("hit testing maps points back to the cells they were drawn in")
        fun `cellAt inverts the layout`() {
            val m = BoardMetrics.fit(10, 10, 4, 4, 1000f, 1400f)
            for (row in 0 until 10) {
                for (col in 0 until 10) {
                    val x = m.cellLeft(col) + m.cellSize / 2f
                    val y = m.cellTop(row) + m.cellSize / 2f
                    m.cellAt(x, y) shouldBe CellRef(row, col)
                }
            }
        }

        @Test
        fun `points in the gutters are not cells`() {
            val m = BoardMetrics.fit(10, 10, 4, 4, 1000f, 1400f)
            m.cellAt(m.rowGutter / 2f, m.colGutter + m.cellSize) shouldBe null
            m.cellAt(m.rowGutter + m.cellSize, m.colGutter / 2f) shouldBe null
        }

        @Test
        fun `a point past the edge clamps to the nearest cell for an in-flight drag`() {
            val m = BoardMetrics.fit(10, 10, 4, 4, 1000f, 1400f)
            m.nearestCell(-500f, -500f) shouldBe CellRef(0, 0)
            m.nearestCell(99_999f, 99_999f) shouldBe CellRef(9, 9)
        }

        @Test
        fun `zooming in makes the board need panning`() {
            val fitted = BoardMetrics.fit(20, 20, 6, 6, 1080f, 1600f)
            val zoomed = fitted.withZoom(2.5f, BoardMetrics.baseCellSize(fitted))
            zoomed.zoom shouldBe 2.5f
            zoomed.needsPanning shouldBe true
            assertTrue(zoomed.cellSize > fitted.cellSize) { "zoom did not enlarge the cells" }
        }

        @Test
        fun `zoom is clamped to sensible bounds`() {
            val fitted = BoardMetrics.fit(20, 20, 6, 6, 1080f, 1600f)
            val base = BoardMetrics.baseCellSize(fitted)
            fitted.withZoom(0.1f, base).zoom shouldBe BoardMetrics.MIN_ZOOM
            fitted.withZoom(99f, base).zoom shouldBe BoardMetrics.MAX_ZOOM
        }

        @Test
        @DisplayName("pan cannot drag the grid away from its gutters or off the far edge")
        fun `pan is clamped both ways`() {
            val fitted = BoardMetrics.fit(20, 20, 6, 6, 1080f, 1600f)
            val zoomed = fitted.withZoom(3f, BoardMetrics.baseCellSize(fitted))

            // Cannot pan content to the right of its origin.
            zoomed.withPan(500f, 500f).panX shouldBe 0f

            // Cannot pan past the point where the last column reaches the right edge.
            val hardLeft = zoomed.withPan(-99_999f, 0f)
            val rightEdge = hardLeft.gridLeft + hardLeft.gridWidth
            assertTrue(rightEdge >= hardLeft.viewportWidth - 0.5f) {
                "panned past the end of the board: right edge $rightEdge"
            }
        }

        @Test
        fun `gutters scale with zoom so clues stay readable`() {
            val fitted = BoardMetrics.fit(15, 15, 5, 5, 1080f, 1600f)
            val zoomed = fitted.withZoom(2f, BoardMetrics.baseCellSize(fitted))
            val ratioBefore = fitted.rowGutter / fitted.cellSize
            val ratioAfter = zoomed.rowGutter / zoomed.cellSize
            assertTrue(kotlin.math.abs(ratioBefore - ratioAfter) < 0.01f) {
                "gutter/cell ratio drifted under zoom: $ratioBefore -> $ratioAfter"
            }
        }

        @Test
        fun `every shipped grid size lays out on a small screen`() {
            listOf(5, 10, 15, 20).forEach { size ->
                val m = BoardMetrics.fit(size, size, size / 2, size / 2, 720f, 1280f)
                assertTrue(m.cellSize > 0f) { "${size}x$size produced a zero cell size" }
                m.cellAt(m.cellLeft(0) + 1f, m.cellTop(0) + 1f) shouldBe CellRef(0, 0)
            }
        }
    }
}
