package com.ganim.nonogram.daily

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ganim.nonogram.data.db.DailyRecordEntity
import com.ganim.nonogram.data.repo.ProgressRepository
import com.ganim.nonogram.data.repo.PuzzleRepository
import com.ganim.nonogram.puzzle.model.Difficulty
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** One square in the calendar month view (build plan 6.2). */
data class CalendarDay(
    val date: LocalDate,
    val inCurrentMonth: Boolean,
    val isToday: Boolean,
    val isFuture: Boolean,
    val completed: Boolean,
    val started: Boolean,
)

/** Everything the Daily screen shows. */
data class DailyUiState(
    val today: LocalDate = LocalDate.now(),
    val puzzleId: String? = null,
    val size: Int = 0,
    val difficulty: Difficulty = Difficulty.EASY,
    val todayCompleted: Boolean = false,
    val todayStarted: Boolean = false,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val totalCompleted: Int = 0,
    val freezeProtecting: Boolean = false,
    val freezeAvailable: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val days: List<CalendarDay> = emptyList(),
)

/**
 * Drives the Daily screen (build plan 6.2, 6.3).
 *
 * Which puzzle today gets is not stored anywhere and never fetched - it falls out of the
 * date through [DailySelector]. The database only records what the player *did*.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DailyViewModel(
    private val puzzles: PuzzleRepository,
    private val progress: ProgressRepository,
    private val clock: GameClock,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.from(clock.today()))

    private val _state = MutableStateFlow(DailyUiState(today = clock.today()))
    val state: StateFlow<DailyUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Rolling the month over on launch keeps the freeze allowance honest even if
            // the player has not finished anything since the month turned.
            progress.refreshFreezeAllowance()
            refreshToday()
        }

        viewModelScope.launch {
            combine(
                progress.observeStats(),
                progress.observeInProgressIds(),
                month.flatMapLatest { progress.observeMonth(it) },
                month,
            ) { stats, inProgress, records, shownMonth ->
                val today = clock.today()
                val spec = DailySchedule.specFor(today)
                val todayId = puzzles.dailyIdFor(today)

                _state.value.copy(
                    today = today,
                    puzzleId = todayId,
                    size = spec.size,
                    difficulty = spec.difficulty,
                    todayCompleted = records[today]?.completed == true,
                    todayStarted = todayId != null && todayId in inProgress,
                    currentStreak = StreakCalculator.displayStreak(stats, today),
                    bestStreak = stats.bestStreak,
                    totalCompleted = stats.totalCompleted,
                    freezeProtecting = StreakCalculator.isFreezeProtecting(stats, today),
                    freezeAvailable = StreakCalculator.freezeAvailableOn(stats, today),
                    month = shownMonth,
                    days = buildCalendar(shownMonth, today, records, inProgress),
                )
            }.collect { _state.value = it }
        }
    }

    /** Ensures today's assignment exists, so the calendar can show it before it is played. */
    private suspend fun refreshToday() {
        val today = clock.today()
        puzzles.dailyIdFor(today)?.let { progress.recordDailyAssignment(today, it) }
    }

    fun showMonth(target: YearMonth) {
        // Nothing beyond the current month exists to show.
        month.value = minOf(target, YearMonth.from(clock.today()))
    }

    fun previousMonth() = showMonth(month.value.minusMonths(1))

    fun nextMonth() = showMonth(month.value.plusMonths(1))

    /** The puzzle id for any past date, so the calendar can open it. */
    fun puzzleIdFor(date: LocalDate): String? = puzzles.dailyIdFor(date)

    /**
     * Builds a six-week grid starting on Monday.
     *
     * Leading and trailing days from the neighbouring months are included so the grid is
     * rectangular; they are marked [CalendarDay.inCurrentMonth] false and drawn muted.
     */
    private fun buildCalendar(
        month: YearMonth,
        today: LocalDate,
        records: Map<LocalDate, DailyRecordEntity>,
        inProgress: Set<String>,
    ): List<CalendarDay> {
        val first = month.atDay(1)
        // Monday is 1, so this backs up to the Monday on or before the 1st.
        val gridStart = first.minusDays((first.dayOfWeek.value - 1).toLong())
        return (0 until CALENDAR_CELLS).map { offset ->
            val date = gridStart.plusDays(offset.toLong())
            val record = records[date]
            CalendarDay(
                date = date,
                inCurrentMonth = YearMonth.from(date) == month,
                isToday = date == today,
                isFuture = date.isAfter(today),
                completed = record?.completed == true,
                started = record?.completed != true &&
                    puzzles.dailyIdFor(date)?.let { it in inProgress } == true,
            )
        }
    }

    class Factory(
        private val puzzles: PuzzleRepository,
        private val progress: ProgressRepository,
        private val clock: GameClock,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DailyViewModel(puzzles, progress, clock) as T
    }

    private companion object {
        /** Six weeks, which is the most any month can span. */
        const val CALENDAR_CELLS = 42
    }
}
