package com.ganim.nonogram.ui

import android.content.Context
import com.ganim.nonogram.daily.GameClock
import com.ganim.nonogram.data.assets.PuzzlePackLoader
import com.ganim.nonogram.data.db.NonogramDatabase
import com.ganim.nonogram.data.repo.ProgressRepository
import com.ganim.nonogram.data.repo.PuzzleRepository
import com.ganim.nonogram.data.repo.SettingsRepository

/**
 * The app's dependency graph, assembled by hand.
 *
 * Build plan section 2: "DI: Manual (constructor injection) - Hilt is overkill at this
 * size." This is the whole of it. Everything below is constructed once and passed down
 * by constructor from here.
 */
class AppContainer(context: Context, val clock: GameClock = GameClock.System) {

    private val appContext = context.applicationContext

    private val database by lazy { NonogramDatabase.get(appContext) }

    val puzzles: PuzzleRepository by lazy {
        PuzzleRepository(PuzzlePackLoader(appContext.assets))
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
}
