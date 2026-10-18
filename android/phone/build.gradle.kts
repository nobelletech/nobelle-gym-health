plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.nobelle.phone"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nobelle.gymhealth"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val androidClientId = providers.gradleProperty("googleAndroidClientId").get()
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${providers.gradleProperty("googleWebClientId").get()}\"")
        buildConfigField("String", "GOOGLE_ANDROID_CLIENT_ID", "\"$androidClientId\"")
        manifestPlaceholders["appAuthRedirectScheme"] = "com.googleusercontent.apps.${androidClientId.removeSuffix(".apps.googleusercontent.com")}"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
}

dependencies {
    implementation(project(":android:core-model"))
    implementation(project(":android:core-coaching"))
    implementation(project(":android:core-health"))
    implementation(project(":android:core-sync"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.app.auth)

    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
}
