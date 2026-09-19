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

    private companion object {
        val HAPTICS = booleanPreferencesKey("haptics_enabled")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
    }
}
