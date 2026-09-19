package com.ganim.nonogram.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room schema, following build plan 6.5:
 *
 * ```
 * PuzzleProgress(puzzleId, state, elapsedMs, mistakes, completedAt, boardSnapshot)
 * DailyRecord(date, puzzleId, completed, completedAt)
 * UserStats(currentStreak, bestStreak, totalCompleted, freezesRemaining, freezeMonth)
 * ```
 *
 * Only *progress* lives here. Puzzles themselves stay in the bundled pack and are never
 * copied into the database - a row refers to one by id. Duplicating 5,000 grids into
 * SQLite would triple the storage and let the two copies drift apart the first time the
 * pack is regenerated.
 */

/** Whether a puzzle has been started, and how far it got. */
enum class ProgressState { NOT_STARTED, IN_PROGRESS, COMPLETED }

@Entity(
    tableName = "puzzle_progress",
    // The archive filters on completion state (6.4), over 5,000 rows.
    indices = [Index("state")],
)
data class PuzzleProgressEntity(
    @PrimaryKey
    @ColumnInfo(name = "puzzle_id")
    val puzzleId: String,

    val state: ProgressState,

    @ColumnInfo(name = "elapsed_ms")
    val elapsedMs: Long = 0L,

    val mistakes: Int = 0,

    /** Epoch millis, null until finished. */
    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null,

    /**
     * The board in progress, packed by
     * [com.ganim.nonogram.data.session.BoardSnapshotCodec]. Null once completed - a
     * finished puzzle is fully described by its own solution.
     */
    @ColumnInfo(name = "board_snapshot", typeAffinity = ColumnInfo.BLOB)
    val boardSnapshot: ByteArray? = null,
) {
    // Room does not care, but a data class holding a ByteArray compares by reference,
    // which would make two identical rows look different to any test or set.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PuzzleProgressEntity) return false
        return puzzleId == other.puzzleId &&
            state == other.state &&
            elapsedMs == other.elapsedMs &&
            mistakes == other.mistakes &&
            completedAt == other.completedAt &&
            (boardSnapshot?.contentEquals(other.boardSnapshot) ?: (other.boardSnapshot == null))
    }

    override fun hashCode(): Int {
        var result = puzzleId.hashCode()
        result = 31 * result + state.hashCode()
        result = 31 * result + elapsedMs.hashCode()
        result = 31 * result + mistakes
        result = 31 * result + (completedAt?.hashCode() ?: 0)
        result = 31 * result + (boardSnapshot?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * One row per calendar day the daily puzzle was offered.
 *
 * [date] is the ISO local date the puzzle belongs to; [completedAt] is when it was
 * actually finished. When those disagree the day was backfilled from the calendar, which
 * counts for stats but never repairs a streak (6.3) - so the pair of columns is what
 * makes that rule expressible at all.
 */
@Entity(tableName = "daily_record")
data class DailyRecordEntity(
    /** ISO-8601 local date, e.g. `2026-09-19`. Sorts chronologically as text. */
    @PrimaryKey
    val date: String,

    @ColumnInfo(name = "puzzle_id")
    val puzzleId: String,

    val completed: Boolean = false,

    /** Epoch millis of completion, null while unfinished. */
    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null,

    /** Local date the player actually finished it, for telling live play from backfill. */
    @ColumnInfo(name = "completed_on_date")
    val completedOnDate: String? = null,
)

/** Streak and lifetime counters. A single row, pinned to [SINGLETON_ID]. */
@Entity(tableName = "user_stats")
data class UserStatsEntity(
    @PrimaryKey
    val id: Int = SINGLETON_ID,

    @ColumnInfo(name = "current_streak")
    val currentStreak: Int = 0,

    @ColumnInfo(name = "best_streak")
    val bestStreak: Int = 0,

    @ColumnInfo(name = "total_completed")
    val totalCompleted: Int = 0,

    @ColumnInfo(name = "freezes_remaining")
    val freezesRemaining: Int = 1,

    /** `yyyy-MM` the freeze allowance belongs to, null before the first play. */
    @ColumnInfo(name = "freeze_month")
    val freezeMonth: String? = null,

    /** ISO date of the last daily completed *on its own day*. Drives the streak. */
    @ColumnInfo(name = "last_streak_date")
    val lastStreakDate: String? = null,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
