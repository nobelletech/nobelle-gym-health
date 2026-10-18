plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.nobelle.core.health"
    compileSdk = 36

    defaultConfig { minSdk = 30 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
}

dependencies {
    api(project(":android:core-model"))
    api(libs.health.connect.client) // public API exposes HealthConnectClient types
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
