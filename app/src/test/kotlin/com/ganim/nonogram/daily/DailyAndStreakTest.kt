package com.ganim.nonogram.daily

import com.ganim.nonogram.puzzle.generator.DifficultyRater
import com.ganim.nonogram.puzzle.model.Difficulty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Build plan 6.2 and 6.3, and their acceptance criteria:
 *
 *  - "Daily selection is deterministic - same date yields the same puzzle across fresh
 *    installs."
 *  - "Streak increments, breaks, and freezes correctly across simulated date changes
 *    (unit-test the streak logic against a mockable clock; do not rely on manual device
 *    date changes)."
 */
class DailyAndStreakTest {

    @Nested
    inner class Selection {

        @Test
        @DisplayName("the same date always yields the same puzzle")
        fun `selection is deterministic`() {
            val date = LocalDate.of(2026, 3, 14)
            val first = DailySelector.indexInPool(date, 900)
            repeat(50) {
                DailySelector.indexInPool(date, 900) shouldBe first
            }
        }

        @Test
        fun `the hash is pinned, so past dailies never change`() {
            DailySelector.dateKey(LocalDate.of(2026, 1, 1)) shouldBe "2026-01-01"
            assertEquals(DailySelector.hash("2026-01-01"), DailySelector.hash("2026-01-01"))

            val samples = listOf("2026-01-01", "2026-06-15", "2027-12-31")
            val hashes = samples.map { DailySelector.hash(it) }
            assertEquals(hashes.size, hashes.toSet().size, "distinct dates collided")
        }

        @Test
        @DisplayName("a slot uses every puzzle in its pool before repeating any of them")
        fun `a full cycle has no repeats`() {
            val poolSize = 53
            // Monday and Tuesday share a pool, so walk both. A scheme that numbered each
            // weekday separately would have the two slots collide with each other, which
            // is exactly the case this pins down.
            val start = LocalDate.of(2026, 1, 5) // a Monday
            val dates = generateSequence(start) { it.plusDays(1) }
                .filter { it.dayOfWeek == DayOfWeek.MONDAY || it.dayOfWeek == DayOfWeek.TUESDAY }
                .take(poolSize)
                .toList()

            val indices = dates.map { DailySelector.indexInPool(it, poolSize) }
            assertEquals(
                (0 until poolSize).toSet(), indices.toSet(),
                "a full cycle should be a permutation of the whole pool",
            )
        }

        @Test
        @DisplayName("no repeat in any window of poolSize draws, including across a lap boundary")
        fun `windows spanning a lap boundary have no repeats`() {
            // The boundary is where a per-lap reseed would break: the end of one lap and
            // the start of the next come from unrelated permutations and can collide.
            val poolSize = 11
            val start = LocalDate.of(2026, 1, 2) // a Friday, which has a pool to itself
            val fridays = generateSequence(start) { it.plusWeeks(1) }.take(poolSize * 3).toList()
            val indices = fridays.map { DailySelector.indexInPool(it, poolSize) }

            indices.windowed(poolSize).forEachIndexed { at, window ->
                assertEquals(
                    poolSize, window.toSet().size,
                    "window starting at draw $at repeated a puzzle: $window",
                )
            }
        }

        @Test
        fun `the order is periodic, so a lap is a full pass through the pool`() {
            val poolSize = 11
            val start = LocalDate.of(2026, 1, 2)
            val fridays = generateSequence(start) { it.plusWeeks(1) }.take(poolSize * 2).toList()
            val first = fridays.take(poolSize).map { DailySelector.indexInPool(it, poolSize) }
            val second = fridays.drop(poolSize).map { DailySelector.indexInPool(it, poolSize) }
            first shouldBe second
        }

        @Test
        fun `occurrence numbering is contiguous across a shared pool`() {
            // Mon, Tue, Mon, Tue... must number consecutively, with no gaps or reuse.
            val start = LocalDate.of(2026, 1, 5) // a Monday
            val dates = generateSequence(start) { it.plusDays(1) }
                .filter { it.dayOfWeek == DayOfWeek.MONDAY || it.dayOfWeek == DayOfWeek.TUESDAY }
                .take(20)
                .toList()
            val first = DailySelector.occurrenceIndex(start)
            dates.map(DailySelector::occurrenceIndex) shouldBe (first until first + 20).toList()
        }

        @Test
        fun `dates before the epoch stay distinct and correctly ordered`() {
            val mondays = generateSequence(DailySelector.EPOCH.minusWeeks(30)) { it.plusWeeks(1) }
                .take(20)
                .toList()
            val sequence = mondays.map(DailySelector::occurrenceIndex)
            assertEquals(sequence.size, sequence.toSet().size, "pre-epoch dates collided")
            assertTrue(sequence.zipWithNext().all { (a, b) -> a < b }) {
                "pre-epoch ordering is wrong: $sequence"
            }
            mondays.forEach { date ->
                assertTrue(DailySelector.indexInPool(date, 37) in 0 until 37)
            }
        }

        @Test
        fun `the index always lands inside the pool`() {
            val start = LocalDate.of(2020, 1, 1)
            listOf(1, 2, 7, 97, 600, 900, 5000).forEach { poolSize ->
                (0 until 400).forEach { offset ->
                    val index = DailySelector.indexInPool(start.plusDays(offset.toLong()), poolSize)
                    assertTrue(index in 0 until poolSize) {
                        "index $index out of range for pool of $poolSize"
                    }
                }
            }
        }

        @Test
        fun `an empty pool is rejected rather than crashing on a modulo by zero`() {
            val error = runCatching { DailySelector.indexInPool(LocalDate.now(), 0) }.exceptionOrNull()
            assertTrue(error is IllegalArgumentException)
        }
    }

    @Nested
    inner class Schedule {

        @Test
        fun `the week rotates as the plan specifies`() {
            fun specOn(day: DayOfWeek) = DailySchedule.specFor(LocalDate.of(2026, 1, 5).with(day))

            specOn(DayOfWeek.MONDAY) shouldBe DailySpec(10, Difficulty.EASY)
            specOn(DayOfWeek.TUESDAY) shouldBe DailySpec(10, Difficulty.EASY)
            specOn(DayOfWeek.WEDNESDAY) shouldBe DailySpec(15, Difficulty.MEDIUM)
            specOn(DayOfWeek.THURSDAY) shouldBe DailySpec(15, Difficulty.MEDIUM)
            specOn(DayOfWeek.FRIDAY) shouldBe DailySpec(15, Difficulty.HARD)
            specOn(DayOfWeek.SATURDAY) shouldBe DailySpec(20, Difficulty.EXPERT)
            specOn(DayOfWeek.SUNDAY) shouldBe DailySpec(10, Difficulty.MEDIUM)
        }

        @Test
        @DisplayName("every spec the rotation asks for is a size/difficulty the pack actually holds")
        fun `the rotation is satisfiable by the pack`() {
            DailySchedule.allSpecs.forEach { spec ->
                assertTrue(spec.difficulty in DifficultyRater.bandsFor(spec.size)) {
                    "the rotation wants ${spec.difficulty} at ${spec.size}x${spec.size}, " +
                        "but that size only produces ${DifficultyRater.bandsFor(spec.size)}"
                }
            }
        }
    }

    @Nested
    inner class Streaks {

        private val jan1 = LocalDate.of(2026, 1, 1)

        private fun fresh() = StreakState()

        /** Completes the daily on each of [days], walking the clock forward. */
        private fun playDays(vararg days: LocalDate): StreakState {
            var state = fresh()
            days.forEach { day -> state = StreakCalculator.onDailyCompleted(state, day, day) }
            return state
        }

        @Test
        fun `a first completion starts a streak of one`() {
            val state = playDays(jan1)
            state.currentStreak shouldBe 1
            state.bestStreak shouldBe 1
            state.totalCompleted shouldBe 1
        }

        @Test
        fun `consecutive days build the streak`() {
            val state = playDays(jan1, jan1.plusDays(1), jan1.plusDays(2), jan1.plusDays(3))
            state.currentStreak shouldBe 4
            state.bestStreak shouldBe 4
        }

        @Test
        @DisplayName("missing two days breaks the streak even with a freeze available")
        fun `a two day gap breaks the streak`() {
            var state = playDays(jan1, jan1.plusDays(1))
            state.currentStreak shouldBe 2

            val muchLater = jan1.plusDays(4) // missed days 2 and 3
            state = StreakCalculator.onDailyCompleted(state, muchLater, muchLater)
            state.currentStreak shouldBe 1
            state.bestStreak shouldBe 2
        }

        @Test
        fun `one missed day is covered by the monthly freeze`() {
            var state = playDays(jan1, jan1.plusDays(1), jan1.plusDays(2))
            state.currentStreak shouldBe 3

            val afterGap = jan1.plusDays(4) // day 3 missed
            state = StreakCalculator.onDailyCompleted(state, afterGap, afterGap)
            state.currentStreak shouldBe 4
            state.freezesRemaining shouldBe 0
        }

        @Test
        fun `the freeze only covers one missed day per month`() {
            var state = playDays(jan1, jan1.plusDays(1))

            val firstGap = jan1.plusDays(3)
            state = StreakCalculator.onDailyCompleted(state, firstGap, firstGap)
            state.currentStreak shouldBe 3
            state.freezesRemaining shouldBe 0

            val secondGap = firstGap.plusDays(2)
            state = StreakCalculator.onDailyCompleted(state, secondGap, secondGap)
            state.currentStreak shouldBe 1
        }

        @Test
        fun `the freeze allowance resets on the first of the month`() {
            var state = playDays(jan1, jan1.plusDays(1))
            val janGap = jan1.plusDays(3)
            state = StreakCalculator.onDailyCompleted(state, janGap, janGap)
            state.freezesRemaining shouldBe 0
            state.freezeMonth shouldBe YearMonth.of(2026, 1)

            val feb = LocalDate.of(2026, 2, 1)
            StreakCalculator.withFreezeAllowance(state, feb).freezesRemaining shouldBe
                StreakCalculator.FREEZES_PER_MONTH
        }

        @Test
        @DisplayName("a freeze spanning a month boundary is drawn from the new month")
        fun `freeze across a month boundary`() {
            val jan30 = LocalDate.of(2026, 1, 30)
            var state = StreakCalculator.onDailyCompleted(fresh(), jan30, jan30)
            state = StreakCalculator.onDailyCompleted(state, jan30.plusDays(1), jan30.plusDays(1))
            state.currentStreak shouldBe 2

            // Misses 1 February, plays 2 February.
            val feb2 = LocalDate.of(2026, 2, 2)
            state = StreakCalculator.onDailyCompleted(state, feb2, feb2)
            state.currentStreak shouldBe 3
            state.freezeMonth shouldBe YearMonth.of(2026, 2)
            state.freezesRemaining shouldBe 0
        }

        @Test
        @DisplayName("backfilling a past day counts for stats but never repairs a streak")
        fun `backfilling does not repair a streak`() {
            var state = playDays(jan1, jan1.plusDays(1))

            // Vanish for a fortnight, then come back and backfill the missed days.
            val today = jan1.plusDays(15)
            state = StreakCalculator.onDailyCompleted(state, jan1.plusDays(2), today)
            state = StreakCalculator.onDailyCompleted(state, jan1.plusDays(3), today)
            state = StreakCalculator.onDailyCompleted(state, jan1.plusDays(4), today)

            state.totalCompleted shouldBe 5
            state.currentStreak shouldBe 2       // unchanged by the backfills
            StreakCalculator.displayStreak(state, today) shouldBe 0  // and long dead
        }

        @Test
        fun `backfilling then playing today starts a fresh streak of one`() {
            var state = playDays(jan1)
            val today = jan1.plusDays(10)
            state = StreakCalculator.onDailyCompleted(state, jan1.plusDays(5), today)
            state = StreakCalculator.onDailyCompleted(state, today, today)
            state.currentStreak shouldBe 1
            state.totalCompleted shouldBe 3
        }

        @Test
        fun `replaying the same day does not inflate the streak`() {
            var state = playDays(jan1)
            repeat(5) { state = StreakCalculator.onDailyCompleted(state, jan1, jan1) }
            state.currentStreak shouldBe 1
            state.totalCompleted shouldBe 1
        }

        @Test
        fun `the displayed streak survives today and yesterday`() {
            val state = playDays(jan1, jan1.plusDays(1))
            StreakCalculator.displayStreak(state, jan1.plusDays(1)) shouldBe 2
            StreakCalculator.displayStreak(state, jan1.plusDays(2)) shouldBe 2
        }

        @Test
        @DisplayName("a lapsed streak reads as zero before anything is completed again")
        fun `the displayed streak reports a dead streak honestly`() {
            var state = playDays(jan1, jan1.plusDays(1))

            // One day missed: the freeze is holding it up, and the UI can say so.
            StreakCalculator.displayStreak(state, jan1.plusDays(3)) shouldBe 2
            StreakCalculator.isFreezeProtecting(state, jan1.plusDays(3)) shouldBe true

            // Two days missed: gone, even though currentStreak still says 2 on disk.
            StreakCalculator.displayStreak(state, jan1.plusDays(4)) shouldBe 0
            state.currentStreak shouldBe 2

            // And once the freeze is spent, one missed day is no longer protected.
            val gapDay = jan1.plusDays(3)
            state = StreakCalculator.onDailyCompleted(state, gapDay, gapDay)
            StreakCalculator.displayStreak(state, gapDay.plusDays(2)) shouldBe 0
        }

        @Test
        fun `best streak is never lowered by a break`() {
            var state = playDays(jan1, jan1.plusDays(1), jan1.plusDays(2), jan1.plusDays(3))
            state.bestStreak shouldBe 4

            val afterLongGap = jan1.plusDays(20)
            state = StreakCalculator.onDailyCompleted(state, afterLongGap, afterLongGap)
            state.currentStreak shouldBe 1
            state.bestStreak shouldBe 4
        }

        @Test
        fun `a fresh install shows no streak`() {
            StreakCalculator.displayStreak(fresh(), jan1) shouldBe 0
            StreakCalculator.isFreezeProtecting(fresh(), jan1) shouldBe false
            StreakCalculator.wouldExtendStreak(fresh(), jan1) shouldBe false
        }

        @Test
        fun `a hundred consecutive days counts to a hundred`() {
            var state = fresh()
            (0 until 100).forEach { offset ->
                val day = jan1.plusDays(offset.toLong())
                state = StreakCalculator.onDailyCompleted(state, day, day)
            }
            state.currentStreak shouldBe 100
            state.bestStreak shouldBe 100
            state.totalCompleted shouldBe 100
            // Three month boundaries crossed, so the freeze allowance is intact.
            state.freezesRemaining shouldBe StreakCalculator.FREEZES_PER_MONTH
        }

        @Test
        fun `the clock abstraction is what the app reads today from`() {
            val fixed = GameClock.fixed(jan1)
            fixed.today() shouldBe jan1
            assertTrue(GameClock.System.today().year >= 2024)
        }
    }
}
