package com.ganim.nonogram.puzzle.solver

import com.ganim.nonogram.puzzle.model.CellState
import com.ganim.nonogram.puzzle.model.Clue
import com.ganim.nonogram.puzzle.model.Grid
import com.ganim.nonogram.puzzle.model.Puzzle

/**
 * Applies [LineSolver] to every row and then every column, repeating until a full pass
 * changes nothing (build plan 4.3).
 *
 * The number of passes needed is reported as [SolveResult.Unique.depth] and is the
 * primary difficulty signal fed to the rater.
 *
 * Backtracking is deliberately absent. A puzzle that stalls here is rejected rather
 * than solved by search, because "stalls here" is exactly the definition of a puzzle
 * the player would have to guess at.
 *
 * ## Not thread-safe
 * Holds a [LineSolver]. One instance per thread.
 */
class PuzzleSolver(private val lineSolver: LineSolver = LineSolver()) {

    /** Solves [puzzle] from a blank board, ignoring its stored solution. */
    fun solve(puzzle: Puzzle): SolveResult =
        run(puzzle.width, puzzle.height, puzzle.rowClues, puzzle.colClues).result

    fun solve(width: Int, height: Int, rowClues: List<Clue>, colClues: List<Clue>): SolveResult =
        run(width, height, rowClues, colClues).result

    /**
     * Runs deduction to exhaustion and returns the final board along with the verdict.
     *
     * [initial] seeds the board - pass the player's in-progress board to find what is
     * deducible for them right now. When null the board starts blank, which is the
     * generator's path and the one that decides whether a puzzle is shippable.
     */
    fun run(
        width: Int,
        height: Int,
        rowClues: List<Clue>,
        colClues: List<Clue>,
        initial: Array<CellState>? = null,
    ): SolveRun {
        require(rowClues.size == height) { "Expected $height row clues, got ${rowClues.size}" }
        require(colClues.size == width) { "Expected $width column clues, got ${colClues.size}" }

        val board = initial?.copyOf() ?: Array(width * height) { CellState.UNKNOWN }
        require(board.size == width * height) { "Initial board has the wrong size" }

        var unknown = board.count { it == CellState.UNKNOWN }
        val rowDirty = BooleanArray(height) { true }
        val colDirty = BooleanArray(width) { true }
        val cellsPerPass = ArrayList<Int>()

        var depth = 0
        while (true) {
            var changed = false
            val unknownAtPassStart = unknown

            for (r in 0 until height) {
                if (!rowDirty[r]) continue
                rowDirty[r] = false
                val offset = r * width
                val line = List(width) { c -> board[offset + c] }
                val solved = lineSolver.solveOrNull(rowClues[r], line)
                    ?: return SolveRun(
                        SolveResult.Contradiction, board, width, height,
                        SolveStats(cellsPerPass, board.size),
                    )
                for (c in 0 until width) {
                    val next = solved[c]
                    if (next != board[offset + c]) {
                        if (board[offset + c] == CellState.UNKNOWN) unknown--
                        board[offset + c] = next
                        changed = true
                        colDirty[c] = true
                    }
                }
            }

            for (c in 0 until width) {
                if (!colDirty[c]) continue
                colDirty[c] = false
                val line = List(height) { r -> board[r * width + c] }
                val solved = lineSolver.solveOrNull(colClues[c], line)
                    ?: return SolveRun(
                        SolveResult.Contradiction, board, width, height,
                        SolveStats(cellsPerPass, board.size),
                    )
                for (r in 0 until height) {
                    val next = solved[r]
                    if (next != board[r * width + c]) {
                        if (board[r * width + c] == CellState.UNKNOWN) unknown--
                        board[r * width + c] = next
                        changed = true
                        rowDirty[r] = true
                    }
                }
            }

            depth++
            cellsPerPass.add(unknownAtPassStart - unknown)
            val stats = SolveStats(cellsPerPass, board.size)
            if (unknown == 0) {
                verifyOrThrow(width, height, rowClues, colClues, board)
                return SolveRun(SolveResult.Unique(depth), board, width, height, stats)
            }
            if (!changed) return SolveRun(SolveResult.Ambiguous, board, width, height, stats)
        }
    }

    /**
     * A completed board must reproduce the clues it was solved from. It always will
     * unless [LineSolver] made an unsound deduction, which is the one class of bug that
     * would silently ship broken puzzles - so this asserts rather than trusting.
     */
    private fun verifyOrThrow(
        width: Int,
        height: Int,
        rowClues: List<Clue>,
        colClues: List<Clue>,
        board: Array<CellState>,
    ) {
        val cells = BooleanArray(width * height) { board[it] == CellState.FILLED }
        val actualRows = Grid.rowClues(width, height, cells)
        val actualCols = Grid.columnClues(width, height, cells)
        check(actualRows == rowClues && actualCols == colClues) {
            buildString {
                appendLine("Solver produced a board that does not match its clues.")
                appendLine("This is a LineSolver soundness bug, not a bad puzzle.")
                appendLine(Grid.render(width, height, cells))
                appendLine("expected rows: $rowClues")
                appendLine("actual   rows: $actualRows")
                appendLine("expected cols: $colClues")
                appendLine("actual   cols: $actualCols")
            }
        }
    }

    companion object {
        /** A blank board of the given shape. */
        fun blankBoard(width: Int, height: Int): Array<CellState> =
            Array(width * height) { CellState.UNKNOWN }
    }
}
