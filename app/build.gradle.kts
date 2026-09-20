import java.util.Properties

/**
 * Ad identifiers, per build plan 8.4:
 *
 *   "Ad unit IDs and the AdMob app ID go in local.properties and are injected via
 *    BuildConfig. Never commit real ad unit IDs. Use Google's official test ad unit IDs
 *    in debug builds, wired automatically by build type."
 *
 * The test IDs below are Google's published ones. They are safe to commit precisely
 * because they are not anyone's inventory - they always fill, and they earn nothing.
 */
val googleTestAdMobAppId = "ca-app-pub-3940256099942544~3347511713"
val googleTestInterstitialUnit = "ca-app-pub-3940256099942544/1033173712"
val googleTestRewardedUnit = "ca-app-pub-3940256099942544/5224354917"

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun adProperty(name: String): String? = (localProperties.getProperty(name) ?: providers.gradleProperty(name).orNull)
    ?.trim()
    ?.takeIf { it.isNotEmpty() }

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.ganim.nonogram"
    // Compose 1.12 requires compiling against API 37 or later. targetSdk stays at 36:
    // compileSdk controls which APIs are available, targetSdk opts into new runtime
    // behaviour, and Play currently requires 36 for new uploads.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ganim.nonogram"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            // Test units, always. Serving a real ad to a developer tapping through the
            // same screen fifty times is how AdMob accounts get suspended for invalid
            // traffic - which would end the project, not just the build.
            buildConfigField("String", "ADMOB_APP_ID", "\"$googleTestAdMobAppId\"")
            buildConfigField("String", "AD_UNIT_INTERSTITIAL", "\"$googleTestInterstitialUnit\"")
            buildConfigField("String", "AD_UNIT_REWARDED", "\"$googleTestRewardedUnit\"")
            buildConfigField("boolean", "USES_TEST_ADS", "true")
            manifestPlaceholders["admobAppId"] = googleTestAdMobAppId
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            // Real units come from local.properties, which is gitignored. A release
            // built without them would silently ship test ads and earn nothing, so it
            // fails instead - but only when a release is actually assembled, so a fresh
            // clone can still build and test everything.
            val appId = adProperty("admob.appId")
            val interstitial = adProperty("admob.unit.interstitial")
            val rewarded = adProperty("admob.unit.rewarded")

            buildConfigField("String", "ADMOB_APP_ID", "\"${appId ?: googleTestAdMobAppId}\"")
            buildConfigField("String", "AD_UNIT_INTERSTITIAL", "\"${interstitial ?: googleTestInterstitialUnit}\"")
            buildConfigField("String", "AD_UNIT_REWARDED", "\"${rewarded ?: googleTestRewardedUnit}\"")
            buildConfigField("boolean", "USES_TEST_ADS", "${appId == null}")
            manifestPlaceholders["admobAppId"] = appId ?: googleTestAdMobAppId
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // java.time only exists from API 26. minSdk is 24, and the daily-puzzle and
        // streak logic is written against LocalDate, so it is desugared in.
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

// The exported Room schema is committed to app/schemas. It is what makes a migration
// reviewable, and what lets Room verify that a migration produces the schema it claims -
// which is the real content of the plan's "progress survives an app update" criterion.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.play.services.ads)
    implementation(libs.billing.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Build plan §4.5 / §13: the offline generation tool. Runs the pure-Kotlin generator
// on the JVM and writes the bundled puzzle pack. Never runs on device.
tasks.register<JavaExec>("generatePuzzlePack") {
    group = "nonogram"
    description = "Regenerates the bundled puzzle pack (app/src/main/assets/puzzles.bin)."
    mainClass.set("com.ganim.nonogram.puzzle.tools.GeneratePackKt")

    val compileKotlin = tasks.named("compileDebugKotlin")
    classpath(compileKotlin.map { it.outputs.files }, configurations.named("debugRuntimeClasspath"))
    dependsOn(compileKotlin)

    jvmArgs("-Xmx2g")
    // Tuning flags, e.g. ./gradlew generatePuzzlePack -Pcount=5000 -Pseed=7
    listOf("count", "seed", "out", "threads", "calibrate", "sample").forEach { key ->
        (project.findProperty(key) as String?)?.let { args("--$key=$it") }
    }
}

/**
 * Refuses to package a release that would ship Google's test ad units.
 *
 * Checked at execution time rather than configuration time so that cloning the repo and
 * running the tests works without an AdMob account. Only assembling or bundling a
 * release needs the real identifiers.
 */
val verifyReleaseAdUnits = tasks.register("verifyReleaseAdUnits") {
    group = "verification"
    description = "Fails if a release build would use Google's test ad units."
    val appId = adProperty("admob.appId")
    val interstitial = adProperty("admob.unit.interstitial")
    val rewarded = adProperty("admob.unit.rewarded")
    doLast {
        val missing = buildList {
            if (appId == null) add("admob.appId")
            if (interstitial == null) add("admob.unit.interstitial")
            if (rewarded == null) add("admob.unit.rewarded")
        }
        if (missing.isNotEmpty()) {
            throw GradleException(
                listOf(
                    "Release build is missing real AdMob identifiers: ${missing.joinToString(", ")}",
                    "Add them to local.properties, which is gitignored. See build plan 8.4.",
                    "Without them the release would silently ship Google's test ads and earn nothing.",
                ).joinToString(System.lineSeparator()),
            )
        }
    }
}

tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    dependsOn(verifyReleaseAdUnits)
}
