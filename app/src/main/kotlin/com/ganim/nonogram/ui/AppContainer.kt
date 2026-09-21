package com.ganim.nonogram.ui

import android.content.Context
import com.ganim.nonogram.daily.GameClock
import com.ganim.nonogram.data.assets.PuzzlePackLoader
import com.ganim.nonogram.data.db.NonogramDatabase
import com.ganim.nonogram.data.repo.ProgressRepository
import com.ganim.nonogram.data.repo.PuzzleRepository
import com.ganim.nonogram.data.repo.SettingsRepository
import com.ganim.nonogram.monetize.AdManager
import com.ganim.nonogram.monetize.AdMobAdManager
import com.ganim.nonogram.monetize.BillingManager
import com.ganim.nonogram.monetize.MonetizationRepository
import com.ganim.nonogram.monetize.PlayBillingManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The app's dependency graph, assembled by hand.
 *
 * Build plan section 2: "DI: Manual (constructor injection) - Hilt is overkill at this
 * size." This is the whole of it. Everything below is constructed once and passed down
 * by constructor from here.
 */
class AppContainer(context: Context, val clock: GameClock = GameClock.System) {

    private val appContext = context.applicationContext

    /** Outlives any one screen, because billing and ad loads must not die with a Composable. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val database by lazy { NonogramDatabase.get(appContext) }

    val puzzles: PuzzleRepository by lazy {
        PuzzleRepository(
            loader = PuzzlePackLoader(appContext.assets),
            pictureLoader = PuzzlePackLoader(appContext.assets, PuzzlePackLoader.PICTURES_ASSET),
        )
    }

    val progress: ProgressRepository by lazy {
        ProgressRepository(
            progressDao = database.puzzleProgressDao(),
            dailyDao = database.dailyRecordDao(),
            statsDao = database.userStatsDao(),
            clock = clock,
        )
    }

    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }

    val monetization: MonetizationRepository by lazy { MonetizationRepository(appContext, clock) }

    val ads: AdManager by lazy {
        AdMobAdManager(appContext, appScope).also { it.initialize() }
    }

    val billing: BillingManager by lazy {
        PlayBillingManager(
            context = appContext,
            scope = appScope,
            onHintPackPurchased = { count ->
                appScope.launch { monetization.grantHintPack(count) }
            },
        ).also { it.connect() }
    }

    init {
        // Reading the pack builds an index of 5,000 records. Whichever screen touches
        // it first otherwise pays for that on the main thread, which on a Galaxy A15
        // was a visible hitch on the first tab switch. Started here so it is almost
        // always finished before anything asks; `by lazy` is synchronised, so a screen
        // that does ask early waits for this rather than repeating the work.
        appScope.launch(Dispatchers.Default) {
            puzzles.entries
            puzzles.pictures
            // Groups the pools, which the daily calendar asks about 42 times a redraw.
            puzzles.poolFor(com.ganim.nonogram.daily.DailySchedule.specFor(clock.today()))

            // minSdk is 24, so java.time is desugared, and the desugar library loads its
            // time-zone and locale tables on first use. Measured on a Galaxy A15 that is
            // a ~250ms stall, and it landed on whichever screen first showed a date -
            // the first visit to Daily, every install. Touching it here moves the cost
            // to a background thread nobody is watching.
            val today = clock.today()
            val locale = java.util.Locale.getDefault()
            today.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, locale)
            today.month.getDisplayName(java.time.format.TextStyle.FULL, locale)
        }
    }

    private var entitlementMirror: Job? = null

    /**
     * Build plan 8.3: restore purchases on every launch, not just the first.
     *
     * The entitlement mirror is started once and kept, not restarted per call - a new
     * collector on every foreground would stack up and write the same value repeatedly.
     */
    fun onAppForegrounded() {
        billing.refresh()
        if (entitlementMirror == null) {
            entitlementMirror = appScope.launch {
                billing.entitlements.collect { monetization.cacheAdFree(it.adFree) }
            }
        }
    }

    fun onAppDestroyed() {
        entitlementMirror?.cancel()
        entitlementMirror = null
        billing.dispose()
    }
}
