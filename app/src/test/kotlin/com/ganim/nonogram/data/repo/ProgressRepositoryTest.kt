package com.ganim.nonogram.data.repo

import com.ganim.nonogram.daily.GameClock
import com.ganim.nonogram.daily.StreakCalculator
import com.ganim.nonogram.data.db.ProgressState
import com.ganim.nonogram.game.GameEngine
import com.ganim.nonogram.game.GameState
import com.ganim.nonogram.game.GameStatus
import com.ganim.nonogram.puzzle.model.CellState
import com.ganim.nonogram.puzzle.model.Puzzle
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Build plan 6.3 and 6.5. The streak rules are exercised here end to end - through the
 * repository, the daily records and the stats row - against a clock the test controls,
 * which is exactly what the acceptance criterion asks for.
 */
class ProgressRepositoryTest {

    private val puzzle = Puzzle.fromRenderedRows(listOf("##.#", ".##.", "#..#", "####"))
    private val jan1 = LocalDate.of(2026, 1, 1)

    private var today = jan1
    private val progressDao = FakePuzzleProgressDao()
    private val dailyDao = FakeDailyRecordDao()
    private val statsDao = FakeUserStatsDao()

    private val repo = ProgressRepository(
        progressDao, dailyDao, statsDao,
        clock = GameClock { today },
    )

    private fun midGame(): GameState {
        var state = GameEngine.tick(GameState.newGame(puzzle), 30_000)
        state = GameEngine.beginGesture(state)
        (12..15).forEach { state = GameEngine.paint(state, it, CellState.FILLED) }
        return GameEngine.endGesture(state)
    }

    private fun solved(): GameState {
        var state = GameState.newGame(puzzle)
        puzzle.solution.forEachIndexed { i, filled -> if (filled) state = GameEngine.tap(state, i) }
        return state
    }

    // --- progress --------------------------------------------------------------------

    @Test
    fun `an unstarted puzzle has no saved board`() = runTest {
        repo.loadInProgress(puzzle).shouldBeNull()
    }

    @Test
    @DisplayName("a saved board comes back with timer, lives and undo intact")
    fun `progress round trips`() = runTest {
        val original = midGame()
        repo.save(original)

        val restored = repo.loadInProgress(puzzle).shouldNotBeNull()
        restored.board shouldBe original.board
        restored.elapsedMs shouldBe original.elapsedMs
        restored.livesRemaining shouldBe original.livesRemaining
        restored.undoStack shouldBe original.undoStack
        restored.canUndo shouldBe true
    }

    @Test
    fun `an in-progress puzzle is recorded as in progress`() = runTest {
        repo.save(midGame())
        val row = progressDao.find(puzzle.id).shouldNotBeNull()
        row.state shouldBe ProgressState.IN_PROGRESS
        row.completedAt.shouldBeNull()
        row.boardSnapshot.shouldNotBeNull()
    }

    @Test
    @DisplayName("a finished puzzle drops its snapshot - the solution already describes it")
    fun `completion clears the snapshot`() = runTest {
        repo.save(solved())
        val row = progressDao.find(puzzle.id).shouldNotBeNull()
        row.state shouldBe ProgressState.COMPLETED
        row.completedAt.shouldNotBeNull()
        row.boardSnapshot.shouldBeNull()
        repo.loadInProgress(puzzle).shouldBeNull()
    }

    @Test
    fun `mistakes are stored as a count, not as remaining lives`() = runTest {
        var state = GameState.newGame(puzzle)
        state = GameEngine.tap(state, 2) // empty in the solution
        repo.save(state)
        progressDao.find(puzzle.id)!!.mistakes shouldBe 1
    }

    @Test
    fun `completed ids are observable for the archive`() = runTest {
        repo.observeCompletedIds().first() shouldBe emptySet()
        repo.save(solved())
        repo.observeCompletedIds().first() shouldBe setOf(puzzle.id)
        repo.observeCompletedCount().first() shouldBe 1
    }

    @Test
    fun `an unfinished puzzle is offered as continue`() = runTest {
        repo.mostRecentInProgressId().shouldBeNull()
        repo.save(midGame())
        repo.mostRecentInProgressId() shouldBe puzzle.id

        repo.save(solved())
        repo.mostRecentInProgressId().shouldBeNull()
    }

    // --- dailies and streaks ---------------------------------------------------------

    @Test
    fun `assigning a daily does not mark it complete`() = runTest {
        repo.recordDailyAssignment(jan1, puzzle.id)
        val record = repo.dailyRecord(jan1).shouldNotBeNull()
        record.completed shouldBe false
        repo.loadStats().currentStreak shouldBe 0
    }

    @Test
    fun `assigning twice does not overwrite a completion`() = runTest {
        repo.completeDaily(jan1, puzzle.id)
        repo.recordDailyAssignment(jan1, puzzle.id)
        repo.dailyRecord(jan1)!!.completed shouldBe true
    }

    @Test
    fun `completing dailies on consecutive days builds a streak`() = runTest {
        repeat(5) { offset ->
            today = jan1.plusDays(offset.toLong())
            repo.completeDaily(today, puzzle.id)
        }
        val stats = repo.loadStats()
        stats.currentStreak shouldBe 5
        stats.bestStreak shouldBe 5
        stats.totalCompleted shouldBe 5
        StreakCalculator.displayStreak(stats, today) shouldBe 5
    }

    @Test
    fun `completing the same daily twice does not advance the streak`() = runTest {
        repo.completeDaily(jan1, puzzle.id)
        repo.completeDaily(jan1, puzzle.id)
        val stats = repo.loadStats()
        stats.currentStreak shouldBe 1
        stats.totalCompleted shouldBe 1
    }

    @Test
    @DisplayName("one missed day is covered by the monthly freeze")
    fun `the freeze survives a single missed day`() = runTest {
        today = jan1
        repo.completeDaily(jan1, puzzle.id)
        today = jan1.plusDays(1)
        repo.completeDaily(today, puzzle.id)

        // Day 3 is missed entirely.
        today = jan1.plusDays(3)
        val stats = repo.completeDaily(today, puzzle.id)
        stats.currentStreak shouldBe 3
        stats.freezesRemaining shouldBe 0
    }

    @Test
    fun `two missed days break the streak`() = runTest {
        today = jan1
        repo.completeDaily(jan1, puzzle.id)
        today = jan1.plusDays(4)
        val stats = repo.completeDaily(today, puzzle.id)
        stats.currentStreak shouldBe 1
        stats.bestStreak shouldBe 1
    }

    @Test
    @DisplayName("backfilling a missed day counts for stats but not for the streak")
    fun `backfilling does not repair the streak`() = runTest {
        today = jan1
        repo.completeDaily(jan1, puzzle.id)

        // Two weeks later, play an old day from the calendar.
        today = jan1.plusDays(14)
        val stats = repo.completeDaily(jan1.plusDays(1), puzzle.id)

        stats.totalCompleted shouldBe 2
        stats.currentStreak shouldBe 1
        StreakCalculator.displayStreak(stats, today) shouldBe 0
    }

    @Test
    fun `a backfilled record remembers it was played late`() = runTest {
        today = jan1.plusDays(10)
        repo.completeDaily(jan1, puzzle.id)
        val record = repo.dailyRecord(jan1).shouldNotBeNull()
        record.completed shouldBe true
        record.date shouldBe jan1.toString()
        record.completedOnDate shouldBe today.toString()
    }

    @Test
    fun `the freeze allowance rolls over when the month changes`() = runTest {
        today = jan1
        repo.completeDaily(jan1, puzzle.id)
        today = jan1.plusDays(1)
        repo.completeDaily(today, puzzle.id)
        today = jan1.plusDays(3)
        repo.completeDaily(today, puzzle.id) // spends the January freeze
        repo.loadStats().freezesRemaining shouldBe 0

        today = LocalDate.of(2026, 2, 1)
        repo.refreshFreezeAllowance().freezesRemaining shouldBe StreakCalculator.FREEZES_PER_MONTH
    }

    @Test
    fun `stats are observable so the daily screen updates itself`() = runTest {
        repo.observeStats().first().currentStreak shouldBe 0
        repo.completeDaily(jan1, puzzle.id)
        repo.observeStats().first().currentStreak shouldBe 1
    }

    @Test
    fun `a month of daily records is keyed by date for the calendar`() = runTest {
        repo.recordDailyAssignment(jan1, puzzle.id)
        repo.completeDaily(jan1.plusDays(1), puzzle.id)
        repo.recordDailyAssignment(LocalDate.of(2026, 2, 5), puzzle.id)

        val january = repo.observeMonth(java.time.YearMonth.of(2026, 1)).first()
        january.keys shouldBe setOf(jan1, jan1.plusDays(1))
        january[jan1]!!.completed shouldBe false
        january[jan1.plusDays(1)]!!.completed shouldBe true
    }

    @Test
    fun `a fresh install reports empty stats rather than failing`() = runTest {
        val stats = repo.loadStats()
        stats.currentStreak shouldBe 0
        stats.bestStreak shouldBe 0
        stats.totalCompleted shouldBe 0
        stats.lastStreakDate.shouldBeNull()
    }

    @Test
    fun `a hundred day streak survives the round trip through storage`() = runTest {
        repeat(100) { offset ->
            today = jan1.plusDays(offset.toLong())
            repo.completeDaily(today, puzzle.id)
        }
        val reloaded = ProgressRepository(progressDao, dailyDao, statsDao, GameClock { today })
        reloaded.loadStats().currentStreak shouldBe 100
        reloaded.loadStats().bestStreak shouldBe 100
    }

    @Test
    fun `a solved game saved then reloaded still reads as complete`() = runTest {
        repo.save(solved())
        progressDao.find(puzzle.id)!!.state shouldBe ProgressState.COMPLETED
        solved().status shouldBe GameStatus.COMPLETE
    }
}
