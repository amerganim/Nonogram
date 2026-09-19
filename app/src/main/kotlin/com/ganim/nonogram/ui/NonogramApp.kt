package com.ganim.nonogram.ui

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
import androidx.compose.runtime.setValue
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
import com.ganim.nonogram.ui.theme.NonogramTheme
import kotlinx.coroutines.launch
import java.time.LocalDate

/** The three top-level destinations from build plan 6.1, plus the play screen. */
private object Routes {
    const val DAILY = "daily"
    const val ARCHIVE = "archive"
    const val SETTINGS = "settings"
    const val GAME = "game/{puzzleId}?date={date}"

    fun game(puzzleId: String, date: LocalDate?): String =
        "game/$puzzleId?date=${date?.toString().orEmpty()}"
}

private data class Destination(val route: String, val label: String)

private val destinations = listOf(
    Destination(Routes.DAILY, "Daily"),
    Destination(Routes.ARCHIVE, "Archive"),
    Destination(Routes.SETTINGS, "Settings"),
)

/**
 * Single Activity, Compose Navigation, three top-level destinations (build plan 6.1).
 *
 * The bottom bar hides on the play screen. A nonogram already fills the display at
 * 20x20, and surrendering a strip of it to navigation the player cannot use mid-puzzle
 * would be a poor trade.
 */
@Composable
fun NonogramApp(container: AppContainer) {
    val settings by container.settings.settings.collectAsState(initial = Settings())
    val completedCount by container.progress.observeCompletedCount().collectAsState(initial = 0)
    val scope = rememberCoroutineScope()

    NonogramTheme(
        darkTheme = settings.darkThemeOverride
            ?: androidx.compose.foundation.isSystemInDarkTheme(),
    ) {
        val navController = rememberNavController()
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
                                icon = {},
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
                    )
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
                        ),
                    )
                    LaunchedEffect(model) { model.onResume() }
                    LaunchedEffect(settings.hapticsEnabled) {
                        model.setHapticsEnabled(settings.hapticsEnabled)
                    }

                    GameScreen(
                        viewModel = model,
                        onExit = { navController.popBackStack() },
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
