package com.ganim.nonogram.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** User settings (build plan 2: "Prefs: DataStore Preferences"). */
data class Settings(
    /**
     * Build plan 5.2 calls haptics "a large part of why the app feels better than
     * competitors" and requires it be disableable. On by default.
     */
    val hapticsEnabled: Boolean = true,

    /** Null follows the system, which is what most players want. */
    val darkThemeOverride: Boolean? = null,

    /**
     * Whether the How to play walkthrough has been opened.
     *
     * Set when the screen is shown, not when it is finished. A player who backs out
     * halfway has made a decision, and an app that keeps reopening a tutorial they
     * dismissed is worse than one that trusts them - it stays in Settings either way.
     */
    val tutorialSeen: Boolean = false,
)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Reads and writes [Settings].
 *
 * A read error yields defaults rather than propagating: settings are a convenience, and
 * a corrupt preferences file should mean "haptics are on" rather than a crash loop on
 * launch.
 */
class SettingsRepository(private val context: Context) {

    val settings: Flow<Settings> = context.settingsDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs ->
            Settings(
                hapticsEnabled = prefs[HAPTICS] ?: true,
                darkThemeOverride = prefs[DARK_THEME],
                tutorialSeen = prefs[TUTORIAL_SEEN] ?: false,
            )
        }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[HAPTICS] = enabled }
    }

    suspend fun setDarkThemeOverride(dark: Boolean?) {
        context.settingsDataStore.edit { prefs ->
            if (dark == null) prefs.remove(DARK_THEME) else prefs[DARK_THEME] = dark
        }
    }

    suspend fun setTutorialSeen(seen: Boolean) {
        context.settingsDataStore.edit { it[TUTORIAL_SEEN] = seen }
    }

    private companion object {
        val HAPTICS = booleanPreferencesKey("haptics_enabled")
        val TUTORIAL_SEEN = booleanPreferencesKey("tutorial_seen")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
    }
}
