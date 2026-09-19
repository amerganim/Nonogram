package com.ganim.nonogram.daily

import com.ganim.nonogram.puzzle.model.Difficulty
import java.time.DayOfWeek
import java.time.LocalDate

/** The size and difficulty the daily puzzle takes on a given day. */
data class DailySpec(val size: Int, val difficulty: Difficulty)

/**
 * Which kind of puzzle each weekday gets (build plan 6.2).
 *
 * > "Rotation: Mon/Tue easy 10x10, Wed/Thu medium 15x15, Fri hard 15x15, Sat expert
 * > 20x20, Sun medium 10x10. Tune after playtesting."
 *
 * The shape of the week matters more than the exact assignment: it starts gently, builds
 * through the week, peaks on Saturday when people have time, and eases off on Sunday.
 * A player who can only manage ten minutes on a weeknight is never locked out of their
 * streak by a 20x20.
 *
 * Note that 5x5 never appears. It is in the archive for a quick game, but a daily puzzle
 * that takes forty seconds does not feel like an occasion.
 */
object DailySchedule {

    fun specFor(date: LocalDate): DailySpec = when (date.dayOfWeek) {
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY -> DailySpec(10, Difficulty.EASY)
        DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY -> DailySpec(15, Difficulty.MEDIUM)
        DayOfWeek.FRIDAY -> DailySpec(15, Difficulty.HARD)
        DayOfWeek.SATURDAY -> DailySpec(20, Difficulty.EXPERT)
        DayOfWeek.SUNDAY -> DailySpec(10, Difficulty.MEDIUM)
    }

    /** Every spec the rotation can produce, so the pack can be checked for coverage. */
    val allSpecs: Set<DailySpec> = DayOfWeek.entries
        .map { day -> specFor(LocalDate.of(2024, 1, 1).with(day)) }
        .toSet()
}
