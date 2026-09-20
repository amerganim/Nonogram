package com.ganim.nonogram.monetize

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Build plan 8.2 acceptance: "Interstitial frequency caps enforced and unit-tested" and
 * "Zero ads observed during any in-progress puzzle."
 */
class MonetizationPolicyTest {

    @Nested
    inner class Interstitials {

        private val ready = AdSessionState(puzzlesCompletedSinceAd = 3)

        @Test
        fun `shows after the third completed puzzle on results dismiss`() {
            AdPolicy.decideInterstitial(ready, AdTrigger.RESULTS_DISMISSED, nowMs = 0, adFree = false) shouldBe
                InterstitialDecision.SHOW
        }

        @Test
        @DisplayName("never during a puzzle - the plan's one absolute rule")
        fun `never mid-puzzle`() {
            // Even with every other condition satisfied and a long-idle session.
            val generous = AdSessionState(
                interstitialsShown = 0,
                lastInterstitialAtMs = null,
                puzzlesCompletedSinceAd = 99,
            )
            AdPolicy.decideInterstitial(generous, AdTrigger.IN_PUZZLE, nowMs = 10_000_000, adFree = false) shouldBe
                InterstitialDecision.SUPPRESSED_WRONG_MOMENT
        }

        @Test
        fun `never on cold start`() {
            AdPolicy.decideInterstitial(ready, AdTrigger.APP_START, nowMs = 10_000_000, adFree = false) shouldBe
                InterstitialDecision.SUPPRESSED_WRONG_MOMENT
        }

        @Test
        fun `never on navigation`() {
            AdPolicy.decideInterstitial(ready, AdTrigger.NAVIGATION, nowMs = 10_000_000, adFree = false) shouldBe
                InterstitialDecision.SUPPRESSED_WRONG_MOMENT
        }

        @Test
        @DisplayName("no interstitial is ever shown at any trigger other than results dismiss")
        fun `only one trigger can ever show`() {
            AdTrigger.entries.forEach { trigger ->
                val decision = AdPolicy.decideInterstitial(ready, trigger, 10_000_000, adFree = false)
                if (trigger == AdTrigger.RESULTS_DISMISSED) {
                    decision shouldBe InterstitialDecision.SHOW
                } else {
                    assertTrue(!decision.shouldShow) { "$trigger was allowed to show an interstitial" }
                }
            }
        }

        @Test
        fun `a paying player never sees one`() {
            AdTrigger.entries.forEach { trigger ->
                val decision = AdPolicy.decideInterstitial(ready, trigger, 10_000_000, adFree = true)
                decision shouldBe InterstitialDecision.SUPPRESSED_AD_FREE
            }
        }

        @Test
        fun `not before three puzzles have been completed`() {
            (0 until AdPolicy.PUZZLES_BETWEEN_ADS).forEach { count ->
                AdPolicy.decideInterstitial(
                    AdSessionState(puzzlesCompletedSinceAd = count),
                    AdTrigger.RESULTS_DISMISSED, 10_000_000, adFree = false,
                ) shouldBe InterstitialDecision.SUPPRESSED_TOO_FEW_PUZZLES
            }
        }

        @Test
        fun `at least three minutes apart`() {
            val justShown = AdSessionState(
                interstitialsShown = 1,
                lastInterstitialAtMs = 1_000_000,
                puzzlesCompletedSinceAd = 3,
            )
            AdPolicy.decideInterstitial(
                justShown, AdTrigger.RESULTS_DISMISSED,
                nowMs = 1_000_000 + AdPolicy.MIN_GAP_MS - 1, adFree = false,
            ) shouldBe InterstitialDecision.SUPPRESSED_TOO_SOON

            AdPolicy.decideInterstitial(
                justShown, AdTrigger.RESULTS_DISMISSED,
                nowMs = 1_000_000 + AdPolicy.MIN_GAP_MS, adFree = false,
            ) shouldBe InterstitialDecision.SHOW
        }

        @Test
        fun `at most four per session`() {
            val capped = AdSessionState(
                interstitialsShown = AdPolicy.MAX_PER_SESSION,
                lastInterstitialAtMs = 0,
                puzzlesCompletedSinceAd = 3,
            )
            AdPolicy.decideInterstitial(
                capped, AdTrigger.RESULTS_DISMISSED, nowMs = 10_000_000, adFree = false,
            ) shouldBe InterstitialDecision.SUPPRESSED_SESSION_CAP
        }

        @Test
        @DisplayName("a long session of solid play still yields at most four interstitials")
        fun `simulated session respects every cap at once`() {
            var state = AdSessionState()
            var now = 0L
            var shown = 0

            // Forty puzzles, five minutes apart - well past every threshold.
            repeat(40) {
                now += 5 * 60 * 1000L
                state = AdPolicy.recordPuzzleCompleted(state)
                val decision = AdPolicy.decideInterstitial(
                    state, AdTrigger.RESULTS_DISMISSED, now, adFree = false,
                )
                if (decision.shouldShow) {
                    shown++
                    state = AdPolicy.recordShown(state, now)
                }
            }

            shown shouldBe AdPolicy.MAX_PER_SESSION
        }

        @Test
        fun `showing one resets the puzzle counter`() {
            val after = AdPolicy.recordShown(AdSessionState(puzzlesCompletedSinceAd = 3), nowMs = 500)
            after.puzzlesCompletedSinceAd shouldBe 0
            after.interstitialsShown shouldBe 1
            after.lastInterstitialAtMs shouldBe 500
        }
    }

    @Nested
    inner class RewardedAds {

        @Test
        fun `watching it through grants the reward`() {
            RewardPolicy.shouldGrant(RewardOutcome.COMPLETED) shouldBe true
        }

        @Test
        fun `backing out early grants nothing`() {
            RewardPolicy.shouldGrant(RewardOutcome.DISMISSED_EARLY) shouldBe false
        }

        @Test
        @DisplayName("no fill and offline both grant anyway - a broken reward loop is worse")
        fun `no fill still grants`() {
            RewardPolicy.shouldGrant(RewardOutcome.NO_FILL) shouldBe true
            RewardPolicy.shouldGrant(RewardOutcome.FAILED) shouldBe true
        }

        @Test
        fun `rewarded ads stay available after removing ads`() {
            RewardPolicy.isOfferable(adFree = true) shouldBe true
            RewardPolicy.isOfferable(adFree = false) shouldBe true
        }

        @Test
        fun `only an early dismissal ever withholds the reward`() {
            RewardOutcome.entries.forEach { outcome ->
                val granted = RewardPolicy.shouldGrant(outcome)
                if (outcome == RewardOutcome.DISMISSED_EARLY) {
                    assertTrue(!granted)
                } else {
                    assertTrue(granted) { "$outcome should have granted the reward" }
                }
            }
        }
    }

    @Nested
    inner class Hints {

        private val today = LocalDate.of(2026, 6, 1)

        @Test
        fun `a fresh install starts with three free hints`() {
            HintEconomy.available(HintWallet(), today) shouldBe HintEconomy.FREE_PER_DAY
        }

        @Test
        fun `spending draws down the free allowance first`() {
            var wallet = HintWallet(purchasedRemaining = 5)
            repeat(HintEconomy.FREE_PER_DAY) {
                val spend = HintEconomy.spend(wallet, today).shouldBeInstanceOf<HintSpend.Spent>()
                spend.fromPurchased shouldBe false
                wallet = spend.wallet
            }
            wallet.freeRemaining shouldBe 0
            wallet.purchasedRemaining shouldBe 5

            val next = HintEconomy.spend(wallet, today).shouldBeInstanceOf<HintSpend.Spent>()
            next.fromPurchased shouldBe true
        }

        @Test
        fun `an empty wallet reports empty so a rewarded ad can be offered`() {
            val empty = HintWallet(freeRemaining = 0, purchasedRemaining = 0, refilledOn = today)
            HintEconomy.spend(empty, today) shouldBe HintSpend.Empty
        }

        @Test
        @DisplayName("free hints refill at local midnight")
        fun `free hints refill daily`() {
            val spent = HintWallet(freeRemaining = 0, refilledOn = today)
            HintEconomy.available(spent, today) shouldBe 0
            HintEconomy.available(spent, today.plusDays(1)) shouldBe HintEconomy.FREE_PER_DAY
        }

        @Test
        @DisplayName("unspent free hints do not pile up while the player is away")
        fun `free hints do not accumulate`() {
            val untouched = HintWallet(freeRemaining = 3, refilledOn = today)
            val afterAMonth = HintEconomy.refreshed(untouched, today.plusDays(30))
            afterAMonth.freeRemaining shouldBe HintEconomy.FREE_PER_DAY
        }

        @Test
        fun `purchased hints survive the daily refill`() {
            val wallet = HintWallet(freeRemaining = 0, purchasedRemaining = 7, refilledOn = today)
            val tomorrow = HintEconomy.refreshed(wallet, today.plusDays(1))
            tomorrow.freeRemaining shouldBe HintEconomy.FREE_PER_DAY
            tomorrow.purchasedRemaining shouldBe 7
        }

        @Test
        fun `a rewarded ad grants one hint`() {
            val wallet = HintEconomy.grantFromAd(HintWallet(freeRemaining = 0, refilledOn = today), today)
            wallet.purchasedRemaining shouldBe HintEconomy.HINTS_PER_REWARDED_AD
        }

        @Test
        fun `a hint pack grants twenty-five`() {
            val wallet = HintEconomy.grantPack(HintWallet(refilledOn = today), today)
            wallet.purchasedRemaining shouldBe HintEconomy.HINT_PACK_SIZE
        }

        @Test
        fun `granting on a new day also refills the free allowance`() {
            val spent = HintWallet(freeRemaining = 0, refilledOn = today)
            val granted = HintEconomy.grantFromAd(spent, today.plusDays(1))
            granted.freeRemaining shouldBe HintEconomy.FREE_PER_DAY
            granted.purchasedRemaining shouldBe 1
        }
    }
}
