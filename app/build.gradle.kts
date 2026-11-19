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
 * Release signing is resolved from (in order of precedence):
 *  1. Environment variables exported by CI after decoding the keystore from
 *     GitHub Secrets (never written into the repository - H26/H27).
 *  2. A local untracked `key.properties` file for trusted maintainers.
 * If neither is present the release build is unsigned and the release
 * pipeline fails its gate instead of publishing a debug-signed artifact (H28).
 */
/**
 * When the release pipeline asks for a signed build it sets ASTRA_REQUIRE_SIGNING=1.
 * In that mode a missing or unreadable keystore is a hard build failure. Without
 * this, `assembleRelease` quietly falls through to an *unsigned* APK, which the
 * release workflow only catches much later at the apksigner gate — or, if that
 * gate were ever loosened, would ship an APK nobody can install over an update.
 */
val requireSigning: Boolean = System.getenv("ASTRA_REQUIRE_SIGNING") == "1"

val releaseSigning: Properties? = run {
    val envStore = System.getenv("ASTRA_KEYSTORE_FILE")
    if (envStore != null) {
        return@run Properties().apply {
            setProperty("storeFile", envStore)
            setProperty("storePassword", System.getenv("ASTRA_KEYSTORE_PASSWORD") ?: "")
            setProperty("keyAlias", System.getenv("ASTRA_KEY_ALIAS") ?: "")
            setProperty("keyPassword", System.getenv("ASTRA_KEY_PASSWORD") ?: "")
        }
    }
    val keyPropsFile = rootProject.file("key.properties")
    if (keyPropsFile.exists()) {
        return@run Properties().apply { keyPropsFile.inputStream().use { load(it) } }
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
    }

    signingConfigs {
        create("release") {
            val props = releaseSigning
            if (props != null) {
                val storePath = props.getProperty("storeFile")
                if (storePath != null && file(storePath).exists()) {
                    storeFile = file(storePath)
                    storePassword = props.getProperty("storePassword")
                    keyAlias = props.getProperty("keyAlias")
                    keyPassword = props.getProperty("keyPassword")
                }
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val signing = signingConfigs.getByName("release")
            if (signing.storeFile != null) signingConfig = signing
            buildConfigField("boolean", "ASTRA_DEBUG_BUILD", "false")
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
val verifyReleaseSigning by tasks.registering {
    group = "verification"
    description = "Fails when ASTRA_REQUIRE_SIGNING=1 but no release signing config resolved."
    doLast {
        if (!requireSigning) return@doLast
        val store = android.signingConfigs.findByName("release")?.storeFile
        if (store == null) {
            throw GradleException(
                "Release signing was required (ASTRA_REQUIRE_SIGNING=1) but no keystore was resolved. " +
                    "Check ASTRA_KEYSTORE_FILE, ASTRA_KEYSTORE_PASSWORD, ASTRA_KEY_ALIAS and ASTRA_KEY_PASSWORD.",
            )
        }
        if (!store.exists()) {
            throw GradleException("Release keystore path resolved but the file does not exist: $store")
        }
    }
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    dependsOn(verifyReleaseSigning)
}
