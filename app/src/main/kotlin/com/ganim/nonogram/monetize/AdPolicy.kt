package com.ganim.nonogram.monetize

/** Where an interstitial is being considered. Only one of these can ever say yes. */
enum class AdTrigger {
    /** The results card was dismissed. The only moment an interstitial is allowed. */
    RESULTS_DISMISSED,

    /** A puzzle is open. Never, under any circumstances. */
    IN_PUZZLE,

    /** The app just launched. Never. */
    APP_START,

    /** Moving between screens. Not a placement the plan allows. */
    NAVIGATION,
}

/** The answer, and why - so the reason can be logged and asserted rather than inferred. */
enum class InterstitialDecision {
    SHOW,

    /** The player bought `remove_ads`. */
    SUPPRESSED_AD_FREE,

    /** Not a placement the plan permits. */
    SUPPRESSED_WRONG_MOMENT,

    /** Fewer than three puzzles finished since the last one. */
    SUPPRESSED_TOO_FEW_PUZZLES,

    /** Less than three minutes since the last one. */
    SUPPRESSED_TOO_SOON,

    /** Four already shown this session. */
    SUPPRESSED_SESSION_CAP;

    val shouldShow: Boolean get() = this == SHOW
}

/** What the session has seen so far. Resets when the process does - it is session state. */
data class AdSessionState(
    val interstitialsShown: Int = 0,
    val lastInterstitialAtMs: Long? = null,
    val puzzlesCompletedSinceAd: Int = 0,
)

/**
 * When an interstitial may be shown (build plan 8.1, 8.2).
 *
 * The plan's first principle is unambiguous:
 *
 * > "Never interrupt an in-progress puzzle with an ad. Not once."
 *
 * So this is a pure function with an explicit [AdTrigger], not a set of `if` statements
 * scattered through the UI. Every placement has to come through here and name the moment
 * it is asking about, which makes "no ads mid-puzzle" and "no ads on cold start" things
 * the test suite can prove rather than things a reviewer has to go looking for.
 *
 * The caps from 8.2, all enforced here:
 *
 *  - after every 3rd completed puzzle, on results-card dismiss
 *  - at most 4 per session
 *  - at least 3 minutes apart
 *  - none at all for players who bought `remove_ads`
 */
object AdPolicy {

    const val MAX_PER_SESSION = 4
    const val MIN_GAP_MS = 3 * 60 * 1000L
    const val PUZZLES_BETWEEN_ADS = 3

    fun decideInterstitial(
        state: AdSessionState,
        trigger: AdTrigger,
        nowMs: Long,
        adFree: Boolean,
    ): InterstitialDecision {
        if (adFree) return InterstitialDecision.SUPPRESSED_AD_FREE
        if (trigger != AdTrigger.RESULTS_DISMISSED) return InterstitialDecision.SUPPRESSED_WRONG_MOMENT
        if (state.puzzlesCompletedSinceAd < PUZZLES_BETWEEN_ADS) {
            return InterstitialDecision.SUPPRESSED_TOO_FEW_PUZZLES
        }
        if (state.interstitialsShown >= MAX_PER_SESSION) return InterstitialDecision.SUPPRESSED_SESSION_CAP

        val last = state.lastInterstitialAtMs
        if (last != null && nowMs - last < MIN_GAP_MS) return InterstitialDecision.SUPPRESSED_TOO_SOON

        return InterstitialDecision.SHOW
    }

    /** Call after an interstitial was actually displayed, not when one was requested. */
    fun recordShown(state: AdSessionState, nowMs: Long): AdSessionState = state.copy(
        interstitialsShown = state.interstitialsShown + 1,
        lastInterstitialAtMs = nowMs,
        puzzlesCompletedSinceAd = 0,
    )

    fun recordPuzzleCompleted(state: AdSessionState): AdSessionState =
        state.copy(puzzlesCompletedSinceAd = state.puzzlesCompletedSinceAd + 1)
}

/** How a rewarded ad ended. */
enum class RewardOutcome {
    /** Watched through. The reward is earned. */
    COMPLETED,

    /** Closed early. No reward - this is the one case where the player gets nothing. */
    DISMISSED_EARLY,

    /** No ad to show. */
    NO_FILL,

    /** The SDK failed, or the device is offline. */
    FAILED,
}

/**
 * Whether a rewarded ad's reward should be granted (build plan 8.2).
 *
 * > "Handle no-fill and offline gracefully: if no ad is available, grant the hint anyway.
 * > A broken reward loop is worse than a lost impression."
 *
 * So [NO_FILL] and [FAILED] both grant. The player asked for a hint and tapped a button
 * that promised one; whether Google had inventory at that moment is not their problem,
 * and a hint button that sometimes does nothing is the fastest way to lose them.
 *
 * Only [DISMISSED_EARLY] withholds, because there the player chose to back out.
 */
object RewardPolicy {

    fun shouldGrant(outcome: RewardOutcome): Boolean = when (outcome) {
        RewardOutcome.COMPLETED -> true
        RewardOutcome.NO_FILL -> true
        RewardOutcome.FAILED -> true
        RewardOutcome.DISMISSED_EARLY -> false
    }

    /**
     * Rewarded ads stay available to players who removed ads.
     *
     * Build plan 8.2: "Rewarded ads remain available to ad-free purchasers (standard
     * practice, and they *want* the hints)." Removing ads is about interruption, not
     * about taking away a way to earn hints.
     */
    fun isOfferable(adFree: Boolean): Boolean = true
}
