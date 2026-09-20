package com.ganim.nonogram.progression

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ganim.nonogram.data.repo.ProgressRepository
import com.ganim.nonogram.data.repo.PuzzleRepository
import com.ganim.nonogram.puzzle.model.Difficulty
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

/**
 * The free-play picker: a bucket of the generated pack, chosen by size and level.
 *
 * The 5,000 puzzles are a supply, not a catalogue. Nobody scrolls five thousand
 * thumbnails looking for one; what a player who has finished the ladder actually wants
 * is "another 15x15 medium". So the bucket is the unit, and the action is "play one".
 */
data class FreePlayUi(
    val sizes: List<Int> = emptyList(),
    val size: Int = 0,
    /** Only the levels this size actually ships at - see [PlayViewModel.difficultiesFor]. */
    val difficulties: List<Difficulty> = emptyList(),
    val difficulty: Difficulty = Difficulty.EASY,
    val solved: Int = 0,
    val total: Int = 0,
) {
    val allSolved: Boolean get() = total > 0 && solved >= total
}

data class PlayUiState(
    val stages: List<StageUi> = emptyList(),
    val next: LevelUi? = null,
    val solved: Int = 0,
    val total: Int = 0,
    val loaded: Boolean = false,
    val freePlay: FreePlayUi = FreePlayUi(),
)

/**
 * Drives the Play screen: the ladder, and where the player is on it.
 *
 * The ladder itself is built once and kept - it is a pure function of the pack, which
 * does not change while the app is running. Only the two id sets move, so a completed
 * puzzle re-projects the state without re-deriving which puzzles the levels are.
 */
class PlayViewModel(
    private val puzzles: PuzzleRepository,
    progress: ProgressRepository,
) : ViewModel() {

    private val stages = LevelLadder.build(puzzles.entries)

    /**
     * Which levels exist at each size.
     *
     * The bands are relative within a size, so the pack only ships some combinations -
     * there is no 5x5 expert. Offering one would hand the player an empty bucket and no
     * explanation, so the difficulty chips are derived from the pack rather than from
     * the enum.
     */
    private val levelsBySize: Map<Int, List<Difficulty>> =
        puzzles.entries.groupBy { it.size }
            .mapValues { (_, group) -> group.map { it.difficulty }.distinct().sorted() }

    private val bucket = MutableStateFlow(
        puzzles.availableSizes.first().let { size ->
            size to levelsBySize.getValue(size).first()
        },
    )

    private val entries = puzzles.entries

    /** The levels [size] ships at, for the chips. */
    fun difficultiesFor(size: Int): List<Difficulty> = levelsBySize[size].orEmpty()

    fun setFreeSize(size: Int) {
        // Carry the level across if this size has it; otherwise fall to its easiest,
        // because silently keeping an impossible pair would empty the bucket.
        val levels = difficultiesFor(size)
        val keep = bucket.value.second.takeIf { it in levels } ?: levels.first()
        bucket.value = size to keep
    }

    fun setFreeDifficulty(difficulty: Difficulty) {
        bucket.value = bucket.value.first to difficulty
    }

    /**
     * A puzzle from the current bucket that has not been solved yet, at random.
     *
     * Random rather than "the next one in pack order": free play is where someone goes
     * when they want another puzzle, not a second ladder, and a predictable order would
     * make it one. Falls back to any puzzle in the bucket once they are all solved, so
     * the button never does nothing.
     */
    fun pickFreePuzzle(): String? {
        val (size, difficulty) = bucket.value
        val pool = entries.filter { it.size == size && it.difficulty == difficulty }
        if (pool.isEmpty()) return null
        val done = solvedIds
        return (pool.filter { it.id !in done }.ifEmpty { pool }).random().id
    }

    private var solvedIds: Set<String> = emptySet()

    private val _state = MutableStateFlow(PlayUiState())
    val state: StateFlow<PlayUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                progress.observeCompletedIds(),
                progress.observeInProgressIds(),
                bucket,
            ) { completed, started, chosen ->
                solvedIds = completed
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
                val (size, difficulty) = chosen
                val pool = puzzles.filter(size, difficulty)

                PlayUiState(
                    freePlay = FreePlayUi(
                        sizes = puzzles.availableSizes,
                        size = size,
                        difficulties = difficultiesFor(size),
                        difficulty = difficulty,
                        solved = pool.count { it.id in completed },
                        total = pool.size,
                    ),
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
