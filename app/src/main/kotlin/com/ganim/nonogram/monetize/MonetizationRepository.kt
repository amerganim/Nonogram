package com.ganim.nonogram.monetize

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ganim.nonogram.daily.GameClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalDate

private val Context.monetizationStore: DataStore<Preferences> by preferencesDataStore(name = "monetization")

/**
 * Persists the hint wallet, and caches whether ads were removed.
 *
 * The cached `adFree` flag is a *convenience*, not the source of truth. Play is, through
 * [BillingManager.refresh] on every launch (build plan 8.3). The cache only exists so
 * that a player who paid does not see an interstitial in the second before the billing
 * client finishes connecting - which would be the single most annoying possible bug for
 * the one player who gave you money.
 */
class MonetizationRepository(
    private val context: Context,
    private val clock: GameClock = GameClock.System,
) {

    val wallet: Flow<HintWallet> = context.monetizationStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs ->
            HintEconomy.refreshed(
                HintWallet(
                    freeRemaining = prefs[FREE_HINTS] ?: HintEconomy.FREE_PER_DAY,
                    purchasedRemaining = prefs[PURCHASED_HINTS] ?: 0,
                    refilledOn = prefs[REFILLED_ON]?.let(LocalDate::parse),
                ),
                clock.today(),
            )
        }

    /** Last known entitlement, for the moment before billing connects. */
    val cachedAdFree: Flow<Boolean> = context.monetizationStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it[AD_FREE] ?: false }

    suspend fun currentWallet(): HintWallet = wallet.first()

    /**
     * Spends one hint.
     *
     * @return true if one was available. False means the caller should offer a rewarded
     * ad rather than silently doing nothing.
     */
    suspend fun spendHint(): Boolean {
        val today = clock.today()
        return when (val result = HintEconomy.spend(currentWallet(), today)) {
            is HintSpend.Empty -> false
            is HintSpend.Spent -> {
                save(result.wallet)
                true
            }
        }
    }

    suspend fun grantHintFromAd() {
        save(HintEconomy.grantFromAd(currentWallet(), clock.today()))
    }

    suspend fun grantHintPack(count: Int = HintEconomy.HINT_PACK_SIZE) {
        save(HintEconomy.grantPack(currentWallet(), clock.today(), count))
    }

    suspend fun cacheAdFree(adFree: Boolean) {
        context.monetizationStore.edit { it[AD_FREE] = adFree }
    }

    private suspend fun save(wallet: HintWallet) {
        context.monetizationStore.edit { prefs ->
            prefs[FREE_HINTS] = wallet.freeRemaining
            prefs[PURCHASED_HINTS] = wallet.purchasedRemaining
            wallet.refilledOn?.let { prefs[REFILLED_ON] = it.toString() }
        }
    }

    private companion object {
        val FREE_HINTS = intPreferencesKey("free_hints")
        val PURCHASED_HINTS = intPreferencesKey("purchased_hints")
        val REFILLED_ON = stringPreferencesKey("hints_refilled_on")
        val AD_FREE = booleanPreferencesKey("ad_free")
    }
}
