import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/** Centralized version metadata (Part 23) sourced from gradle.properties. */
fun prop(key: String, fallback: String): String =
    (project.findProperty(key) as String?) ?: fallback

val astraVersionName = prop("astratouch.versionName", "1.0.1")
val astraVersionCode = prop("astratouch.versionCode", "10001").toInt()
val astraApplicationId = prop("astratouch.applicationId", "com.astratouch.app")
val astraMinSdk = prop("astratouch.minSdk", "29").toInt()
val astraCompileSdk = prop("astratouch.compileSdk", "36").toInt()
val astraTargetSdk = prop("astratouch.targetSdk", "36").toInt()

/**
 * Signing strategy: PERSONAL-USE DEBUG SIGNING.
 *
 * AstraTouch is a personal application. It is deliberately signed with a stable
 * *debug* identity rather than a production release key:
 *
 *  - No production keystore, no release key material, no signing secrets for a
 *    store release exist or are required.
 *  - The signing identity must nevertheless be *identical on every build*,
 *    because Android refuses to install an update whose signing certificate
 *    differs from the installed one. A runner-generated `debug.keystore` is
 *    regenerated per CI job, which would break every update.
 *  - `scripts/derive-debug-keystore.py` therefore derives the key deterministically
 *    from a seed and pairs it with the committed *public* certificate
 *    (`signing/astratouch-debug-cert.pem`) so the certificate bytes — the thing
 *    Android actually compares — are stable across runs.
 *
 * The keystore is generated on demand into a temporary directory. No private key
 * is ever committed; `.gitignore` rejects `*.jks`, `*.keystore`, `*.p12`, `*.b64`.
 *
 * Order of precedence for a `debugPersonal`-signed build:
 *  1. ASTRA_DEBUG_KEYSTORE_* environment variables (verified pipeline).
 *  2. A local `key.properties` (untracked) for running the same build locally.
 *  3. Android's automatic debug keystore (plain `assembleDebug` on a dev machine).
 */
val requireSigning: Boolean = System.getenv("ASTRA_REQUIRE_SIGNING") == "1"

data class DebugSigning(val storeFile: java.io.File, val storePassword: String, val keyAlias: String, val keyPassword: String)

val debugSigning: DebugSigning? = run {
    val envStore = System.getenv("ASTRA_DEBUG_KEYSTORE_FILE")
    if (envStore != null) {
        val f = file(envStore)
        if (f.exists()) {
            return@run DebugSigning(
                storeFile = f,
                storePassword = System.getenv("ASTRA_DEBUG_KEYSTORE_PASSWORD") ?: "",
                keyAlias = System.getenv("ASTRA_DEBUG_KEY_ALIAS") ?: "",
                keyPassword = System.getenv("ASTRA_DEBUG_KEY_PASSWORD") ?: "",
            )
        }
    }
    val propsFile = rootProject.file("key.properties")
    if (propsFile.exists()) {
        val props = Properties()
        propsFile.inputStream().use { stream -> props.load(stream) }
        val path = props.getProperty("storeFile")
        if (path != null && file(path).exists()) {
            return@run DebugSigning(
                storeFile = file(path),
                storePassword = props.getProperty("storePassword") ?: "",
                keyAlias = props.getProperty("keyAlias") ?: "",
                keyPassword = props.getProperty("keyPassword") ?: "",
            )
        }
    }
    null
}

android {
    namespace = "com.astratouch.app"
    compileSdk = astraCompileSdk

    defaultConfig {
        applicationId = astraApplicationId
        minSdk = astraMinSdk
        targetSdk = astraTargetSdk
        versionCode = astraVersionCode
        versionName = astraVersionName

        testInstrumentationRunner = "com.astratouch.app.HiltTestRunner"
        vectorDrawables { useSupportLibrary = true }

        buildConfigField("String", "ASTRA_APPLICATION_ID", "\"$astraApplicationId\"")
        buildConfigField("String", "ASTRA_VERSION_NAME", "\"$astraVersionName\"")
        buildConfigField("int", "ASTRA_VERSION_CODE", astraVersionCode.toString())
        buildConfigField("int", "ASTRA_MIN_SDK", astraMinSdk.toString())
        buildConfigField("int", "ASTRA_TARGET_SDK", astraTargetSdk.toString())
        // Declared here so *every* variant has the field; overridden per build type.
        // BuildConfig fields only exist in variants that declare them, and a field
        // referenced from shared source must be present in all of them.
        buildConfigField("boolean", "ASTRA_DEBUG_BUILD", "false")
    }

    signingConfigs {
        // Stable personal-use debug identity. Populated only when a keystore was
        // resolved above; otherwise the `debug` build type falls back to Android's
        // automatic ~/.android/debug.keystore for local development.
        create("debugPersonal") {
            val cfg = debugSigning
            if (cfg != null) {
                storeFile = cfg.storeFile
                storePassword = cfg.storePassword
                keyAlias = cfg.keyAlias
                keyPassword = cfg.keyPassword
                // Also satisfies AGP's check that a debug signing config uses a
                // debug certificate; this one is a purpose-built debug identity.
                isV1SigningEnabled = true
                isV2SigningEnabled = true
            }
        }
    }

    buildTypes {
        /**
         * Development build. Gets Android's automatic debug key. Deliberately NOT
         * used for published artifacts: the `.debug` application-id suffix means an
         * APK built here installs alongside — and can never update — the real app.
         */
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
            isDebuggable = true
        }

        /**
         * The published artifact: minified, non-debuggable, real applicationId, and
         * signed with the stable personal-use debug identity. This is what the
         * release pipeline builds and attaches to a GitHub Release.
         */
        create("debugPersonal") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debugPersonal")
            // Keep the real applicationId so updates replace earlier installs, but
            // mark the artifact as what it is: debug-signed, not a store release.
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            matchingFallbacks += listOf("release")
            buildConfigField("boolean", "ASTRA_DEBUG_BUILD", "true")
        }

        /**
         * Retained for anyone who genuinely wants a store/private release build.
         * Without release-key material this stays unsigned and the Gradle task
         * fails on purpose rather than emitting an unsigned APK.
         */
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            freeCompilerArgs.addAll("-Xjvm-default=all")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
        checkDependencies = false
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "OldTargetApi", "NewerVersionAvailable")
        htmlReport = true
        xmlReport = true
        sarifReport = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "META-INF/*.kotlin_module",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
            )
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.expandProjection", "true")
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    // AndroidX core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Coroutines + Serialization
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Instrumentation tests
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
}

/**
 * Fails the build when signing was requested but no signing config was resolved.
 * Wired as a dependency of `assembleRelease` so it cannot be skipped by invoking
 * Gradle from a different entry point.
 */
val verifyDebugSigning by tasks.registering {
    group = "verification"
    description = "Fails when ASTRA_REQUIRE_SIGNING=1 but no stable debug keystore resolved."
    doLast {
        if (!requireSigning) return@doLast
        val store = android.signingConfigs.findByName("debugPersonal")?.storeFile
        if (store == null) {
            throw GradleException(
                "A signed personal build was required (ASTRA_REQUIRE_SIGNING=1) but no debug keystore was resolved. " +
                    "Check ASTRA_DEBUG_KEYSTORE_FILE, ASTRA_DEBUG_KEYSTORE_PASSWORD, ASTRA_DEBUG_KEY_ALIAS and " +
                    "ASTRA_DEBUG_KEY_PASSWORD, or run scripts/derive-debug-keystore.py first.",
            )
        }
        if (!store.exists()) {
            throw GradleException("Debug keystore path resolved but the file does not exist: $store")
        }
        logger.lifecycle("Signing with stable personal-use debug identity: $store")
    }
}

// The published artifact is `debugPersonal`; gate it so an unsigned APK can never
// be produced when a signed build was requested.
tasks.matching { it.name == "assembleDebugPersonal" }.configureEach {
    dependsOn(verifyDebugSigning)
}
