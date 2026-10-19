package com.nobelle.core.sync

import com.nobelle.core.model.SyncEvent
import com.nobelle.drive.DriveStore
import com.nobelle.drive.EventCodec
import com.nobelle.drive.EventFileNaming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

class AndroidDriveStore(
    private val accessToken: suspend () -> String,
    private val connectionFactory: (String) -> HttpURLConnection = { URI(it).toURL().openConnection() as HttpURLConnection },
) : DriveStore {
    override suspend fun putEvent(event: SyncEvent): DriveStore.RemoteFile = withContext(Dispatchers.IO) {
        val bytes = EventCodec.encode(event)
        val boundary = "nobelle_${UUID.randomUUID()}"
        val metadata = buildJsonObject {
            put("name", EventFileNaming.nameFor(event))
            put("mimeType", "application/json")
            put("parents", kotlinx.serialization.json.buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("appDataFolder")) })
        }.toString()
        val body = multipart(boundary, metadata, bytes)
        val response = request(
            url = "$UPLOAD_API/files?uploadType=multipart&fields=id,name",
            method = "POST",
            contentType = "multipart/related; boundary=$boundary",
            body = body,
        )
        val json = Json.parseToJsonElement(response.decodeToString()).jsonObject
        DriveStore.RemoteFile(
            id = json.getValue("id").jsonPrimitive.content,
            name = json.getValue("name").jsonPrimitive.content,
        )
    }

    override suspend fun getEvent(fileId: String): SyncEvent? = withContext(Dispatchers.IO) {
        try {
            EventCodec.decode(request("$API/files/${encode(fileId)}?alt=media"))
        } catch (error: DriveHttpException) {
            if (error.status == 404) null else throw error
        }
    }

    override suspend fun listEvents(cursor: String?): DriveStore.Page = withContext(Dispatchers.IO) {
        val params = linkedMapOf(
            "spaces" to "appDataFolder",
            "pageSize" to "100",
            "fields" to "nextPageToken,files(id,name)",
            "q" to "trashed = false",
        )
        if (cursor != null) params["pageToken"] = cursor
        val query = params.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }
        val root = Json.parseToJsonElement(request("$API/files?$query").decodeToString()).jsonObject
        val files = root["files"]?.jsonArray.orEmpty().map { element ->
            val file = element.jsonObject
            DriveStore.RemoteFile(
                id = file.getValue("id").jsonPrimitive.content,
                name = file.getValue("name").jsonPrimitive.content,
            )
        }
        DriveStore.Page(
            files = files,
            nextCursor = root["nextPageToken"]?.jsonPrimitive?.contentOrNull,
        )
    }

    override suspend fun deleteFile(fileId: String): Unit = withContext(Dispatchers.IO) {
        request("$API/files/${encode(fileId)}", method = "DELETE")
    }

    private suspend fun request(
        url: String,
        method: String = "GET",
        contentType: String? = null,
        body: ByteArray? = null,
    ): ByteArray {
        val connection = connectionFactory(url)
        try {
            // Default HttpURLConnection timeouts are infinite: a stalled
            // socket would suspend the outbox drain forever, silently.
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.requestMethod = method
            connection.setRequestProperty("Authorization", "Bearer ${accessToken()}")
            connection.setRequestProperty("Accept", "application/json")
            if (contentType != null) connection.setRequestProperty("Content-Type", contentType)
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body) }
            }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.use { it.readBytes() } ?: byteArrayOf()
            if (status !in 200..299) throw DriveHttpException(status, response.decodeToString())
            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun multipart(boundary: String, metadata: String, bytes: ByteArray): ByteArray {
        val prefix = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n" +
            "--$boundary\r\nContent-Type: application/json\r\n\r\n"
        val suffix = "\r\n--$boundary--"
        return prefix.encodeToByteArray() + bytes + suffix.encodeToByteArray()
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    class DriveHttpException(val status: Int, detail: String) :
        IllegalStateException("Google Drive request failed ($status): $detail")

    private companion object {
        const val API = "https://www.googleapis.com/drive/v3"
        const val UPLOAD_API = "https://www.googleapis.com/upload/drive/v3"
    }
}
