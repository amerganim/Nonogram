package com.ganim.nonogram.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.ganim.nonogram.ui.theme.LocalBoardColors
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ganim.nonogram.archive.ArchiveScreen
import com.ganim.nonogram.archive.ArchiveViewModel
import com.ganim.nonogram.daily.DailyScreen
import com.ganim.nonogram.daily.DailyViewModel
import com.ganim.nonogram.data.repo.Settings
import com.ganim.nonogram.game.GameScreen
import com.ganim.nonogram.game.GameViewModel
import com.ganim.nonogram.monetize.AdTrigger
import com.ganim.nonogram.monetize.RewardPolicy
import com.ganim.nonogram.ui.theme.NonogramTheme
import com.ganim.nonogram.ui.tutorial.HowToPlayScreen
import kotlinx.coroutines.launch
import java.time.LocalDate

/** The three top-level destinations from build plan 6.1, plus the play screen. */
private object Routes {
    const val DAILY = "daily"
    const val ARCHIVE = "archive"
    const val SETTINGS = "settings"
    const val HOW_TO_PLAY = "howtoplay"
    const val GAME = "game/{puzzleId}?date={date}"

    fun game(puzzleId: String, date: LocalDate?): String =
        "game/$puzzleId?date=${date?.toString().orEmpty()}"
}

/**
 * The nav glyphs are drawn rather than imported.
 *
 * Material's icon artifacts are deprecated out of Material 3, and a stock icon set would
 * pull the bar towards the generic-app look section 7 steers away from. Three small
 * geometric marks - a calendar, a grid of cells, a set of sliders - cost nothing, scale
 * with the theme, and echo the board itself.
 */
private enum class NavGlyph { CALENDAR, GRID, SLIDERS }

private data class Destination(val route: String, val label: String, val glyph: NavGlyph)

private val destinations = listOf(
    Destination(Routes.DAILY, "Daily", NavGlyph.CALENDAR),
    Destination(Routes.ARCHIVE, "Archive", NavGlyph.GRID),
    Destination(Routes.SETTINGS, "Settings", NavGlyph.SLIDERS),
)

@Composable
private fun NavIcon(glyph: NavGlyph, selected: Boolean) {
    val colors = LocalBoardColors.current
    val tint = if (selected) colors.accent else colors.textMuted
    Canvas(Modifier.size(22.dp)) {
        val stroke = size.minDimension * 0.10f
        when (glyph) {
            NavGlyph.CALENDAR -> {
                drawRect(tint, style = Stroke(width = stroke))
                // The header band, as on a wall calendar.
                drawRect(tint, size = Size(size.width, size.height * 0.26f))
            }

            NavGlyph.GRID -> {
                val cell = size.width * 0.42f
                val gap = size.width - cell * 2f
                listOf(0f to 0f, (cell + gap) to 0f, 0f to (cell + gap), (cell + gap) to (cell + gap))
                    .forEachIndexed { index, (x, y) ->
                        // Two filled, two outlined - a half-solved puzzle in miniature.
                        if (index % 3 == 0) {
                            drawRect(tint, Offset(x, y), Size(cell, cell))
                        } else {
                            drawRect(tint, Offset(x, y), Size(cell, cell), style = Stroke(stroke))
                        }
                    }
            }

            NavGlyph.SLIDERS -> {
                val rows = 3
                repeat(rows) { index ->
                    val y = size.height * (index + 0.5f) / rows
                    drawLine(tint, Offset(0f, y), Offset(size.width, y), strokeWidth = stroke)
                    val knobX = size.width * (if (index == 1) 0.72f else 0.32f)
                    drawCircle(tint, radius = stroke * 1.6f, center = Offset(knobX, y))
                }
            }
        }
    }
}

/**
 * Single Activity, Compose Navigation, three top-level destinations (build plan 6.1).
 *
 * The bottom bar hides on the play screen. A nonogram already fills the display at
 * 20x20, and surrendering a strip of it to navigation the player cannot use mid-puzzle
 * would be a poor trade.
 */
@Composable
fun NonogramApp(container: AppContainer) {
    // Nullable until DataStore answers. A non-null default would report "tutorial not
    // seen" for the first frame and open the walkthrough at every launch.
    val loadedSettings by container.settings.settings.collectAsState(initial = null)
    val settings = loadedSettings ?: Settings()
    val completedCount by container.progress.observeCompletedCount().collectAsState(initial = 0)
    val entitlements by container.billing.entitlements.collectAsState()
    val wallet by container.monetization.wallet.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val activity = LocalActivity.current

    NonogramTheme(
        darkTheme = settings.darkThemeOverride
            ?: androidx.compose.foundation.isSystemInDarkTheme(),
    ) {
        val navController = rememberNavController()

        // Shown once, on the first launch that reaches this point. Marked seen on the
        // way in rather than on the way out - see Settings.tutorialSeen.
        var tutorialOffered by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(loadedSettings) {
            val known = loadedSettings ?: return@LaunchedEffect
            if (tutorialOffered) return@LaunchedEffect
            tutorialOffered = true
            if (!known.tutorialSeen) navController.navigate(Routes.HOW_TO_PLAY)
        }

        val backStack by navController.currentBackStackEntryAsState()
        val currentRoute = backStack?.destination?.route
        val showBar = currentRoute in destinations.map { it.route }

        Scaffold(
            bottomBar = {
                if (showBar) {
                    NavigationBar {
                        destinations.forEach { destination ->
                            NavigationBarItem(
                                selected = currentRoute == destination.route,
                                onClick = { navigateTop(navController, destination.route) },
                                icon = {
                                    NavIcon(
                                        glyph = destination.glyph,
                                        selected = currentRoute == destination.route,
                                    )
                                },
                                label = { Text(destination.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Routes.DAILY,
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                composable(Routes.DAILY) {
                    val model: DailyViewModel = viewModel(
                        factory = DailyViewModel.Factory(
                            container.puzzles, container.progress, container.clock,
                        ),
                    )
                    DailyScreen(
                        viewModel = model,
                        onPlay = { puzzleId, date ->
                            navController.navigate(Routes.game(puzzleId, date))
                        },
                    )
                }

                composable(Routes.ARCHIVE) {
                    val model: ArchiveViewModel = viewModel(
                        factory = ArchiveViewModel.Factory(container.puzzles, container.progress),
                    )
                    ArchiveScreen(
                        viewModel = model,
                        onOpen = { puzzleId -> navController.navigate(Routes.game(puzzleId, null)) },
                    )
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        settings = settings,
                        completedCount = completedCount,
                        onHapticsChanged = { enabled ->
                            scope.launch { container.settings.setHapticsEnabled(enabled) }
                        },
                        onThemeChanged = { dark ->
                            scope.launch { container.settings.setDarkThemeOverride(dark) }
                        },
                        onHowToPlay = { navController.navigate(Routes.HOW_TO_PLAY) },
                    )
                }

                composable(Routes.HOW_TO_PLAY) {
                    LaunchedEffect(Unit) { container.settings.setTutorialSeen(true) }
                    HowToPlayScreen(onDone = { navController.popBackStack() })
                }

                composable(Routes.GAME) { entry ->
                    val puzzleId = entry.arguments?.getString("puzzleId")
                    val dateArg = entry.arguments?.getString("date").orEmpty()
                    val dailyDate = dateArg.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
                    val puzzle = remember(puzzleId) { puzzleId?.let(container.puzzles::puzzleById) }

                    if (puzzle == null) {
                        // A puzzle id that is not in the pack means a stale link or a
                        // regenerated pack. Go back rather than showing an empty board.
                        LaunchedEffect(puzzleId) { navController.popBackStack() }
                        return@composable
                    }

                    // Loaded once per puzzle, before the ViewModel is built, so the
                    // board is never briefly blank before the save arrives.
                    var restored by remember(puzzle.id) { mutableStateOf<GameLoad?>(null) }
                    LaunchedEffect(puzzle.id) {
                        restored = GameLoad(container.progress.loadInProgress(puzzle))
                    }

                    val load = restored
                    if (load == null) return@composable

                    val model: GameViewModel = viewModel(
                        key = puzzle.id,
                        factory = GameViewModel.Factory(
                            puzzle = puzzle,
                            progress = container.progress,
                            restored = load.state,
                            onCompleted = {
                                if (dailyDate != null) {
                                    container.progress.completeDaily(dailyDate, puzzle.id)
                                }
                            },
                            monetization = container.monetization,
                        ),
                    )
                    LaunchedEffect(model) { model.onResume() }
                    LaunchedEffect(settings.hapticsEnabled) {
                        model.setHapticsEnabled(settings.hapticsEnabled)
                    }

                    GameScreen(
                        viewModel = model,
                        onExit = { navController.popBackStack() },
                        hintsRemaining = wallet?.let {
                            it.freeRemaining + it.purchasedRemaining
                        } ?: 0,
                        onWatchAdForHint = {
                            val host = activity ?: return@GameScreen false
                            RewardPolicy.shouldGrant(container.ads.showRewarded(host))
                        },
                        onWatchAdForLife = {
                            val host = activity ?: return@GameScreen false
                            RewardPolicy.shouldGrant(container.ads.showRewarded(host))
                        },
                        onResultsDismissed = {
                            container.ads.onPuzzleCompleted()
                            activity?.let { host ->
                                container.ads.maybeShowInterstitial(
                                    activity = host,
                                    trigger = AdTrigger.RESULTS_DISMISSED,
                                    adFree = entitlements.adFree,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

/** Wraps a nullable restored state so "not loaded yet" and "nothing saved" stay distinct. */
private class GameLoad(val state: com.ganim.nonogram.game.GameState?)

private fun navigateTop(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(Routes.DAILY) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
