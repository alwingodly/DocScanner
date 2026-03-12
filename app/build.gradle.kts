plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)       // NEW
    kotlin("kapt")                          // NEW: for Hilt annotation processing
}

android {
    namespace = "com.example.docscanner"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.docscanner"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true                  // Changed: enable for prod
            isShrinkResources = true                // NEW: remove unused resources
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17    // Changed: 11 → 17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"                                // Changed: 11 → 17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {

    // ── Core (existing) ──
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.7.0")
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)
    // ── NEW: CameraX ──
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // ── NEW: OpenCV (edge detection, perspective warp, filters) ──
    implementation(libs.opencv)

    // ── NEW: Hilt DI ──
    implementation(libs.hilt.android)
    kapt(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    // ── NEW: Navigation ──
    implementation(libs.androidx.navigation.compose)

    // ── NEW: Coil (image loading) ──
    implementation(libs.coil.compose)

    // ── NEW: Accompanist Permissions ──
    implementation(libs.accompanist.permissions)

    // ── NEW: Coroutines ──
    implementation(libs.kotlinx.coroutines.android)

    // ── NEW: Lifecycle ViewModel for Compose ──
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // ── NEW: Extended Material Icons ──
    implementation(libs.androidx.compose.material.icons.extended)

    // ── Testing (existing) ──
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Hilt needs this
kapt {
    correctErrorTypes = true
}