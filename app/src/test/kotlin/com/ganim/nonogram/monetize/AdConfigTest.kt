package com.ganim.nonogram.monetize

import com.ganim.nonogram.BuildConfig
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Build plan 8.4 acceptance: "Debug builds use test ad units; release builds use real
 * ones; verified by inspecting `BuildConfig` in both."
 *
 * Unit tests compile against the debug variant, so this proves the debug half directly.
 * The release half is enforced at build time by the `verifyReleaseAdUnits` task, which
 * refuses to assemble or bundle a release whose identifiers are still Google's test
 * ones - a stronger check than a test, because it cannot be skipped.
 *
 * Getting this wrong in the debug direction is not a lost impression, it is an account
 * risk: real ads served to a developer tapping the same screen fifty times is exactly
 * the invalid-traffic pattern AdMob suspends accounts over.
 */
class AdConfigTest {

    @Test
    @DisplayName("debug builds use Google's published test ad units")
    fun `debug uses test units`() {
        assertTrue(BuildConfig.USES_TEST_ADS) { "the debug build is not marked as using test ads" }
        BuildConfig.ADMOB_APP_ID shouldBeGoogleTestId "app ID"
        BuildConfig.AD_UNIT_INTERSTITIAL shouldBeGoogleTestId "interstitial unit"
        BuildConfig.AD_UNIT_REWARDED shouldBeGoogleTestId "rewarded unit"
    }

    @Test
    fun `every ad identifier is populated`() {
        listOf(
            "ADMOB_APP_ID" to BuildConfig.ADMOB_APP_ID,
            "AD_UNIT_INTERSTITIAL" to BuildConfig.AD_UNIT_INTERSTITIAL,
            "AD_UNIT_REWARDED" to BuildConfig.AD_UNIT_REWARDED,
        ).forEach { (name, value) ->
            assertTrue(value.isNotBlank()) { "$name is empty; the SDK would fail at runtime" }
        }
    }

    @Test
    fun `the interstitial and rewarded units are different`() {
        assertTrue(BuildConfig.AD_UNIT_INTERSTITIAL != BuildConfig.AD_UNIT_REWARDED) {
            "both placements point at the same ad unit"
        }
    }

    /** Google's test identifiers all live under this publisher. */
    private infix fun String.shouldBeGoogleTestId(label: String) {
        assertTrue(startsWith(GOOGLE_TEST_PUBLISHER)) {
            "debug $label is '$this', which is not one of Google's test identifiers"
        }
    }

    private companion object {
        const val GOOGLE_TEST_PUBLISHER = "ca-app-pub-3940256099942544"
    }
}
