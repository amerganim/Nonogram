package com.ganim.nonogram

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ganim.nonogram.ui.AppContainer
import com.ganim.nonogram.ui.NonogramApp

/**
 * The single Activity (build plan section 2, 6.1).
 *
 * It owns nothing but the dependency graph and the Compose entry point. Navigation and
 * every screen live in [NonogramApp]; the timer and autosave belong to the game's own
 * ViewModel, which the Compose lifecycle already drives.
 */
class MainActivity : ComponentActivity() {

    private lateinit var container: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        container = AppContainer(this)

        setContent {
            NonogramApp(container)
        }
    }
}
