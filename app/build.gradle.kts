plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("com.google.devtools.ksp") version "2.3.12"
    id("com.google.dagger.hilt.android") version "2.60.1"
}

android {
    namespace = "com.example.wajuscanner"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.wajuscanner"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // ID debug kini = applicationId penuh (tanpa suffix), sejak
            // kunci asal CN=WajuScanner tiada di PC ini dan app asal
            // telah di-uninstall dengan kebenaran Walt — build seterusnya
            // sama key debug ini, update in-place aman.
        }
        release {
            optimization {
                enable = false
            }
        }
    }
    // Release signing config dipindahkan ke local — lihat .gradle/release-config-backup.txt.
    // Asal ada password hardcode; dikeluarkan sebelum publish source ke GitHub.
    // buildTypes {
    //     release {
    //         // signingConfig = signingConfigs.getByName("release")
    //         optimization {
    //             enable = false
    //         }
    //     }
    // }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))

    // Compose
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material.icons.extended)

    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)
    implementation("androidx.hilt:hilt-work:1.4.0")

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // ML Kit
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.mlkit.segmentation.selfie)
    implementation(libs.mlkit.face.detection)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}