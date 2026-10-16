package com.nobelle.core.model

import kotlinx.serialization.Serializable

/**
 * Stable scoped owner identity: issuer + subject of the signed-in Google account.
 * Never an email address alone (§2).
 */
@Serializable
data class OwnerId(val issuer: String, val subject: String) {
    val uid: String get() = "$issuer:$subject"
}

/** Identity key for records imported from an external source (§13). */
@Serializable
data class SourceKey(
    val sourceNamespace: String,
    val sourceRecordId: String,
    val originDevice: String? = null,
    val importRoute: String? = null,
)

/** Recording provenance label required on every displayed value (§10). */
enum class Provenance { MEASURED_IMPORTED, MANUALLY_ENTERED, ESTIMATED }

/** How a value arrived. */
enum class RecordingMethod { SENSOR_LIVE, SOURCE_SYNC, FILE_IMPORT, OCR_CONFIRMED, MANUAL }

@Serializable
data class Revision(
    val revision: Long,
    val modifiedUtc: kotlinx.datetime.Instant,
    val deviceId: String,
)
