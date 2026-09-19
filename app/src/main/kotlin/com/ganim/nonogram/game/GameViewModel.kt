package com.ganim.nonogram.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ganim.nonogram.data.session.GameSessionStore
import com.ganim.nonogram.puzzle.model.Puzzle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Something worth a tick of haptic feedback (build plan 5.2). */
enum class HapticKind {
    /** A cell changed value. The constant tick that makes painting feel physical. */
    CELL,

    /** Mode switched, long-press engaged, undo - a heavier confirmation. */
    CONFIRM,

    /** A wrong cell. */
    MISTAKE,
}

/**
 * Holds the game for one puzzle and mediates between input, rules and storage.
 *
 * Deliberately thin: every rule lives in [GameEngine], every hint decision in
 * [HintProvider], all layout arithmetic in [BoardMetrics]. What is left here is
 * lifecycle - the timer, autosave and one-shot effects like haptics.
 *
 * Dependencies arrive through the constructor. The plan calls Hilt overkill at this
 * size (section 2), and [Factory] is all the wiring this needs.
 */
class GameViewModel(
    puzzle: Puzzle,
    private val sessionStore: GameSessionStore,
    private val hintProvider: HintProvider = HintProvider(),
    restored: GameState? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(restored ?: GameState.newGame(puzzle))
    val state: StateFlow<GameState> = _state.asStateFlow()

    private val _haptics = MutableSharedFlow<HapticKind>(extraBufferCapacity = 8)
    val haptics: SharedFlow<HapticKind> = _haptics

    /**
     * Phase 2 keeps this in memory. The plan puts settings and DataStore in Phase 3
     * (6.1), and persisting it here would mean building half that screen early.
     */
    var hapticsEnabled: Boolean = true
        private set

    private val dragTracker = DragTracker()

    /** The cell under the finger, for the row/column highlight (5.1). */
    private val _highlight = MutableStateFlow<CellRef?>(null)
    val highlight: StateFlow<CellRef?> = _highlight.asStateFlow()

    private var timerRunning = false

    init {
        // Autosave. collectLatest cancels a pending save when the state changes again,
        // so a fast drag coalesces into one write shortly after the finger lifts rather
        // than hammering the disk once per cell. Section 5.3 wants every change saved;
        // what matters is that no change is ever *lost*, which the debounce preserves
        // because saveNow() runs on stop.
        viewModelScope.launch {
            _state.collectLatest { snapshot ->
                kotlinx.coroutines.delay(SAVE_DEBOUNCE_MS)
                withContext(Dispatchers.IO) { runCatching { sessionStore.save(snapshot) } }
            }
        }
    }

    // --- lifecycle -------------------------------------------------------------------

    /** Starts the clock. Called when the screen resumes. */
    fun onResume() {
        if (timerRunning) return
        timerRunning = true
        viewModelScope.launch {
            var last = System.nanoTime()
            while (isActive && timerRunning) {
                kotlinx.coroutines.delay(TIMER_TICK_MS)
                val now = System.nanoTime()
                val deltaMs = (now - last) / 1_000_000
                last = now
                if (_state.value.isPlayable) {
                    _state.value = GameEngine.tick(_state.value, deltaMs)
                }
            }
        }
    }

    /** Stops the clock and forces an immediate save, so a kill after this loses nothing. */
    fun onPause() {
        timerRunning = false
        saveNow()
    }

    fun saveNow() {
        val snapshot = _state.value
        viewModelScope.launch(Dispatchers.IO) { runCatching { sessionStore.save(snapshot) } }
    }

    // --- input -----------------------------------------------------------------------

    fun onTap(cell: CellRef) {
        val index = indexOf(cell) ?: return
        val before = _state.value
        val next = GameEngine.tap(before, index)
        emitChangeFeedback(before, next)
        _state.value = next
    }

    /**
     * Begins a drag. The first cell decides what the whole drag writes (5.2).
     *
     * [oppositeMode] comes from a long press, which paints the other mode for this
     * gesture only without flipping the mode button.
     */
    fun onDragStart(cell: CellRef, oppositeMode: Boolean = false) {
        val index = indexOf(cell) ?: return
        val mode = if (oppositeMode) oppositeOf(_state.value.paintMode) else null
        dragTarget = GameEngine.dragTargetFor(_state.value, index, mode) ?: return
        dragTracker.begin(cell)

        val before = GameEngine.beginGesture(_state.value)
        val next = GameEngine.paint(before, index, dragTarget)
        emitChangeFeedback(before, next)
        _state.value = next
        _highlight.value = cell
    }

    /** Continues a drag. [dx]/[dy] are pixels from where the drag began, for axis lock. */
    fun onDragTo(cell: CellRef, dx: Float, dy: Float) {
        val painted = dragTracker.resolve(cell, dx, dy) ?: return
        _highlight.value = painted
        val index = indexOf(painted) ?: return

        val before = _state.value
        val next = GameEngine.paint(before, index, dragTarget)
        if (next !== before) {
            emitChangeFeedback(before, next)
            _state.value = next
        }
    }

    fun onDragEnd() {
        dragTracker.end()
        _state.value = GameEngine.endGesture(_state.value)
        _highlight.value = null
    }

    fun onHighlight(cell: CellRef?) {
        _highlight.value = cell
    }

    // --- actions ---------------------------------------------------------------------

    fun toggleMode() {
        _state.value = GameEngine.toggleMode(_state.value)
        emit(HapticKind.CONFIRM)
    }

    fun toggleHaptics() {
        hapticsEnabled = !hapticsEnabled
    }

    fun undo() {
        val before = _state.value
        val next = GameEngine.undo(before)
        if (next !== before) {
            _state.value = next
            emit(HapticKind.CONFIRM)
        }
    }

    /**
     * Reveals one deducible cell (5.4).
     *
     * @return false when nothing is left to deduce, so the caller can avoid spending a
     * hint or showing a rewarded ad for nothing.
     */
    fun useHint(): Boolean {
        val index = hintProvider.nextHint(_state.value) ?: return false
        _state.value = GameEngine.revealHint(_state.value, index)
        emit(HapticKind.CONFIRM)
        return true
    }

    fun restoreLife() {
        _state.value = GameEngine.restoreLife(_state.value)
    }

    fun restart() {
        _state.value = GameEngine.restart(_state.value)
    }

    // --- internals -------------------------------------------------------------------

    private var dragTarget = com.ganim.nonogram.puzzle.model.CellState.FILLED

    private fun indexOf(cell: CellRef): Int? {
        val puzzle = _state.value.puzzle
        if (cell.row !in 0 until puzzle.height || cell.col !in 0 until puzzle.width) return null
        return cell.row * puzzle.width + cell.col
    }

    private fun oppositeOf(mode: PaintMode) =
        if (mode == PaintMode.FILL) PaintMode.CROSS else PaintMode.FILL

    /** Build plan 5.2: "Haptics on every cell state change." */
    private fun emitChangeFeedback(before: GameState, after: GameState) {
        if (before === after) return
        when {
            after.livesRemaining < before.livesRemaining -> emit(HapticKind.MISTAKE)
            after.board !== before.board -> emit(HapticKind.CELL)
        }
    }

    private fun emit(kind: HapticKind) {
        if (hapticsEnabled) _haptics.tryEmit(kind)
    }

    /** Manual constructor injection, per the plan's "DI: Manual" decision. */
    class Factory(
        private val puzzle: Puzzle,
        private val sessionStore: GameSessionStore,
        private val restored: GameState?,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            GameViewModel(puzzle, sessionStore, HintProvider(), restored) as T
    }

    private companion object {
        const val TIMER_TICK_MS = 250L
        const val SAVE_DEBOUNCE_MS = 150L
    }
}
