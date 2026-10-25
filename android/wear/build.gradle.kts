plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.nobelle.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nobelle.gymhealth"
        minSdk = 30 // Wear OS 4+
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        // Debug package mirrors the phone's .debug suffix so a future Data
        // Layer client pairing targets com.nobelle.gymhealth.debug on both.
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

    testOptions {
        // Robolectric unit tests need merged app+library resources (the
        // bundled exercise catalog JSON lives in :android:core-coaching).
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":android:core-model"))
    // Shared schematic figure engine + the curated catalog (preview set).
    implementation(project(":android:core-coaching"))
    implementation(project(":android:core-media"))

    implementation(platform(libs.compose.bom))
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)
    implementation(libs.wear.health.services)
    implementation(libs.guava)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    testImplementation(libs.junit)
    // Compose preview tests run on the JVM via Robolectric: the on-watch
    // compose-test harness cannot observe the hierarchy in a ScalingLazyColumn
    // world (see WearExercisePreviewTest), and the continuous figure animation
    // never idles. Robolectric gives the same assertions deterministically.
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.activity.compose)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.compose.ui.test.manifest)
}
