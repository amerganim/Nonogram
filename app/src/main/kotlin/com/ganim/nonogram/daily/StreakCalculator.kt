package com.ganim.nonogram.daily

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Streak counters, mirroring the `UserStats` row in build plan 6.5.
 *
 * [lastStreakDate] is the last date whose daily was completed *on that date*. Backfilled
 * days are deliberately not recorded here - see [StreakCalculator].
 */
data class StreakState(
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val totalCompleted: Int = 0,
    val freezesRemaining: Int = StreakCalculator.FREEZES_PER_MONTH,
    val freezeMonth: YearMonth? = null,
    val lastStreakDate: LocalDate? = null,
)

/** Today, in device local time. Injected so streak rules are testable without a device clock. */
fun interface GameClock {
    fun today(): LocalDate

    companion object {
        /** Build plan 6.3: streaks run "in device local time". */
        val System = GameClock { LocalDate.now() }

        /** A clock that can be wound forward, for tests. */
        fun fixed(date: LocalDate) = GameClock { date }
    }
}

/**
 * Streak rules (build plan 6.3).
 *
 * > "Increments on completing the daily puzzle, in device local time. Breaks if a day is
 * > missed. Backfilling a past day does **not** repair a broken streak - it counts for
 * > completion stats only. One streak freeze per month, auto-applied, protecting against
 * > a single missed day."
 *
 * ## Why backfilling cannot repair a streak
 *
 * The archive lets any past day be played with no penalty, because that drives archive
 * engagement. If those plays also rebuilt the streak, the streak would stop measuring
 * anything - a player could vanish for a month and restore a 30-day streak in an
 * afternoon. So a streak advances only when the daily is completed on its own day, and
 * every other completion counts towards [StreakState.totalCompleted] alone.
 *
 * ## The freeze
 *
 * One per calendar month, applied automatically the moment it is needed rather than
 * being a resource to spend. The allowance resets on the first of the month. It covers
 * exactly one missed day: miss two and the streak is gone.
 *
 * Nothing here reads a clock. Today is always a parameter, which is what lets the whole
 * rule set be tested across simulated date changes, as 6.3's acceptance criterion
 * requires ("unit-test the streak logic against a mockable clock; do not rely on manual
 * device date changes").
 */
object StreakCalculator {

    const val FREEZES_PER_MONTH = 1

    /** Resets the freeze allowance when the calendar month turns over. */
    fun withFreezeAllowance(state: StreakState, today: LocalDate): StreakState {
        val month = YearMonth.from(today)
        return if (state.freezeMonth == month) {
            state
        } else {
            state.copy(freezesRemaining = FREEZES_PER_MONTH, freezeMonth = month)
        }
    }

    /**
     * Applies a completed daily puzzle.
     *
     * [completedDate] is the date the puzzle *belongs to*; [today] is when it was
     * actually played. They differ when the player backfills from the calendar.
     */
    fun onDailyCompleted(
        state: StreakState,
        completedDate: LocalDate,
        today: LocalDate,
    ): StreakState {
        val refreshed = withFreezeAllowance(state, today)

        // A backfill counts for stats and nothing else.
        if (completedDate != today) {
            return refreshed.copy(totalCompleted = refreshed.totalCompleted + 1)
        }

        // Replaying a day already finished must not inflate anything.
        if (refreshed.lastStreakDate == today) return refreshed

        val gap = refreshed.lastStreakDate?.let { ChronoUnit.DAYS.between(it, today) }
        var freezesRemaining = refreshed.freezesRemaining

        val nextStreak = when {
            gap == null -> 1
            gap == 1L -> refreshed.currentStreak + 1
            // Exactly one day missed, and a freeze is available: the streak survives.
            gap == 2L && freezesRemaining > 0 -> {
                freezesRemaining--
                refreshed.currentStreak + 1
            }
            else -> 1
        }

        return refreshed.copy(
            currentStreak = nextStreak,
            bestStreak = maxOf(refreshed.bestStreak, nextStreak),
            totalCompleted = refreshed.totalCompleted + 1,
            freezesRemaining = freezesRemaining,
            lastStreakDate = today,
        )
    }

    /**
     * The streak to show right now.
     *
     * Stored counters only change when something is completed, so a streak that has
     * quietly lapsed still sits in the database until the next completion. This is the
     * honest view: it reports zero the moment the streak is actually dead.
     */
    fun displayStreak(state: StreakState, today: LocalDate): Int {
        val last = state.lastStreakDate ?: return 0
        val gap = ChronoUnit.DAYS.between(last, today)
        val freezeAvailable = freezeAvailableOn(state, today)
        return when {
            gap <= 1L -> state.currentStreak
            gap == 2L && freezeAvailable -> state.currentStreak
            else -> 0
        }
    }

    /**
     * True when today's streak is only alive because a freeze is covering a missed day,
     * so the UI can say so instead of silently spending it.
     */
    fun isFreezeProtecting(state: StreakState, today: LocalDate): Boolean {
        val last = state.lastStreakDate ?: return false
        return ChronoUnit.DAYS.between(last, today) == 2L && freezeAvailableOn(state, today)
    }

    /** Whether a freeze can be used today, accounting for the monthly reset. */
    fun freezeAvailableOn(state: StreakState, today: LocalDate): Boolean =
        withFreezeAllowance(state, today).freezesRemaining > 0

    /** True when completing today's daily would extend rather than restart the streak. */
    fun wouldExtendStreak(state: StreakState, today: LocalDate): Boolean {
        val last = state.lastStreakDate ?: return false
        val gap = ChronoUnit.DAYS.between(last, today)
        return gap == 1L || (gap == 2L && freezeAvailableOn(state, today))
    }
}
