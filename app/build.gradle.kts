import java.util.Properties

plugins {
    id("app.cash.paparazzi") version "1.3.5"
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "app.timecapsule"
    compileSdk = 35
    // CarUxRestrictionsManager (driving restrictions). Provided by the AAOS platform at runtime.
    useLibrary("android.car")

    defaultConfig {
        applicationId = "app.timecapsule.proto"
        // Pleos requires API 28+ (signing key rotation / lineage).
        minSdk = 28
        targetSdk = 35
        testInstrumentationRunner = "app.timecapsule.ForwardingSmokeRunner"
        versionCode = 1
        versionName = "0.1.0-proto"

        // Pleos head units and the Pleos Connect emulator images are 64-bit only. Dropping the
        // 32-bit native libraries keeps the release APK under GitHub's 100 MB file limit.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }

        // Firebase (friends + shared capsules). Keys live in local.properties, never in git:
        //   firebase.apiKey=...  firebase.appId=...  firebase.projectId=...
        // Without them the app runs on an in-memory fake server.
        val local = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        }
        fun cfg(key: String) = "\"" + (local.getProperty(key) ?: "") + "\""
        buildConfigField("String", "FIREBASE_API_KEY", cfg("firebase.apiKey"))
        buildConfigField("String", "FIREBASE_APP_ID", cfg("firebase.appId"))
        buildConfigField("String", "FIREBASE_PROJECT_ID", cfg("firebase.projectId"))
    }

    // Release signing: keystore.properties (git-ignored) points to a keystore kept outside the repo.
    val keys = Properties().apply {
        rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    signingConfigs {
        if (keys.getProperty("storeFile") != null) create("release") {
            storeFile = file(keys.getProperty("storeFile"))
            storePassword = keys.getProperty("storePassword")
            keyAlias = keys.getProperty("keyAlias")
            keyPassword = keys.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.maplibre.android)
    implementation(libs.okhttp)

    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.navihelper)
    implementation(libs.pleos.tts)
    implementation(libs.pleos.stt)

    // Friends and shared capsules. Only Auth (anonymous) + Firestore: both work without
    // Google Play services, which Pleos head units do not have. Audio goes into Firestore
    // too (a 30 s clip is < 1 MB), so the free Spark plan is enough (no Cloud Storage).
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.zxing.core) // friend-code QR

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
