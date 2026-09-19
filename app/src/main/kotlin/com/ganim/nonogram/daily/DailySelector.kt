package com.ganim.nonogram.daily

import java.time.LocalDate

/**
 * Picks which puzzle is today's (build plan 6.2).
 *
 * > "Deterministic selection: `puzzleIndex = hash(dateString) % poolSize`, selecting
 * > from the pool matching the day's size/difficulty. Deterministic means no server and
 * > no per-device drift."
 *
 * Two devices installing the app on different days must still agree on what 14 March's
 * puzzle was, with nothing to sync and nothing to look up.
 *
 * ## Why not String.hashCode()
 *
 * Java specifies `String.hashCode()` exactly, so it would in fact be stable. It is
 * avoided anyway because it is *someone else's* guarantee: if this ever runs on Kotlin
 * Multiplatform, or the date key format changes, the whole archive of past dailies
 * silently points at different puzzles. FNV-1a is eight lines, written down here, and
 * cannot be changed by anything outside this file.
 *
 * Changing [hash] or [dateKey] rewrites history. Every past daily would resolve to a
 * different puzzle and every stored `DailyRecord` would refer to the wrong one.
 */
object DailySelector {

    /** The string the hash is taken over. ISO-8601, so it sorts and reads sensibly. */
    fun dateKey(date: LocalDate): String = date.toString()

    /**
     * FNV-1a, 32-bit. Chosen for being tiny, well-distributed over short strings, and
     * fully specified right here.
     */
    fun hash(key: String): Int {
        var hash = FNV_OFFSET_BASIS
        for (char in key) {
            hash = hash xor (char.code and 0xFF)
            hash *= FNV_PRIME
            // The high byte of a multi-byte character still contributes.
            val high = (char.code shr 8) and 0xFF
            if (high != 0) {
                hash = hash xor high
                hash *= FNV_PRIME
            }
        }
        return hash
    }

    /**
     * The position within the day's pool.
     *
     * `toUInt()` before the modulo, because Kotlin's `%` keeps the sign and a negative
     * hash would produce a negative index.
     */
    fun indexInPool(date: LocalDate, poolSize: Int): Int {
        require(poolSize > 0) { "Cannot pick a daily puzzle from an empty pool" }
        return (hash(dateKey(date)).toUInt() % poolSize.toUInt()).toInt()
    }

    private const val FNV_OFFSET_BASIS = -2128831035 // 0x811C9DC5
    private const val FNV_PRIME = 16777619
}
