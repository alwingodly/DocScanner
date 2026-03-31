plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    kotlin("kapt")
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

    // ── Fix for deprecated aaptOptions syntax ─────────────────────────────────
    androidResources {
        noCompress += "tflite"   // don't compress the model file in APK
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    }
}

dependencies {

    // ── Core ──
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // ── Room ──
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.compose.foundation)
    kapt(libs.androidx.room.compiler)

    // ── CameraX ──
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // ── ML Kit (replaces OpenCV + Tesseract) ──
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:object-detection:17.0.2")
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")

    // ── ExifInterface (for correct image rotation from gallery/scanner) ──
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("io.coil-kt:coil-compose:2.6.0")
    // ── TensorFlow Lite (document classification) ──
    implementation("org.tensorflow:tensorflow-lite:2.17.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.5.0")
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
    implementation("sh.calvin.reorderable:reorderable:2.4.0")

    // ── Hilt DI ──
    implementation(libs.hilt.android)
    kapt(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    // ── Navigation ──
    implementation(libs.androidx.navigation.compose)

    // ── Coil ──
    implementation(libs.coil.compose)

    // ── Accompanist Permissions ──
    implementation(libs.accompanist.permissions)

    // ── Coroutines ──
    implementation(libs.kotlinx.coroutines.android)

    // ── Lifecycle ViewModel ──
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // ── Extended Material Icons ──
    implementation(libs.androidx.compose.material.icons.extended)

    // ── Testing ──
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

kapt {
    correctErrorTypes = true
}