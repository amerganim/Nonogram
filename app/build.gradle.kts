plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
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
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
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
