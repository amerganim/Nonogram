package com.ganim.nonogram.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ganim.nonogram.data.repo.PuzzleCollection
import com.ganim.nonogram.data.repo.ProgressRepository
import com.ganim.nonogram.data.repo.PuzzleRepository
import com.ganim.nonogram.puzzle.model.Difficulty
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Completion filter for the archive (build plan 6.4). */
enum class CompletionFilter(val label: String) {
    ALL("All"),
    SOLVED("Solved"),
    UNSOLVED("Unsolved"),
    IN_PROGRESS("Started"),
}

/** One row of the archive listing. */
data class ArchiveItem(
    val index: Int,
    val id: String,
    val size: Int,
    val difficulty: Difficulty,
    val completed: Boolean,
    val inProgress: Boolean,
    val collection: PuzzleCollection,
    /** What the finished picture shows. Empty for generated puzzles. */
    val name: String,
)

data class ArchiveUiState(
    val collection: PuzzleCollection = PuzzleCollection.GENERATED,
    val pictureCount: Int = 0,
    val total: Int = 0,
    val completedCount: Int = 0,
    /** Solved out of [total], within the collection being shown. */
    val completedHere: Int = 0,
    val sizes: List<Int> = emptyList(),
    val sizeFilter: Int? = null,
    val difficultyFilter: Difficulty? = null,
    val completionFilter: CompletionFilter = CompletionFilter.ALL,
    val visible: List<ArchiveItem> = emptyList(),
)

/**
 * Drives the archive (build plan 6.4).
 *
 * Filtering runs over a pre-built index of record headers - no grid is decoded to decide
 * whether a puzzle matches. Grids are decoded only for thumbnails that scroll into view,
 * which is what keeps a 5,000 item list smooth.
 */
class ArchiveViewModel(
    private val puzzles: PuzzleRepository,
    progress: ProgressRepository,
) : ViewModel() {

    private val sizeFilter = MutableStateFlow<Int?>(null)
    private val difficultyFilter = MutableStateFlow<Difficulty?>(null)
    private val completionFilter = MutableStateFlow(CompletionFilter.ALL)
    private val collection = MutableStateFlow(PuzzleCollection.GENERATED)

    private val _state = MutableStateFlow(ArchiveUiState())
    val state: StateFlow<ArchiveUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                progress.observeCompletedIds(),
                progress.observeInProgressIds(),
                combine(sizeFilter, difficultyFilter) { a, b -> a to b },
                completionFilter,
                collection,
            ) { completed, started, filters, completion, shownCollection ->
                val (size, difficulty) = filters
                val source = when (shownCollection) {
                    PuzzleCollection.GENERATED -> puzzles.filter(size, difficulty)
                    // Twenty-three drawings do not need size or difficulty filters; the
                    // whole set fits on two screens.
                    PuzzleCollection.PICTURE -> puzzles.pictures
                }
                val visible = source.mapNotNull { entry ->
                    val isCompleted = entry.id in completed
                    val isStarted = entry.id in started
                    val keep = when (completion) {
                        CompletionFilter.ALL -> true
                        CompletionFilter.SOLVED -> isCompleted
                        CompletionFilter.UNSOLVED -> !isCompleted
                        CompletionFilter.IN_PROGRESS -> isStarted
                    }
                    if (!keep) {
                        null
                    } else {
                        ArchiveItem(
                            index = entry.index,
                            id = entry.id,
                            size = entry.size,
                            difficulty = entry.difficulty,
                            completed = isCompleted,
                            inProgress = isStarted,
                            collection = entry.collection,
                            name = entry.name,
                        )
                    }
                }

                ArchiveUiState(
                    collection = shownCollection,
                    pictureCount = puzzles.pictureCount,
                    total = if (shownCollection == PuzzleCollection.PICTURE) puzzles.pictureCount else puzzles.count,
                    completedCount = completed.size,
                    completedHere = source.count { it.id in completed },
                    sizes = puzzles.availableSizes,
                    sizeFilter = size,
                    difficultyFilter = difficulty,
                    completionFilter = completion,
                    visible = visible,
                )
            }.collect { _state.value = it }
        }
    }

    fun setCollection(value: PuzzleCollection) {
        collection.value = value
    }

    fun setSizeFilter(size: Int?) {
        sizeFilter.value = size
    }

    fun setDifficultyFilter(difficulty: Difficulty?) {
        difficultyFilter.value = difficulty
    }

    fun setCompletionFilter(filter: CompletionFilter?) {
        completionFilter.value = filter ?: CompletionFilter.ALL
    }

    /** Decodes one grid, for a thumbnail that has scrolled into view. */
    fun solutionFor(item: ArchiveItem): BooleanArray = when (item.collection) {
        PuzzleCollection.GENERATED -> puzzles.puzzleAt(item.index).solution
        PuzzleCollection.PICTURE -> puzzles.pictureAt(item.index).solution
    }

    class Factory(
        private val puzzles: PuzzleRepository,
        private val progress: ProgressRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ArchiveViewModel(puzzles, progress) as T
    }
}
