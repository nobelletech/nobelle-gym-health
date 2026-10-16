package com.nobelle.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlin.time.Duration

@Serializable
data class FastingSchedule(
    val id: String,
    val ownerUID: String,
    val enabled: Boolean = false, // no default enrollment (§21)
    val fastingHours: Int,
    val eatingHours: Int,
    val startLocalTime: String, // "HH:mm" local
    val daysOfWeek: List<Int>,
    val timeZonePolicy: String = "FOLLOW_DEVICE",
    val exceptions: List<String> = emptyList(), // ISO local dates
    val reminderPreferences: ReminderPreferences = ReminderPreferences(),
    val revision: Long = 1,
) {
    init {
        require(fastingHours > 0 && eatingHours > 0) { "Fasting/eating hours must be positive" }
        require(fastingHours + eatingHours == 24) { "Daily window template must total 24h" }
        require(daysOfWeek.all { it in 1..7 }) { "daysOfWeek are 1..7" }
    }

    @Serializable
    data class ReminderPreferences(
        val beforeStartMin: Int? = null,
        val beforeEndMin: Int? = null,
        val hydrationReminders: Boolean = false,
        val quietHours: String? = null,
    )

    companion object {
        val TEMPLATES = listOf(12 to 12, 14 to 10, 16 to 8)
    }
}

/**
 * An actual fast. Timer derives from persisted instants, never a running service (§21).
 * One actual active fast per account; overlaps resolve on sync.
 */
@Serializable
data class FastingSession(
    val id: String,
    val ownerUID: String,
    val actualStartInstant: Instant,
    val actualEndInstant: Instant? = null,
    val originalStartZone: String,
    val originalEndZone: String? = null,
    val targetDurationSec: Long,
    val scheduleId: String? = null,
    val status: Status,
    val endedEarly: Boolean = false, // normal completion state, not failure
    val notes: String? = null,
    val checkIns: List<CheckIn> = emptyList(),
    val revision: Long = 1,
    val deletedUtc: Instant? = null,
) {
    enum class Status { ACTIVE, COMPLETED, CANCELLED }

    @Serializable
    data class CheckIn(
        val utc: Instant,
        val hunger: Int? = null,
        val energy: Int? = null,
        val mood: Int? = null,
        val symptoms: String? = null,
    )

    fun elapsedAt(now: Instant): Duration =
        (actualEndInstant ?: now) - actualStartInstant

    fun targetReached(now: Instant): Boolean =
        elapsedAt(now).inWholeSeconds >= targetDurationSec
}
