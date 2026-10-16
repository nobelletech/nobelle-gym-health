rootProject.name = "nobelle-gym-health"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

include(":android:phone")
include(":android:wear")
include(":android:core-model")
include(":android:core-data")
include(":android:core-health")
include(":android:core-sync")
include(":android:core-coaching")
include(":drive-sync")
