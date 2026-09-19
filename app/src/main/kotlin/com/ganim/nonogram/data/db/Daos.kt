package com.ganim.nonogram.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Data access for progress, dailies and stats.
 *
 * Queries return [Flow] wherever a screen should react to a change, so completing a
 * puzzle updates the calendar and the archive without either of them polling.
 *
 * Every query here is checked by Room at compile time: a typo in a column name or a
 * mismatched return type fails the build rather than the app.
 */
@Dao
interface PuzzleProgressDao {

    @Query("SELECT * FROM puzzle_progress WHERE puzzle_id = :puzzleId")
    suspend fun find(puzzleId: String): PuzzleProgressEntity?

    @Query("SELECT * FROM puzzle_progress WHERE puzzle_id = :puzzleId")
    fun observe(puzzleId: String): Flow<PuzzleProgressEntity?>

    @Upsert
    suspend fun upsert(row: PuzzleProgressEntity)

    /**
     * Ids of every completed puzzle.
     *
     * The archive needs completion state for 5,000 puzzles while scrolling (6.4).
     * Fetching the id set once and checking membership in memory beats a query per
     * thumbnail by a wide margin - a few thousand short strings is nothing, and the
     * alternative is the jank the acceptance criterion forbids.
     */
    @Query("SELECT puzzle_id FROM puzzle_progress WHERE state = 'COMPLETED'")
    fun observeCompletedIds(): Flow<List<String>>

    @Query("SELECT puzzle_id FROM puzzle_progress WHERE state = 'IN_PROGRESS'")
    fun observeInProgressIds(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM puzzle_progress WHERE state = 'COMPLETED'")
    fun observeCompletedCount(): Flow<Int>

    /** The puzzle to offer as "continue", if any. */
    @Query(
        "SELECT * FROM puzzle_progress WHERE state = 'IN_PROGRESS' " +
            "ORDER BY elapsed_ms DESC LIMIT 1",
    )
    suspend fun mostRecentInProgress(): PuzzleProgressEntity?

    @Query("DELETE FROM puzzle_progress WHERE puzzle_id = :puzzleId")
    suspend fun delete(puzzleId: String)
}

@Dao
interface DailyRecordDao {

    @Query("SELECT * FROM daily_record WHERE date = :date")
    suspend fun find(date: String): DailyRecordEntity?

    @Upsert
    suspend fun upsert(row: DailyRecordEntity)

    /** One calendar month, for the month view in 6.2. */
    @Query("SELECT * FROM daily_record WHERE date LIKE :monthPrefix || '%' ORDER BY date")
    fun observeMonth(monthPrefix: String): Flow<List<DailyRecordEntity>>

    @Query("SELECT * FROM daily_record WHERE completed = 1 ORDER BY date DESC LIMIT 1")
    suspend fun mostRecentCompleted(): DailyRecordEntity?

    @Query("SELECT COUNT(*) FROM daily_record WHERE completed = 1")
    fun observeCompletedCount(): Flow<Int>
}

@Dao
interface UserStatsDao {

    @Query("SELECT * FROM user_stats WHERE id = ${UserStatsEntity.SINGLETON_ID}")
    suspend fun find(): UserStatsEntity?

    @Query("SELECT * FROM user_stats WHERE id = ${UserStatsEntity.SINGLETON_ID}")
    fun observe(): Flow<UserStatsEntity?>

    @Upsert
    suspend fun upsert(row: UserStatsEntity)

    /** Seeds the single row on first launch without clobbering an existing one. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(row: UserStatsEntity)
}
