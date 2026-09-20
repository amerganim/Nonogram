package com.ganim.nonogram.game

import com.ganim.nonogram.puzzle.model.CellState

/**
 * Every rule of play, as pure functions over [GameState] (build plan 5.3).
 *
 * Nothing here touches Android or Compose, so the whole rule set is unit-tested on the
 * JVM rather than through the UI.
 *
 * ## Mistakes are permanent, and undo does not refund them
 *
 * The plan gives the player three lives and sells "restore a life" as a rewarded ad
 * placement (8.2). That only means anything if a life, once spent, is actually gone -
 * an undo that refunded lives would make the whole system free and the ad placement
 * worthless. So a wrong fill:
 *
 *  - costs a life,
 *  - reveals the cell as crossed, because the player now knows it is empty,
 *  - ends the gesture immediately, so a careless drag cannot burn all three lives,
 *  - and is left out of the undo entry, so undo steps over it.
 *
 * Crossing is never a mistake. A cross is a note, not an assertion.
 */
object GameEngine {

    // --- gestures --------------------------------------------------------------------

    /** Opens a new undo step. Every paint between here and [endGesture] undoes together. */
    fun beginGesture(state: GameState): GameState =
        if (!state.isPlayable) {
            state
        } else {
            state.copy(openGesture = UndoEntry(emptyList()), dragBlocked = false)
        }

    /**
     * Writes [target] into the cell at [index], if the rules allow it.
     *
     * Ignored when the game is over, when the cell is a revealed mistake, or when the
     * cell already holds [target] - so dragging back and forth over one cell does not
     * fill the undo stack with no-ops.
     */
    fun paint(state: GameState, index: Int, target: CellState): GameState {
        if (!state.isPlayable) return state
        // A wrong cell earlier in this drag stops the rest of it. One careless sweep
        // must cost one life, not three.
        if (state.dragBlocked) return state
        if (index !in state.board.indices) return state
        if (index in state.mistakeCells) return state

        val current = state.board[index]
        if (current == target) return state

        // Filling a cell the solution leaves empty is the only way to lose a life.
        val isWrongFill = target == CellState.FILLED && !state.solutionFilled(index)
        return if (isWrongFill) applyMistake(state, index) else applyPaint(state, index, current, target)
    }

    private fun applyPaint(
        state: GameState,
        index: Int,
        from: CellState,
        to: CellState,
    ): GameState {
        val board = state.board.with(index, to)
        val gesture = state.openGesture?.let { UndoEntry(it.changes + CellChange(index, from, to)) }
        return state
            .copy(board = board, openGesture = gesture)
            .withCompletionChecked()
    }

    private fun applyMistake(state: GameState, index: Int): GameState {
        val lives = state.livesRemaining - 1
        val board = state.board.with(index, CellState.CROSSED)
        return state.copy(
            board = board,
            mistakeCells = state.mistakeCells + index,
            livesRemaining = lives,
            status = if (lives <= 0) GameStatus.OUT_OF_LIVES else state.status,
            // The gesture ends here, and stays ended: a wrong cell stops the drag rather
            // than letting it plough on through the remaining lives.
            openGesture = null,
            dragBlocked = true,
        ).let { afterMistake ->
            if (state.openGesture != null && !state.openGesture.isEmpty) {
                afterMistake.pushUndo(state.openGesture)
            } else {
                afterMistake
            }
        }
    }

    /** Closes the open gesture, pushing it onto the undo stack unless it changed nothing. */
    fun endGesture(state: GameState): GameState {
        val gesture = state.openGesture ?: return state.copy(dragBlocked = false)
        val closed = state.copy(openGesture = null, dragBlocked = false)
        return if (gesture.isEmpty) closed else closed.pushUndo(gesture)
    }

    /** A tap: one cell, as its own undo step. */
    fun tap(state: GameState, index: Int): GameState {
        val target = nextStateForTap(state, index) ?: return state
        return endGesture(paint(beginGesture(state), index, target))
    }

    /**
     * What a tap on [index] should write.
     *
     * In FILL mode a tap cycles unknown to filled and back; in CROSS mode unknown to
     * crossed and back. A tap never converts a fill straight into a cross - that would
     * make a mis-tap destructive in a mode the player is not in.
     */
    fun nextStateForTap(state: GameState, index: Int): CellState? {
        if (!state.isPlayable || index !in state.board.indices) return null
        if (index in state.mistakeCells) return null
        val current = state.board[index]
        return when (state.paintMode) {
            PaintMode.FILL -> if (current == CellState.FILLED) CellState.UNKNOWN else CellState.FILLED
            PaintMode.CROSS -> if (current == CellState.CROSSED) CellState.UNKNOWN else CellState.CROSSED
        }
    }

    /**
     * What a drag starting on [index] should write into every cell it crosses.
     *
     * Build plan 5.2: "The mode is locked by the *first* cell of the drag: if the drag
     * starts on an empty cell in fill mode, the whole drag fills; if it starts on a
     * filled cell, the whole drag clears. This prevents the flickering mess of per-cell
     * toggling."
     *
     * [overrideMode] supports long-press to paint the opposite mode without switching.
     */
    fun dragTargetFor(
        state: GameState,
        index: Int,
        overrideMode: PaintMode? = null,
    ): CellState? {
        if (!state.isPlayable || index !in state.board.indices) return null
        if (index in state.mistakeCells) return null
        val mode = overrideMode ?: state.paintMode
        val current = state.board[index]
        return when (mode) {
            PaintMode.FILL -> if (current == CellState.FILLED) CellState.UNKNOWN else CellState.FILLED
            PaintMode.CROSS -> if (current == CellState.CROSSED) CellState.UNKNOWN else CellState.CROSSED
        }
    }

    // --- undo ------------------------------------------------------------------------

    /**
     * Reverses the most recent gesture.
     *
     * Reverses paints, crosses, clears and hint reveals alike - anything recorded as a
     * [CellChange]. Lost lives and revealed mistakes are not in the stack and stay lost.
     */
    fun undo(state: GameState): GameState {
        val entry = state.undoStack.lastOrNull() ?: return state
        // One copy for the whole gesture, unwound in reverse so a gesture that touched
        // the same cell twice ends up back where it started.
        val board = state.board.mutate {
            entry.changes.asReversed().forEach { change -> this[change.index] = change.from }
        }
        return state.copy(
            board = board,
            undoStack = state.undoStack.dropLast(1),
            openGesture = null,
            // Undoing out of a finished board puts the player back in play.
            status = if (state.status == GameStatus.COMPLETE) GameStatus.PLAYING else state.status,
        ).withCompletionChecked()
    }

    private fun GameState.pushUndo(entry: UndoEntry): GameState {
        val stack = undoStack + entry
        return copy(undoStack = if (stack.size > GameState.UNDO_LIMIT) stack.takeLast(GameState.UNDO_LIMIT) else stack)
    }

    // --- other actions ---------------------------------------------------------------

    fun toggleMode(state: GameState): GameState = state.copy(
        paintMode = if (state.paintMode == PaintMode.FILL) PaintMode.CROSS else PaintMode.FILL,
    )

    fun tick(state: GameState, deltaMs: Long): GameState =
        if (!state.isPlayable) state else state.copy(elapsedMs = state.elapsedMs + deltaMs)

    /** Grants one life back, after a rewarded ad (8.2) or a no-fill fallback. */
    fun restoreLife(state: GameState): GameState =
        if (state.status != GameStatus.OUT_OF_LIVES) state
        else state.copy(livesRemaining = 1, status = GameStatus.PLAYING)

    fun restart(state: GameState): GameState = GameState.newGame(state.puzzle)

    /**
     * Reveals [index] as part of a hint: filled cells become filled, empty ones crossed.
     *
     * Recorded as its own undo step, so a hint can be taken back like any other action.
     */
    fun revealHint(state: GameState, index: Int): GameState {
        if (!state.isPlayable || index !in state.board.indices) return state
        val truth = if (state.solutionFilled(index)) CellState.FILLED else CellState.CROSSED
        val current = state.board[index]
        if (current == truth) return state

        val board = state.board.with(index, truth)
        return state
            .copy(
                board = board,
                hintsUsed = state.hintsUsed + 1,
                // A hint can overwrite a wrong cross, so clear any mistake flag on it.
                mistakeCells = state.mistakeCells - index,
                openGesture = null,
            )
            .pushUndo(UndoEntry(listOf(CellChange(index, current, truth))))
            .withCompletionChecked()
    }

    // --- completion ------------------------------------------------------------------

    /**
     * The puzzle is done when the filled cells match the solution exactly.
     *
     * Crosses are ignored: they are the player's notes, and demanding a complete set of
     * them would punish players who do not use them.
     */
    private fun GameState.withCompletionChecked(): GameState {
        if (status == GameStatus.OUT_OF_LIVES) return this
        // Every filled cell is necessarily correct - a wrong fill becomes a mistake and
        // is written as a cross instead - so the board is finished exactly when the right
        // number of cells are filled. That turns an O(cells) scan on every painted cell
        // into a single comparison.
        val solved = filledCount == puzzle.targetFilledCount
        val next = if (solved) GameStatus.COMPLETE else GameStatus.PLAYING
        return if (next == status) this else copy(status = next)
    }
}
