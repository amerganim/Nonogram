package com.ganim.nonogram.data.repo

import com.ganim.nonogram.data.db.DailyRecordDao
import com.ganim.nonogram.data.db.DailyRecordEntity
import com.ganim.nonogram.data.db.ProgressState
import com.ganim.nonogram.data.db.PuzzleProgressDao
import com.ganim.nonogram.data.db.PuzzleProgressEntity
import com.ganim.nonogram.data.db.UserStatsDao
import com.ganim.nonogram.data.db.UserStatsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory stand-ins for the Room DAOs.
 *
 * The DAOs are interfaces precisely so the repository - where the streak rules actually
 * live - can be tested on the JVM without a device or Robolectric. Room's own SQL is
 * verified at compile time, and the DAO round-trips are covered by the instrumented
 * tests in `androidTest`.
 *
 * Backed by [MutableStateFlow] so `observe*` really emits on change, as the screens
 * depend on.
 */
class FakePuzzleProgressDao : PuzzleProgressDao {
    private val rows = MutableStateFlow<Map<String, PuzzleProgressEntity>>(emptyMap())

    override suspend fun find(puzzleId: String): PuzzleProgressEntity? = rows.value[puzzleId]

    override fun observe(puzzleId: String): Flow<PuzzleProgressEntity?> =
        rows.map { it[puzzleId] }

    override suspend fun upsert(row: PuzzleProgressEntity) {
        rows.value = rows.value + (row.puzzleId to row)
    }

    override fun observeCompletedIds(): Flow<List<String>> =
        rows.map { map -> map.values.filter { it.state == ProgressState.COMPLETED }.map { it.puzzleId } }

    override fun observeInProgressIds(): Flow<List<String>> =
        rows.map { map -> map.values.filter { it.state == ProgressState.IN_PROGRESS }.map { it.puzzleId } }

    override fun observeCompletedCount(): Flow<Int> =
        rows.map { map -> map.values.count { it.state == ProgressState.COMPLETED } }

    override suspend fun mostRecentInProgress(): PuzzleProgressEntity? =
        rows.value.values.filter { it.state == ProgressState.IN_PROGRESS }.maxByOrNull { it.elapsedMs }

    override suspend fun delete(puzzleId: String) {
        rows.value = rows.value - puzzleId
    }

    val all: Collection<PuzzleProgressEntity> get() = rows.value.values
}

class FakeDailyRecordDao : DailyRecordDao {
    private val rows = MutableStateFlow<Map<String, DailyRecordEntity>>(emptyMap())

    override suspend fun find(date: String): DailyRecordEntity? = rows.value[date]

    override suspend fun upsert(row: DailyRecordEntity) {
        rows.value = rows.value + (row.date to row)
    }

    override fun observeMonth(monthPrefix: String): Flow<List<DailyRecordEntity>> =
        rows.map { map -> map.values.filter { it.date.startsWith(monthPrefix) }.sortedBy { it.date } }

    override suspend fun mostRecentCompleted(): DailyRecordEntity? =
        rows.value.values.filter { it.completed }.maxByOrNull { it.date }

    override fun observeCompletedCount(): Flow<Int> =
        rows.map { map -> map.values.count { it.completed } }

    val all: Collection<DailyRecordEntity> get() = rows.value.values
}

class FakeUserStatsDao : UserStatsDao {
    private val row = MutableStateFlow<UserStatsEntity?>(null)

    override suspend fun find(): UserStatsEntity? = row.value

    override fun observe(): Flow<UserStatsEntity?> = row

    override suspend fun upsert(row: UserStatsEntity) {
        this.row.value = row
    }

    override suspend fun insertIfAbsent(row: UserStatsEntity) {
        if (this.row.value == null) this.row.value = row
    }
}
