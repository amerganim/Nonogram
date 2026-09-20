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
