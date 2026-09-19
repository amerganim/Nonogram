package com.ganim.nonogram.data.repo

import com.ganim.nonogram.daily.GameClock
import com.ganim.nonogram.daily.StreakCalculator
import com.ganim.nonogram.daily.StreakState
import com.ganim.nonogram.data.db.DailyRecordDao
import com.ganim.nonogram.data.db.DailyRecordEntity
import com.ganim.nonogram.data.db.ProgressState
import com.ganim.nonogram.data.db.PuzzleProgressDao
import com.ganim.nonogram.data.db.PuzzleProgressEntity
import com.ganim.nonogram.data.db.UserStatsDao
import com.ganim.nonogram.data.db.UserStatsEntity
import com.ganim.nonogram.data.session.BoardSnapshotCodec
import com.ganim.nonogram.game.GameState
import com.ganim.nonogram.game.GameStatus
import com.ganim.nonogram.puzzle.model.Puzzle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth

/**
 * Everything the app knows about what the player has done (build plan 6.5).
 *
 * Sits between the DAOs and the screens so the streak rules stay in one place. The DAOs
 * are interfaces, so this whole class is tested against in-memory fakes on the JVM -
 * including the streak transitions, which the plan insists must be unit-tested against a
 * mockable clock rather than by changing the device date.
 */
class ProgressRepository(
    private val progressDao: PuzzleProgressDao,
    private val dailyDao: DailyRecordDao,
    private val statsDao: UserStatsDao,
    private val clock: GameClock = GameClock.System,
) {

    // --- puzzle progress -------------------------------------------------------------

    /** The saved board for [puzzle], or null if it has not been started. */
    suspend fun loadInProgress(puzzle: Puzzle): GameState? {
        val row = progressDao.find(puzzle.id) ?: return null
        val snapshot = row.boardSnapshot ?: return null
        return BoardSnapshotCodec.decode(snapshot, puzzle)
    }

    /**
     * Records the current board.
     *
     * A completed puzzle drops its snapshot: the solution already describes it, and
     * keeping a blob per finished puzzle would grow the database for nothing.
     */
    suspend fun save(state: GameState) {
        val completed = state.status == GameStatus.COMPLETE
        progressDao.upsert(
            PuzzleProgressEntity(
                puzzleId = state.puzzle.id,
                state = if (completed) ProgressState.COMPLETED else ProgressState.IN_PROGRESS,
                elapsedMs = state.elapsedMs,
                mistakes = GameState.MAX_LIVES - state.livesRemaining,
                completedAt = if (completed) nowMillis() else null,
                boardSnapshot = if (completed) null else BoardSnapshotCodec.encode(state),
            ),
        )
    }

    /** The puzzle to offer as "continue", if one was left unfinished. */
    suspend fun mostRecentInProgressId(): String? = progressDao.mostRecentInProgress()?.puzzleId

    fun observeCompletedIds(): Flow<Set<String>> =
        progressDao.observeCompletedIds().map { it.toSet() }

    fun observeInProgressIds(): Flow<Set<String>> =
        progressDao.observeInProgressIds().map { it.toSet() }

    fun observeCompletedCount(): Flow<Int> = progressDao.observeCompletedCount()

    suspend fun clear(puzzleId: String) = progressDao.delete(puzzleId)

    // --- the daily puzzle ------------------------------------------------------------

    /** Notes which puzzle a given date was assigned, so the calendar can show it. */
    suspend fun recordDailyAssignment(date: LocalDate, puzzleId: String) {
        val existing = dailyDao.find(date.toString())
        if (existing != null) return
        dailyDao.upsert(DailyRecordEntity(date = date.toString(), puzzleId = puzzleId))
    }

    /**
     * Marks a daily as finished and moves the streak on.
     *
     * [date] is the day the puzzle belongs to. Finishing it on a later day is a
     * backfill: it counts towards completions and leaves the streak alone (6.3).
     */
    suspend fun completeDaily(date: LocalDate, puzzleId: String): StreakState {
        val today = clock.today()
        val existing = dailyDao.find(date.toString())

        dailyDao.upsert(
            DailyRecordEntity(
                date = date.toString(),
                puzzleId = puzzleId,
                completed = true,
                completedAt = existing?.completedAt ?: nowMillis(),
                completedOnDate = existing?.completedOnDate ?: today.toString(),
            ),
        )

        // Finishing the same day twice must not advance anything.
        if (existing?.completed == true) return loadStats()

        val updated = StreakCalculator.onDailyCompleted(loadStats(), date, today)
        statsDao.upsert(updated.toEntity())
        return updated
    }

    suspend fun dailyRecord(date: LocalDate): DailyRecordEntity? = dailyDao.find(date.toString())

    /** A month of daily records, keyed by date, for the calendar view (6.2). */
    fun observeMonth(month: YearMonth): Flow<Map<LocalDate, DailyRecordEntity>> =
        dailyDao.observeMonth(month.toString()).map { rows ->
            rows.associateBy { LocalDate.parse(it.date) }
        }

    // --- streak and stats ------------------------------------------------------------

    suspend fun loadStats(): StreakState = (statsDao.find() ?: UserStatsEntity()).toStreakState()

    fun observeStats(): Flow<StreakState> =
        statsDao.observe().map { (it ?: UserStatsEntity()).toStreakState() }

    /**
     * Rolls the freeze allowance over when the month changes.
     *
     * Called on launch so the allowance is correct even if the player has not completed
     * anything since the month turned.
     */
    suspend fun refreshFreezeAllowance(): StreakState {
        val current = loadStats()
        val refreshed = StreakCalculator.withFreezeAllowance(current, clock.today())
        if (refreshed != current) statsDao.upsert(refreshed.toEntity())
        return refreshed
    }

    private fun nowMillis(): Long = System.currentTimeMillis()
}

// --- mapping ---------------------------------------------------------------------------

internal fun UserStatsEntity.toStreakState() = StreakState(
    currentStreak = currentStreak,
    bestStreak = bestStreak,
    totalCompleted = totalCompleted,
    freezesRemaining = freezesRemaining,
    freezeMonth = freezeMonth?.let(YearMonth::parse),
    lastStreakDate = lastStreakDate?.let(LocalDate::parse),
)

internal fun StreakState.toEntity() = UserStatsEntity(
    id = UserStatsEntity.SINGLETON_ID,
    currentStreak = currentStreak,
    bestStreak = bestStreak,
    totalCompleted = totalCompleted,
    freezesRemaining = freezesRemaining,
    freezeMonth = freezeMonth?.toString(),
    lastStreakDate = lastStreakDate?.toString(),
)
