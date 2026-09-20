package com.ganim.nonogram.progression

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ganim.nonogram.data.repo.ProgressRepository
import com.ganim.nonogram.data.repo.PuzzleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** One level as the screen needs it: the rung, plus what the player has done with it. */
data class LevelUi(
    val level: LevelLadder.Level,
    val completed: Boolean,
    val started: Boolean,
    /** The level the home screen is steering towards. Exactly one is true. */
    val isNext: Boolean,
)

data class StageUi(
    val stage: LevelLadder.Stage,
    val levels: List<LevelUi>,
    val completedCount: Int,
) {
    val total: Int get() = levels.size
    val fraction: Float get() = if (total == 0) 0f else completedCount.toFloat() / total
    /** True once every level in the stage is solved. */
    val cleared: Boolean get() = total > 0 && completedCount == total
}

data class PlayUiState(
    val stages: List<StageUi> = emptyList(),
    val next: LevelUi? = null,
    val solved: Int = 0,
    val total: Int = 0,
    val loaded: Boolean = false,
)

/**
 * Drives the Play screen: the ladder, and where the player is on it.
 *
 * The ladder itself is built once and kept - it is a pure function of the pack, which
 * does not change while the app is running. Only the two id sets move, so a completed
 * puzzle re-projects the state without re-deriving which puzzles the levels are.
 */
class PlayViewModel(
    puzzles: PuzzleRepository,
    progress: ProgressRepository,
) : ViewModel() {

    private val stages = LevelLadder.build(puzzles.entries)

    private val _state = MutableStateFlow(PlayUiState())
    val state: StateFlow<PlayUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                progress.observeCompletedIds(),
                progress.observeInProgressIds(),
            ) { completed, started ->
                val nextLevel = LevelLadder.next(stages, completed)
                val projected = stages.map { stage ->
                    val levels = stage.levels.map { level ->
                        LevelUi(
                            level = level,
                            completed = level.id in completed,
                            started = level.id in started,
                            isNext = level.id == nextLevel?.id,
                        )
                    }
                    StageUi(
                        stage = stage.stage,
                        levels = levels,
                        completedCount = levels.count { it.completed },
                    )
                }
                PlayUiState(
                    stages = projected,
                    next = projected.firstNotNullOfOrNull { s -> s.levels.firstOrNull { it.isNext } },
                    solved = projected.sumOf { it.completedCount },
                    total = projected.sumOf { it.total },
                    loaded = true,
                )
            }.collect { _state.value = it }
        }
    }

    class Factory(
        private val puzzles: PuzzleRepository,
        private val progress: ProgressRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PlayViewModel(puzzles, progress) as T
    }
}
