package com.nobelle.core.health

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.nobelle.core.model.HealthObservation
import com.nobelle.core.model.MarkerGroup
import com.nobelle.core.model.OwnerId
import com.nobelle.core.model.Provenance
import com.nobelle.core.model.RecordingMethod
import com.nobelle.core.model.SourceKey
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.UUID
import kotlin.reflect.KClass
import kotlinx.datetime.Instant

enum class HealthConnectCategory(
    val displayName: String,
    val permission: String,
) {
    STEPS("Steps", HealthPermission.getReadPermission(StepsRecord::class)),
    RESTING_HEART_RATE(
        "Resting heart rate",
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
    ),
    WEIGHT("Weight", HealthPermission.getReadPermission(WeightRecord::class)),
    SLEEP("Sleep", HealthPermission.getReadPermission(SleepSessionRecord::class)),
}

data class HealthConnectImportResult(
    val observations: List<HealthObservation>,
    val counts: Map<HealthConnectCategory, Int>,
)

class HealthConnectImporter(
    private val client: HealthConnectClient,
) {
    suspend fun import(
        owner: OwnerId,
        categories: Set<HealthConnectCategory>,
        rangeStart: java.time.Instant,
        rangeEnd: java.time.Instant,
        importedAt: Instant,
    ): HealthConnectImportResult {
        require(!rangeEnd.isBefore(rangeStart)) { "Health Connect import range is invalid" }
        val observations = buildList {
            if (HealthConnectCategory.STEPS in categories) {
                readAll(StepsRecord::class, rangeStart, rangeEnd).forEach {
                    add(HealthConnectMapper.steps(owner, it, importedAt))
                }
            }
            if (HealthConnectCategory.RESTING_HEART_RATE in categories) {
                readAll(RestingHeartRateRecord::class, rangeStart, rangeEnd).forEach {
                    add(HealthConnectMapper.restingHeartRate(owner, it, importedAt))
                }
            }
            if (HealthConnectCategory.WEIGHT in categories) {
                readAll(WeightRecord::class, rangeStart, rangeEnd).forEach {
                    add(HealthConnectMapper.weight(owner, it, importedAt))
                }
            }
            if (HealthConnectCategory.SLEEP in categories) {
                readAll(SleepSessionRecord::class, rangeStart, rangeEnd).forEach {
                    add(HealthConnectMapper.sleep(owner, it, importedAt))
                }
            }
        }
        return HealthConnectImportResult(
            observations = observations.sortedByDescending { it.measuredUtc },
            counts = categories.associateWith { category ->
                observations.count { it.context["healthConnectCategory"] == category.name }
            },
        )
    }

    private suspend fun <T : androidx.health.connect.client.records.Record> readAll(
        type: KClass<T>,
        start: java.time.Instant,
        end: java.time.Instant,
    ): List<T> {
        val records = mutableListOf<T>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = type,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    ascendingOrder = false,
                    pageSize = PAGE_SIZE,
                    pageToken = pageToken,
                )
            )
            records += response.records
            pageToken = response.pageToken
        } while (pageToken != null)
        return records
    }

    private companion object {
        const val PAGE_SIZE = 1000
    }
}

object HealthConnectMapper {
    fun steps(owner: OwnerId, record: StepsRecord, importedAt: Instant): HealthObservation =
        observation(
            owner = owner,
            type = "steps",
            category = HealthConnectCategory.STEPS,
            marker = "steps",
            group = MarkerGroup.ACTIVITY,
            value = record.count.toDouble(),
            unit = "count",
            measured = record.endTime,
            zoneOffsetSec = record.endZoneOffset?.totalSeconds,
            metadata = record.metadata,
            importedAt = importedAt,
            context = mapOf("windowStartUtc" to record.startTime.toString()),
        )

    fun restingHeartRate(
        owner: OwnerId,
        record: RestingHeartRateRecord,
        importedAt: Instant,
    ): HealthObservation = observation(
        owner = owner,
        type = "resting_heart_rate",
        category = HealthConnectCategory.RESTING_HEART_RATE,
        marker = "resting_heart_rate",
        group = MarkerGroup.HEART_VITALS,
        value = record.beatsPerMinute.toDouble(),
        unit = "bpm",
        measured = record.time,
        zoneOffsetSec = record.zoneOffset?.totalSeconds,
        metadata = record.metadata,
        importedAt = importedAt,
    )

    fun weight(owner: OwnerId, record: WeightRecord, importedAt: Instant): HealthObservation =
        observation(
            owner = owner,
            type = "weight",
            category = HealthConnectCategory.WEIGHT,
            marker = "weight",
            group = MarkerGroup.BODY,
            value = record.weight.inKilograms,
            unit = "kg",
            rawValue = record.weight.inKilograms,
            rawUnit = "kg",
            measured = record.time,
            zoneOffsetSec = record.zoneOffset?.totalSeconds,
            metadata = record.metadata,
            importedAt = importedAt,
        )

    fun sleep(owner: OwnerId, record: SleepSessionRecord, importedAt: Instant): HealthObservation =
        observation(
            owner = owner,
            type = "sleep_session",
            category = HealthConnectCategory.SLEEP,
            marker = "sleep_duration",
            group = MarkerGroup.SLEEP_RECOVERY,
            value = Duration.between(record.startTime, record.endTime).seconds / 60.0,
            unit = "min",
            measured = record.endTime,
            zoneOffsetSec = record.endZoneOffset?.totalSeconds,
            metadata = record.metadata,
            importedAt = importedAt,
            context = mapOf(
                "windowStartUtc" to record.startTime.toString(),
                "stageCount" to record.stages.size.toString(),
            ),
        )

    private fun observation(
        owner: OwnerId,
        type: String,
        category: HealthConnectCategory,
        marker: String,
        group: MarkerGroup,
        value: Double,
        unit: String,
        measured: java.time.Instant,
        zoneOffsetSec: Int?,
        metadata: Metadata,
        importedAt: Instant,
        rawValue: Double? = value,
        rawUnit: String? = unit,
        context: Map<String, String> = emptyMap(),
    ): HealthObservation {
        val namespace = "health_connect:${metadata.dataOrigin.packageName}"
        val sourceRecordId = metadata.id.ifBlank {
            metadata.clientRecordId?.takeIf { it.isNotBlank() }
                ?: "$type:${measured.epochSecond}:${measured.nano}:$value:$unit"
        }
        val canonicalKey = "$namespace|$type|$sourceRecordId"
        val device = metadata.device?.let {
            listOfNotNull(it.manufacturer, it.model).joinToString(" ").ifBlank { null }
        }
        val measuredUtc = measured.toKotlinInstant()
        return HealthObservation(
            id = UUID.nameUUIDFromBytes(canonicalKey.toByteArray(StandardCharsets.UTF_8)).toString(),
            ownerUID = owner.uid,
            marker = marker,
            group = group,
            value = value,
            unit = unit,
            rawValue = rawValue,
            rawUnit = rawUnit,
            measuredUtc = measuredUtc,
            timeZoneOffsetSec = zoneOffsetSec,
            provenance = if (metadata.recordingMethod == Metadata.RECORDING_METHOD_MANUAL_ENTRY) {
                Provenance.MANUALLY_ENTERED
            } else {
                Provenance.MEASURED_IMPORTED
            },
            method = RecordingMethod.SOURCE_SYNC,
            source = SourceKey(
                sourceNamespace = namespace,
                sourceRecordId = sourceRecordId,
                originDevice = device,
                importRoute = "Health Connect",
            ),
            qualityFlag = if (measuredUtc > importedAt) {
                HealthObservation.QualityFlag.FUTURE_DATED
            } else {
                HealthObservation.QualityFlag.OK
            },
            context = context + ("healthConnectCategory" to category.name),
            importedUtc = importedAt,
        )
    }

    private fun java.time.Instant.toKotlinInstant(): Instant =
        Instant.fromEpochSeconds(epochSecond, nano)
}
