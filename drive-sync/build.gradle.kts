plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":android:core-model"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    // Google Drive API client is wired by the Android/web adapters; the engine
    // itself only needs the DriveStore interface so it stays platform-neutral.
    testImplementation(libs.junit)
}
