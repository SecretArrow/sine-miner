import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// =============================================================================
// SineMiner Android app module.
// Rust core is built by CI (build.yml) via cargo-ndk into
// app/src/main/jniLibs/<abi>/, which AGP picks up automatically.
// =============================================================================

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.secretarrow.sineminer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.secretarrow.sineminer"
        minSdk = 29
        targetSdk = 35
        versionCode = (findProperty("app.version.code") as String? ?: "1").toInt()
        versionName = findProperty("app.version.name") as String? ?: "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["disableAnalytics"] = "true"
        vectorDrawables { useSupportLibrary = true }

        // ABI splits — only arm64-v8a for now (most modern Android). x86_64 for emulator.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        // Inject native lib name into BuildConfig
        buildConfigField("String", "CORE_LIB_NAME", "\"sineminer_core\"")
        buildConfigField("String", "CORE_VERSION", "\"0.1.0\"")
    }

    signingConfigs {
        // CI will provide keystore via env vars. Local dev: skip and use debuggable release.
        create("release") {
            val storeFilePath = System.getenv("SINEMINER_KEYSTORE_FILE")
            val storePassword = System.getenv("SINEMINER_KEYSTORE_PASSWORD")
            val keyAlias = System.getenv("SINEMINER_KEY_ALIAS")
            val keyPassword = System.getenv("SINEMINER_KEY_PASSWORD")
            if (storeFilePath != null && storePassword != null && keyAlias != null && keyPassword != null) {
                storeFile = file(storeFilePath)
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // Use a low-memory Rust preset to avoid OOM on emulators.
            buildConfigField("long", "DEFAULT_MEMORY_KIB", "262144") // 256 MiB
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
            // Production uses the full 2 GiB Argon2id memory cost.
            buildConfigField("long", "DEFAULT_MEMORY_KIB", "2097152") // 2 GiB
            // Output filename is predictable for CI upload
            setProperty("archivesBaseName", "SineMiner-${defaultConfig.versionName}")
        }
        create("ci") {
            initWith(getByName("release"))
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = false
            matchingFallbacks += listOf("release")
            // Use medium memory (1 GiB) to keep CI builds from OOMing on small emulators.
            buildConfigField("long", "DEFAULT_MEMORY_KIB", "1048576") // 1 GiB
            // Don't sign — just zipalign
            signingConfig = null
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = false
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.add("-Xcontext-receivers")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/versions/**",
                "META-INF/DEPENDENCIES",
                "/okhttp3/internal/publicsuffix/**.gz",
            )
        }
        // Strip duplicate jniLibs
        jniLibs {
            useLegacyPackaging = false
            pickFirsts += setOf("**/libsineminer_core.so")
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
        managedDevices {
            localDevices {
                create("pixel6Api29") {
                    device = "Pixel 6"
                    apiLevel = 29
                    systemImageSource = "aosp-atd"
                }
            }
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
        warningsAsErrors = false
        disable += setOf("MissingTranslation", "ExtraTranslation")
        baseline = file("lint-baseline.xml")
    }
}

// =============================================================================
// Rust core — built by CI (build.yml) via cargo-ndk into app/src/main/jniLibs/<abi>/
// AGP picks up that directory automatically. No Gradle task here — keeps the
// build script simple and lets the Kotlin DSL avoid Gradle Property API quirks.
// =============================================================================
// To build locally, run: scripts/build-rust.sh
// (Requires Rust + cargo-ndk + Android NDK.)

// =============================================================================
// Dependencies
// =============================================================================

dependencies {
    // AndroidX core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3.window.size)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Security (encrypted prefs for wallet storage)
    implementation(libs.androidx.security.crypto)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.core.testing)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.truth)
    testImplementation(libs.room.testing)

    // Instrumented tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.core.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.mockk.android)
}
