import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import org.gradle.api.tasks.Exec
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// =============================================================================
// SineMiner Android app module.
// Builds the Rust core via cargo-ndk (called from a custom Exec task), then
// links the resulting .so files into the APK via jniLibs.
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
// Rust core build task — cross-compiles via cargo-ndk into jniLibs.
// =============================================================================

val rustTargets = mapOf(
    "arm64-v8a" to "aarch64-linux-android",
    "x86_64" to "x86_64-linux-android",
)

val buildRustCore by tasks.registering(Exec::class) {
    group = "rust"
    description = "Builds the Rust core library for all Android ABIs via cargo-ndk."

    val coreDir = rootProject.layout.projectDirectory.dir("core")
    val jniLibsDir = project.layout.buildDir.dir("intermediates").dir("rust_jniLibs")
    val ndkDir = System.getenv("ANDROID_NDK_HOME")
        ?: System.getenv("ANDROID_NDK_ROOT")
        ?: System.getenv("ANDROID_NDK_PATH")
        ?: providers.exec {
            commandLine("bash", "-c", "echo ${System.getProperty("user.home")}/Android/Sdk/ndk/24.0.8215888")
        }.standardOutput.asText.get().trim()

    val cargoNdk = providers.exec {
        commandLine("bash", "-c", "which cargo-ndk 2>/dev/null || echo cargo-ndk")
    }.standardOutput.asText.get().trim()

    val cargoHome = System.getenv("CARGO_HOME") ?: "${System.getProperty("user.home")}/.cargo"

    // Inputs: all Rust sources
    inputs.dir(coreDir)
    // Outputs: per-ABI .so files
    outputs.dir(jniLibsDir)

    doFirst {
        val cargoNdkExe = if (cargoNdk == "cargo-ndk") "cargo" else cargoNdk
        // cargo-ndk prefixes cargo, so the actual command is `cargo ndk -t <target> -o <dir> -- build`
        // We loop per-ABI because cargo-ndk wants a single target per invocation usually.
        rustTargets.forEach { (abi, target) ->
            val outDir = jniLibsDir.get().dir(abi).asFile
            outDir.mkdirs()
            logger.lifecycle("Building Rust core for $abi ($target)…")
            val cmd = listOfNotNull(
                "cargo", "ndk",
                "-t", target,
                "-o", outDir.absolutePath,
                "--",
                "build",
                "--manifest-path", coreDir.file("Cargo.toml").asFile.absolutePath,
                "--release",
                if (System.getenv("CI") != null) "--profile" else null,
                if (System.getenv("CI") != null) "ci" else null,
            )
            exec {
                commandLine = cmd.filterNotNull()
                workingDir = coreDir.asFile
                environment("ANDROID_NDK_HOME", ndkDir)
                environment("PATH", "$cargoHome/bin:${System.getenv("PATH")}")
                environment("CARGO_HOME", cargoHome)
            }
        }
    }

    commandLine = listOf("bash", "-c", "true") // placeholder; the real work is in doFirst
    isIgnoreExitValue = false
}

android.applicationVariants.configureEach {
    // Merge Rust .so files into the variant's jniLibs
    val variantName = name
    val mergeTask = tasks.register("copyRustCore${variantName.capitalize()}") {
        dependsOn(buildRustCore)
        doFirst {
            val jniLibsDir = project.layout.buildDir.dir("intermediates/rust_jniLibs").get().asFile
            val variantJniDir = project.layout.buildDir.dir("intermediates/merged_packaged_jni_libs/${variantName}").get().asFile
            if (jniLibsDir.exists()) {
                jniLibsDir.walkTopDown().filter { it.isFile && it.extension == "so" }.forEach { soFile ->
                    val abiDir = soFile.parentFile?.name ?: "default"
                    val target = File(variantJniDir, abiDir)
                    target.mkdirs()
                    soFile.copyTo(File(target, soFile.name), overwrite = true)
                    logger.lifecycle("Copied ${soFile.name} for $abiDir")
                }
            }
        }
    }
    // Run before the JNI packaging task
    tasks.matching { it.name.startsWith("merge${variantName.capitalize()}JniLibFolders") }.configureEach {
        dependsOn(mergeTask)
    }
}

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
