package com.nobelle.core.sync

import com.nobelle.core.model.SyncEvent
import com.nobelle.drive.DriveStore
import kotlinx.datetime.Clock
import java.util.UUID

class DriveRoundTripDiagnostic(private val drive: DriveStore) {
    data class Result(val fileName: String, val operationUuid: String, val deleted: Boolean)

    suspend fun run(): Result {
        val operationUuid = UUID.randomUUID().toString()
        val event = SyncEvent(
            schemaVersion = 1,
            accountNamespace = "diagnostic",
            installationId = "diagnostic",
            entityId = UUID.randomUUID().toString(),
            entityType = "DriveDiagnostic",
            operationUuid = operationUuid,
            revision = 1,
            op = SyncEvent.Op.UPSERT,
            payloadJson = "{\"personalData\":false}",
            utc = Clock.System.now(),
        )
        var remote: DriveStore.RemoteFile? = null
        var operationError: Throwable? = null
        try {
            remote = drive.putEvent(event)
            val downloaded = drive.getEvent(remote.id)
            check(downloaded == event) { "Downloaded diagnostic event did not match the uploaded event" }
        } catch (error: Throwable) {
            operationError = error
        }

        if (remote != null) {
            try {
                drive.deleteFile(remote.id)
            } catch (cleanupError: Throwable) {
                val prior = operationError?.message?.let { "$it; " }.orEmpty()
                throw IllegalStateException("${prior}temporary Drive diagnostic cleanup failed: ${cleanupError.message}", cleanupError)
            }
        }
        if (operationError != null) throw operationError
        return Result(remote!!.name, operationUuid, deleted = true)
    }
}
