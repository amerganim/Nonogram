# R8 rules for the release build (build plan section 9).
#
# The plan warns that "R8 breakage that only appears in release builds is a classic
# late-stage disaster", so these go in with the dependencies rather than being discovered
# the week before upload. Phase 6 verifies the minified build actually runs.

# --- Room -------------------------------------------------------------------------------
# Entities are constructed reflectively by generated code, and their field names are the
# column names. Renaming a field silently breaks the mapping.
-keep class com.ganim.nonogram.data.db.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public static <methods>;
}
-dontwarn androidx.room.paging.**

# --- Play Billing -----------------------------------------------------------------------
# The library reflects over its own response models.
-keep class com.android.billingclient.api.** { *; }
-dontwarn com.android.billingclient.**

# --- Google Mobile Ads ------------------------------------------------------------------
# The SDK and its mediation adapters resolve classes by name at runtime.
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.android.gms.internal.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# --- Kotlin / coroutines ----------------------------------------------------------------
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlin.Metadata { public <methods>; }

# --- Keep our own line numbers in crash reports -----------------------------------------
# Without this a Crashlytics stack trace from the field is a list of obfuscated names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
