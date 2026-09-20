package com.ganim.nonogram.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
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
import com.ganim.nonogram.data.repo.PuzzleCollection
import com.ganim.nonogram.data.repo.Settings
import com.ganim.nonogram.game.GameScreen
import com.ganim.nonogram.game.GameViewModel
import com.ganim.nonogram.monetize.AdTrigger
import com.ganim.nonogram.monetize.RewardPolicy
import com.ganim.nonogram.ui.components.GameIcon
import com.ganim.nonogram.ui.components.Glyph
import com.ganim.nonogram.ui.theme.LocalBoardColors
import com.ganim.nonogram.ui.theme.NonogramTheme
import com.ganim.nonogram.ui.tutorial.HowToPlayScreen
import kotlinx.coroutines.launch
import java.time.LocalDate

/** The three top-level destinations from build plan 6.1, plus the play screen. */
private object Routes {
    const val DAILY = "daily"
    const val ARCHIVE = "archive?pictures={pictures}"

    /**
     * The archive, optionally opened on the picture collection.
     *
     * A query argument rather than a second route: the bottom bar compares the current
     * destination against [ARCHIVE], and a separate route would leave the Archive tab
     * looking unselected while the archive was on screen.
     */
    fun archive(pictures: Boolean = false): String = "archive?pictures=$pictures"
    const val SETTINGS = "settings"
    const val HOW_TO_PLAY = "howtoplay"
    const val GAME = "game/{puzzleId}?date={date}"

    fun game(puzzleId: String, date: LocalDate?): String =
        "game/$puzzleId?date=${date?.toString().orEmpty()}"
}

/**
 * A bottom-bar tab.
 *
 * [route] is the pattern the current destination is compared against; [target] is what
 * tapping actually navigates to. They differ for the archive, whose pattern carries an
 * optional argument - navigating to a pattern would send the literal "{pictures}" along
 * as the argument's value.
 */
private data class Destination(
    val route: String,
    val target: String,
    val label: String,
    val glyph: Glyph,
)

private val destinations = listOf(
    Destination(Routes.DAILY, Routes.DAILY, "Daily", Glyph.CALENDAR),
    Destination(Routes.ARCHIVE, Routes.archive(), "Archive", Glyph.GRID),
    Destination(Routes.SETTINGS, Routes.SETTINGS, "Settings", Glyph.SLIDERS),
)

/**
 * The bottom bar.
 *
 * Material's `NavigationBar` indicator is a pale pill that says "selected" quietly. A
 * game says it loudly: the active tab wears the accent outright, which is the same
 * signal the primary button and the active tool use, so the whole app agrees on what
 * "this one" looks like.
 */
@Composable
private fun BottomBar(currentRoute: String?, onSelect: (String) -> Unit) {
    val colors = LocalBoardColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .border(width = 1.dp, color = colors.stroke, shape = RectangleShape)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        destinations.forEach { destination ->
            val selected = currentRoute == destination.route
            Column(
                Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (selected) colors.accent else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(destination.target) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val tint = if (selected) colors.onAccent else colors.textMuted
                GameIcon(destination.glyph, tint, size = 21.dp)
                Spacer(Modifier.height(3.dp))
                Text(
                    destination.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = tint,
                )
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
    val stats by container.progress.observeStats().collectAsState(initial = null)
    val completedIds by container.progress.observeCompletedIds().collectAsState(initial = emptySet())
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
                    BottomBar(currentRoute) { route -> navigateTop(navController, route) }
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
                        onOpenPictures = {
                            navController.navigate(Routes.archive(pictures = true))
                        },
                    )
                }

                composable(Routes.ARCHIVE) { entry ->
                    val model: ArchiveViewModel = viewModel(
                        factory = ArchiveViewModel.Factory(container.puzzles, container.progress),
                    )
                    val openPictures = entry.arguments?.getString("pictures") == "true"
                    LaunchedEffect(openPictures) {
                        if (openPictures) model.setCollection(PuzzleCollection.PICTURE)
                    }
                    ArchiveScreen(
                        viewModel = model,
                        onOpen = { puzzleId -> navController.navigate(Routes.game(puzzleId, null)) },
                    )
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        settings = settings,
                        completedCount = completedCount,
                        totalCount = container.puzzles.count + container.puzzles.pictureCount,
                        dailyCount = stats?.totalCompleted ?: 0,
                        // Twenty-three ids against a set, only while this screen is up.
                        pictureCount = container.puzzles.pictures.count { it.id in completedIds },
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
