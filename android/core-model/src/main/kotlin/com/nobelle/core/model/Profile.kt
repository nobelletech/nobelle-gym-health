package com.nobelle.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val ownerUID: String,
    val displayName: String? = null,
    val goals: Set<Goal> = emptySet(),
    val experience: ExperienceLevel = ExperienceLevel.BEGINNER,
    val equipment: Set<Equipment> = emptySet(),
    val preferredDays: Set<Int> = emptySet(), // 1=Mon..7=Sun
    val sessionMinutes: Int = 45,
    val units: UnitPreferences = UnitPreferences(),
    val limitationsNotes: String? = null, // user-entered only (§4.4)
    val createdUtc: Instant,
    val revision: Long = 1,
)

enum class Goal { GENERAL_FITNESS, STRENGTH, MUSCLE, ENDURANCE, MOBILITY, WEIGHT_MANAGEMENT, CUSTOM }
enum class ExperienceLevel { BEGINNER, INTERMEDIATE, ADVANCED }
enum class Equipment { FULL_GYM, DUMBBELLS, RESISTANCE_BANDS, BODYWEIGHT, CARDIO_MACHINES, HOME_BASIC }

@Serializable
data class UnitPreferences(
    val mass: MassUnit = MassUnit.KG,
    val distance: DistanceUnit = DistanceUnit.KM,
    val glucose: GlucoseUnit = GlucoseUnit.MMOL_L,
) {
    enum class MassUnit { KG, LB }
    enum class DistanceUnit { KM, MI }
    enum class GlucoseUnit { MMOL_L, MG_DL }
}

@Serializable
data class Device(
    val id: String,
    val ownerUID: String,
    val kind: Kind,
    val model: String,
    val osVersion: String,
    val enrolledUtc: Instant,
    val revoked: Boolean = false,
) {
    enum class Kind { PHONE, WATCH, WEB }
}

/**
 * Consent is per-purpose and per-category; grants are independent (§4.7, §11).
 */
@Serializable
data class ConsentGrant(
    val id: String,
    val ownerUID: String,
    val purpose: Purpose,
    val category: String? = null,
    val granted: Boolean,
    val grantedUtc: Instant,
    val revokedUtc: Instant? = null,
) {
    enum class Purpose {
        DEVICE_SENSORS, CLOUD_SYNC, ROUTES, AI_PROCESSING, EXPERIMENTAL_RECORDING,
        HEALTH_READ, HEALTH_WRITE_WORKOUTS, NOTIFICATIONS, FASTING_TRACKING,
    }
}

@Serializable
data class DataSource(
    val id: String,
    val ownerUID: String,
    val kind: Kind,
    val displayName: String,
    val packageName: String? = null,
    val appVersion: String? = null,
    val region: String? = null,
    val categories: Set<String> = emptySet(),
    val status: Status,
    val lastSyncAttemptUtc: Instant? = null,
    val latestMeasurementUtc: Instant? = null,
    val notes: String? = null,
) {
    enum class Kind { HEALTH_CONNECT, SAMSUNG_HEALTH, GOOGLE_HEALTH, ONETOUCH, OMRON, ONEBYONE, FILE_IMPORT, MANUAL, WATCH_LIVE }
    enum class Status {
        NEEDS_SETUP, PERMISSION_GRANTED, AWAITING_FIRST_RECORD, IMPORTING,
        CURRENT, DELAYED, LIMITED_HISTORY, APPROVAL_PENDING, UNSUPPORTED,
    }
}

/** Result of a runtime capability probe (M0). */
@Serializable
data class CapabilityReport(
    val deviceId: String,
    val probedUtc: Instant,
    val healthConnectAvailable: Boolean,
    val healthConnectSdk: Int? = null,
    val liveHeartRate: Boolean? = null,
    val watchGps: Boolean? = null,
    val passiveMonitoring: Boolean? = null,
    val notes: List<String> = emptyList(),
)
