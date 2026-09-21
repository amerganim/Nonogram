package com.ganim.nonogram.monetize

import android.app.Activity
import android.content.Context
import com.ganim.nonogram.BuildConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Showing ads, with the policy decisions left to [AdPolicy] and [RewardPolicy].
 *
 * An interface so the rest of the app never sees the SDK, and so a fake can stand in for
 * it. Everything worth testing already lives in the pure policy objects; what is left
 * here is SDK plumbing that only a real device can exercise.
 */
interface AdManager {

    /** Whether a rewarded ad is loaded and ready, so the button can avoid a spinner. */
    val isRewardedReady: Boolean

    fun initialize()

    /** Keeps one rewarded ad warm in the background (build plan 8.2). */
    fun preloadRewarded()

    /** Shows a rewarded ad and reports how it ended. Never throws. */
    suspend fun showRewarded(activity: Activity): RewardOutcome

    /**
     * Shows an interstitial if [AdPolicy] allows one at this moment.
     *
     * @return true if one was actually shown.
     */
    suspend fun maybeShowInterstitial(activity: Activity, trigger: AdTrigger, adFree: Boolean): Boolean

    fun onPuzzleCompleted()
}

/**
 * The real thing, on top of the Google Mobile Ads SDK.
 *
 * ## What this deliberately does not decide
 *
 * Whether an ad *should* appear is [AdPolicy]'s job, and whether a reward is earned is
 * [RewardPolicy]'s. This class only loads, shows and reports. Keeping the judgement out
 * of the SDK layer is what makes "never interrupt an in-progress puzzle" provable in a
 * unit test rather than a property of code that needs a device to run.
 *
 * ## Not yet verified on hardware
 *
 * Every path below needs a real device and a real AdMob account to exercise: fill, no
 * fill, early dismissal, and the reward callback firing before or after dismissal. The
 * *policy* around them is fully tested; this plumbing is not.
 */
class AdMobAdManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) : AdManager {

    private var session = AdSessionState()
    private var rewarded: RewardedAd? = null
    private var interstitial: InterstitialAd? = null
    private var initialized = false

    override val isRewardedReady: Boolean get() = rewarded != null

    override fun initialize() {
        if (initialized) return
        initialized = true
        // Initialisation does disk and network work; off the main thread it goes.
        scope.launch(Dispatchers.IO) {
            MobileAds.initialize(context) {}
            withContext(Dispatchers.Main) {
                preloadRewarded()
                preloadInterstitial()
            }
        }
    }

    override fun preloadRewarded() {
        if (rewarded != null) return
        RewardedAd.load(
            context,
            BuildConfig.AD_UNIT_REWARDED,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewarded = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    // Left null. The hint is granted anyway when there is no ad, so a
                    // failure here costs an impression and nothing else.
                    rewarded = null
                }
            },
        )
    }

    private fun preloadInterstitial() {
        if (interstitial != null) return
        InterstitialAd.load(
            context,
            BuildConfig.AD_UNIT_INTERSTITIAL,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitial = null
                }
            },
        )
    }

    override suspend fun showRewarded(activity: Activity): RewardOutcome {
        val ad = rewarded ?: return RewardOutcome.NO_FILL.also { preloadRewarded() }
        rewarded = null

        val outcome = suspendCancellableCoroutine { continuation ->
            var earned = false

            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    // The reward callback fires before dismissal when it fires at all,
                    // so by here `earned` is settled. Closing early leaves it false.
                    if (continuation.isActive) {
                        continuation.resume(
                            if (earned) RewardOutcome.COMPLETED else RewardOutcome.DISMISSED_EARLY,
                        )
                    }
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    if (continuation.isActive) continuation.resume(RewardOutcome.FAILED)
                }
            }

            ad.show(activity) { earned = true }
        }

        // Start the next one warming immediately, so the button is ready next time.
        preloadRewarded()
        return outcome
    }

    override suspend fun maybeShowInterstitial(
        activity: Activity,
        trigger: AdTrigger,
        adFree: Boolean,
    ): Boolean {
        val decision = AdPolicy.decideInterstitial(session, trigger, clock(), adFree)
        if (!decision.shouldShow) return false

        val ad = interstitial ?: run {
            preloadInterstitial()
            return false
        }
        interstitial = null

        suspendCancellableCoroutine { continuation ->
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
            ad.show(activity)
        }

        session = AdPolicy.recordShown(session, clock())
        preloadInterstitial()
        return true
    }

    override fun onPuzzleCompleted() {
        session = AdPolicy.recordPuzzleCompleted(session)
    }
}
