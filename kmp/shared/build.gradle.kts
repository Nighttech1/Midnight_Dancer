plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.skie)
}

kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
                }
            }
        }
    }

    // iOS framework export tasks (Mac / macos-latest CI only):
    //   :shared:linkDebugFrameworkIosArm64            → device (iphoneos) — Mobile Next Cloud
    //   :shared:linkDebugFrameworkIosSimulatorArm64   → simulator arm64
    //   :shared:linkReleaseFrameworkIosArm64
    // Output: shared/build/bin/<target>/<debug|release>Framework/Shared.framework
    // Packaged by scripts/build-kmp-ios-ipa.sh → dist/MidnightDancer-stage1.ipa
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
            export(project(":core:navigation"))
            export(project(":core:tts"))
            export(project(":feature:splash:logic"))
            export(project(":feature:elements:logic"))
            export(project(":feature:music:logic"))
            export(project(":feature:choreography:logic"))
            export(project(":feature:trainer:logic"))
            export(project(":feature:exchange:logic"))
            export(project(":feature:settings:logic"))
            export(libs.decompose)
            export(libs.essenty.lifecycle)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:domain"))
            api(project(":core:data"))
            api(project(":core:designsystem"))
            api(project(":core:navigation"))
            api(project(":core:tts"))
            api(project(":feature:splash:logic"))
            api(project(":feature:elements:logic"))
            api(project(":feature:music:logic"))
            api(project(":feature:choreography:logic"))
            api(project(":feature:trainer:logic"))
            api(project(":feature:exchange:logic"))
            api(project(":feature:settings:logic"))
            api(libs.decompose)
            api(libs.essenty.lifecycle)
            implementation(libs.essenty.state.keeper)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.koin.test)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
        iosMain.dependencies {
            // Koin common only on iOS for Stage 1.
        }
    }
}

android {
    namespace = "com.midnightdancer.shared"
    compileSdk = 35
    defaultConfig {
        minSdk = 29
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

skie {
    features {
        // Default Flow → AsyncSequence enabled; keep SKIE-friendly public API.
    }
}

// Explicit CI/Mac names used by scripts/build-kmp-ios-ipa.sh and .github/workflows/kmp-ios.yml:
//   :shared:linkDebugFrameworkIosArm64          → device (iphoneos) arm64
//   :shared:linkDebugFrameworkIosSimulatorArm64 → simulator arm64
// Aliases keep runbooks stable if someone greps for "IosDevice".
tasks.register("linkDebugFrameworkIosDevice") {
    group = "build"
    description = "Alias for linkDebugFrameworkIosArm64 (Shared.framework for physical/cloud iPhone)"
    dependsOn("linkDebugFrameworkIosArm64")
}
