plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    kotlin("kapt")
}

android {
    namespace = "com.example.feelvision"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.feelvision"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // Add JNI libs configuration for 16 KB alignment
        jniLibs {
            useLegacyPackaging = false
        }
    }

    // For large model files
    androidResources {
        noCompress += "tflite"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// MediaPipe does not publish a sources JAR.
// This suppresses the Android Studio IDE source-download error
// without affecting the actual build.
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "com.google.mediapipe") {
            // No-op: sources simply don't exist; IDE will skip gracefully
        }
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // MediaPipe LLM Inference
    implementation(libs.litertlm)

    // MediaPipe Tasks Vision (Face Detection + Face Embedding)
    implementation(libs.mediapipe.tasks.vision)

    // Standard LiteRT (for raw mobilefacenet.tflite without metadata)
    implementation(libs.tflite)
    implementation(libs.tflite.support)

    // Hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.android.compiler)
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Coil for image loading
    implementation("io.coil-kt:coil-compose:2.5.0")

    // CameraX
    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // Accompanist Permissions
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")

    // OkHttp for model download
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // ExifInterface
    implementation("androidx.exifinterface:exifinterface:1.4.2")

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.7.5")
    implementation("com.github.mik3y:usb-serial-for-android:3.7.3")
    
    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    // Workaround for Kotlin 2.3.0 metadata mismatch in KAPT

}

kapt {
    correctErrorTypes = true
}