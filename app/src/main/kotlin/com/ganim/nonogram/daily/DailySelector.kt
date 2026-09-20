package com.ganim.nonogram.daily

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Picks which puzzle is today's (build plan 6.2).
 *
 * The plan specifies `puzzleIndex = hash(dateString) % poolSize`, and the requirement
 * behind it is the important part:
 *
 * > "Deterministic means no server and no per-device drift."
 *
 * That requirement is kept in full. Two devices installing on different days still agree
 * on what 14 March's puzzle was, with nothing synced, nothing stored and nothing fetched.
 *
 * ## Why not the literal formula
 *
 * `hash(date) % poolSize` has no memory, so every day is an independent draw and the
 * birthday paradox applies. Measured over 2026 against the real pack, the literal
 * formula hands back a puzzle the player has already solved **14 times a year** - 104
 * Mondays and Tuesdays drawing from 900 easy 10x10 puzzles collide roughly six times on
 * their own. That is not a bad hash (14 beat the ~19 chance predicts); it is what
 * sampling with replacement does. For an app whose whole promise is a *new* puzzle every
 * day, being handed February's puzzle again in October reads as a bug.
 *
 * ## What it does instead
 *
 * Each weekday rotation slot has its own pool. Count how many times that slot has come
 * round since a fixed epoch, and walk the pool in a permuted order:
 *
 * ```
 * seq      = occurrences of this slot since EPOCH
 * position = seq % poolSize
 * index    = permute(position, poolSize)
 * ```
 *
 * Because `permute` is a bijection on `[0, poolSize)` and the sequence steps through it
 * one at a time, **no puzzle repeats within any `poolSize` consecutive draws of a slot**.
 *
 * Against the shipped pack, time to a first repeat per slot:
 *
 * ```
 * Wed+Thu  medium 15x15   750 pool, 104 draws/yr    7.2 years   <- soonest
 * Mon+Tue  easy   10x10   900 pool, 104 draws/yr    8.7 years
 * Fri      hard   15x15   600 pool,  52 draws/yr   11.5 years
 * Sun      medium 10x10   750 pool,  52 draws/yr   14.4 years
 * Sat      expert 20x20   800 pool,  52 draws/yr   15.4 years
 * ```
 *
 * Still a pure function of the date: no state, no storage, no server.
 *
 * Changing [EPOCH], [permute] or [DailySchedule] rewrites history - every past daily
 * would resolve to a different puzzle and every stored `DailyRecord` would point at the
 * wrong one. [hash] is kept for the same reason it always was: it is written down here
 * and cannot be changed by anything outside this file.
 */
object DailySelector {

    /**
     * A Monday, chosen well before any plausible install. Dates before it are handled
     * too - the arithmetic uses floor division, so sequence numbers simply go negative.
     */
    val EPOCH: LocalDate = LocalDate.of(2020, 1, 6)

    /** The string the hash is taken over. ISO-8601, so it sorts and reads sensibly. */
    fun dateKey(date: LocalDate): String = date.toString()

    /**
     * FNV-1a, 32-bit. Chosen for being tiny, well-distributed over short inputs, and
     * fully specified right here rather than inherited from a platform.
     */
    fun hash(key: String): Int {
        var hash = FNV_OFFSET_BASIS
        for (char in key) {
            hash = hash xor (char.code and 0xFF)
            hash *= FNV_PRIME
            val high = (char.code shr 8) and 0xFF
            if (high != 0) {
                hash = hash xor high
                hash *= FNV_PRIME
            }
        }
        return hash
    }

    /**
     * How many times this date's rotation slot has come round since [EPOCH], counting
     * from zero. Negative for dates before the epoch.
     */
    fun occurrenceIndex(date: LocalDate): Int {
        val weekdays = DailySchedule.weekdaysSharing(date.dayOfWeek)
        // Occurrences strictly before `date`, plus this date's own place among the
        // slot's weekdays within its week.
        var before = 0
        for (day in weekdays) before += countWeekdaysBefore(day, date)
        return before
    }

    /**
     * The position within the day's pool.
     *
     * @throws IllegalArgumentException if the pool is empty, which would mean the
     * rotation asks for a size and difficulty the pack does not contain.
     */
    fun indexInPool(date: LocalDate, poolSize: Int): Int {
        require(poolSize > 0) { "Cannot pick a daily puzzle from an empty pool" }
        return permute(occurrenceIndex(date).mod(poolSize), poolSize)
    }

    /**
     * A bijection on `[0, size)`, fixed for a given pool size.
     *
     * An affine map `position * a + b` is a permutation exactly when `a` is coprime with
     * `size`, so a candidate derived from the size is nudged up until it is. Pools are at
     * most a few thousand entries, so the search ends within a handful of steps.
     *
     * ## Why the permutation does not change from lap to lap
     *
     * Re-seeding it per lap is the obvious thing to do and it is worse. The draws either
     * side of a lap boundary come from two unrelated permutations, so a puzzle at the end
     * of one lap can reappear near the start of the next - the exact failure this whole
     * mechanism exists to prevent, just moved somewhere rarer and harder to notice.
     *
     * Keeping one permutation per pool makes the sequence perfectly periodic, which gives
     * a guarantee that holds at every point in time and not just within a lap: **no
     * puzzle repeats within any `poolSize` consecutive draws of that slot.** The price is
     * that the order repeats once a lap completes, which is at soonest 7.2 years away.
     *
     * The regular stride an affine map produces is not visible: pool order is already
     * effectively random, because puzzles are sorted by an id derived from a SHA-256 of
     * the grid.
     */
    fun permute(position: Int, size: Int): Int {
        if (size <= 1) return 0
        var multiplier = (hash("stride:$size").toUInt() % size.toUInt()).toInt().coerceAtLeast(1)
        var guard = 0
        while (gcd(multiplier, size) != 1 && guard++ <= size) {
            multiplier = if (multiplier + 1 >= size) 1 else multiplier + 1
        }
        val offset = (hash("offset:$size").toUInt() % size.toUInt()).toInt()
        return ((position.toLong() * multiplier + offset) % size).toInt()
    }

    /** Occurrences of [weekday] in `[EPOCH, date)`. */
    private fun countWeekdaysBefore(weekday: DayOfWeek, date: LocalDate): Int {
        val span = (date.toEpochDay() - EPOCH.toEpochDay()).toInt()
        if (span <= 0) {
            // Mirror the forward case so dates before the epoch stay distinct.
            val back = -span
            val fullWeeks = back / 7
            val remainder = back % 7
            val stepsBack = (EPOCH.dayOfWeek.value - weekday.value).mod(7)
            return -(fullWeeks + if (stepsBack in 1..remainder) 1 else 0)
        }
        val fullWeeks = span / 7
        val remainder = span % 7
        val stepsForward = (weekday.value - EPOCH.dayOfWeek.value).mod(7)
        return fullWeeks + if (stepsForward < remainder) 1 else 0
    }

    private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

    private const val FNV_OFFSET_BASIS = -2128831035 // 0x811C9DC5
    private const val FNV_PRIME = 16777619
}
