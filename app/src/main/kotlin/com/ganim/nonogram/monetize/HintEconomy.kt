package com.ganim.nonogram.monetize

import java.time.LocalDate

/**
 * The player's hints.
 *
 * Free hints refill daily; purchased ones do not, so the two are counted separately.
 */
data class HintWallet(
    val freeRemaining: Int = HintEconomy.FREE_PER_DAY,
    val purchasedRemaining: Int = 0,
    /** The day [freeRemaining] was last topped up. Null on a fresh install. */
    val refilledOn: LocalDate? = null,
)

/** What happened when a hint was requested. */
sealed class HintSpend {
    /** A hint was taken. */
    data class Spent(val wallet: HintWallet, val fromPurchased: Boolean) : HintSpend()

    /** Nothing left. The caller should offer a rewarded ad (8.2). */
    data object Empty : HintSpend()
}

/**
 * Hint supply and spending (build plan 5.4, 8.3).
 *
 * > "Economy: 3 free hints per day, refilled at local midnight; more via rewarded video."
 *
 * Pure, with the date passed in. The refill happens at *local* midnight, and anything
 * that depends on local midnight has to be testable without changing the device clock.
 */
object HintEconomy {

    const val FREE_PER_DAY = 3

    /** The `hint_pack_25` consumable from 8.3. */
    const val HINT_PACK_SIZE = 25

    /** One rewarded video buys one hint. */
    const val HINTS_PER_REWARDED_AD = 1

    /**
     * Tops the free allowance back up if the day has turned over.
     *
     * Unspent free hints do not accumulate - the allowance is set, not added to.
     * Otherwise a player returning after a month would arrive with ninety hints and the
     * rewarded placement would never fire again.
     */
    fun refreshed(wallet: HintWallet, today: LocalDate): HintWallet =
        if (wallet.refilledOn == today) {
            wallet
        } else {
            wallet.copy(freeRemaining = FREE_PER_DAY, refilledOn = today)
        }

    fun available(wallet: HintWallet, today: LocalDate): Int =
        refreshed(wallet, today).let { it.freeRemaining + it.purchasedRemaining }

    /**
     * Takes one hint, free ones first.
     *
     * Free first because they expire at midnight and purchased ones do not. Spending the
     * paid ones while free ones sit unused would quietly waste something the player paid
     * for.
     */
    fun spend(wallet: HintWallet, today: LocalDate): HintSpend {
        val current = refreshed(wallet, today)
        return when {
            current.freeRemaining > 0 ->
                HintSpend.Spent(current.copy(freeRemaining = current.freeRemaining - 1), fromPurchased = false)

            current.purchasedRemaining > 0 ->
                HintSpend.Spent(current.copy(purchasedRemaining = current.purchasedRemaining - 1), fromPurchased = true)

            else -> HintSpend.Empty
        }
    }

    /** Grants the reward for a watched rewarded ad. */
    fun grantFromAd(wallet: HintWallet, today: LocalDate): HintWallet =
        refreshed(wallet, today).let {
            it.copy(purchasedRemaining = it.purchasedRemaining + HINTS_PER_REWARDED_AD)
        }

    /** Grants a purchased `hint_pack_25`. */
    fun grantPack(wallet: HintWallet, today: LocalDate, count: Int = HINT_PACK_SIZE): HintWallet =
        refreshed(wallet, today).let { it.copy(purchasedRemaining = it.purchasedRemaining + count) }
}
