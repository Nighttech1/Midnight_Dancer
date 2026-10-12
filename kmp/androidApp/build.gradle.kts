plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.midnightdancer.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.midnightdancer.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.4.1-stage4-polish"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    // Full-only: no productFlavors (lite/standard/english forbidden).
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    debugImplementation(libs.compose.ui.tooling)

    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation(libs.koin.android)
    implementation(libs.decompose)
    // Decompose-Compose: Children / ChildPages / subscribeAsState (same version as catalog)
    implementation("com.arkivanov.decompose:extensions-compose:3.3.0")

    // C-18 Coil wire — Compose AsyncImage + shared buildCoilImageLoader
    implementation(libs.coil)
    implementation(libs.coil.network.okhttp)
    implementation("io.coil-kt.coil3:coil-compose:3.2.0")

    // Boot splash looping video (Media3 ExoPlayer)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
}
