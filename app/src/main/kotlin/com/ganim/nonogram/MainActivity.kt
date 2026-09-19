package com.ganim.nonogram

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import com.ganim.nonogram.data.assets.PuzzlePackLoader
import com.ganim.nonogram.data.session.GameSessionStore
import com.ganim.nonogram.game.GameScreen
import com.ganim.nonogram.game.GameViewModel
import com.ganim.nonogram.puzzle.model.Puzzle
import com.ganim.nonogram.ui.theme.NonogramTheme
import java.io.File

/**
 * Single Activity, per build plan section 2.
 *
 * Phase 2 shows the play screen directly. Phase 3 (section 6) puts Compose Navigation
 * here with Daily, Archive and Settings destinations; the size buttons in the toolbar
 * are a stand-in until the archive exists.
 *
 * Wiring is manual constructor injection, as the plan specifies - no Hilt at this size.
 */
class MainActivity : ComponentActivity() {

    private lateinit var packLoader: PuzzlePackLoader
    private lateinit var sessionStore: GameSessionStore
    private var viewModel: GameViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        packLoader = PuzzlePackLoader(assets)
        sessionStore = GameSessionStore(File(filesDir, GameSessionStore.DEFAULT_FILE_NAME))

        // Build plan 5.3: relaunch restores the puzzle in progress exactly. The grid
        // itself is not stored in the session - only its id, resolved against the pack.
        val restored = sessionStore.load { id -> packLoader.findById(id) }

        setContent {
            NonogramTheme {
                var puzzle by remember { mutableStateOf(restored?.puzzle ?: firstPuzzleOfSize(DEFAULT_SIZE)) }

                val model = remember(puzzle.id) {
                    ViewModelProvider(
                        this,
                        GameViewModel.Factory(
                            puzzle = puzzle,
                            sessionStore = sessionStore,
                            restored = restored?.takeIf { it.puzzle.id == puzzle.id },
                        ),
                    )[puzzle.id, GameViewModel::class.java]
                }.also { viewModel = it }

                GameScreen(
                    viewModel = model,
                    onSizeChange = { size -> puzzle = randomPuzzleOfSize(size) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel?.onResume()
    }

    override fun onPause() {
        // Saves immediately, so a process kill straight after backgrounding loses nothing.
        viewModel?.onPause()
        super.onPause()
    }

    private fun firstPuzzleOfSize(size: Int): Puzzle {
        val index = packLoader.indicesMatching(size = size).firstOrNull() ?: 0
        return packLoader.puzzleAt(index)
    }

    /**
     * Picks a puzzle of the requested size.
     *
     * Phase 3 replaces this with the deterministic daily selector (6.2) and the archive
     * browser (6.4); for now it just has to reach all four sizes.
     */
    private fun randomPuzzleOfSize(size: Int): Puzzle {
        val indices = packLoader.indicesMatching(size = size)
        if (indices.isEmpty()) return firstPuzzleOfSize(size)
        return packLoader.puzzleAt(indices.random())
    }

    private companion object {
        const val DEFAULT_SIZE = 10
    }
}
