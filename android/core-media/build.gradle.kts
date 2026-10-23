plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.nobelle.core.media"
    compileSdk = 36

    defaultConfig { minSdk = 30 }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
}

dependencies {
    // The canonical motion primitive set lives in the curated core-coaching
    // catalog; the renderer falls back to a neutral pose for anything outside it.
    implementation(project(":android:core-coaching"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    // LocalContentColor for theme-aware figure drawing.
    implementation(libs.compose.material3)

    testImplementation(libs.junit)
}