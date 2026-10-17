package com.nobelle.core.sync

import com.nobelle.core.model.SyncEvent
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class AndroidDriveStoreTest {
    @Test
    fun `list is restricted to appDataFolder and token stays in header`() = runBlocking {
        val connection = FakeConnection("""{"files":[],"nextPageToken":"next"}""")
        var requestedUrl = ""
        val store = AndroidDriveStore(
            accessToken = { "secret-token" },
            connectionFactory = { url -> requestedUrl = url; connection },
        )

        val page = store.listEvents("cursor")

        assertEquals("next", page.nextCursor)
        assertTrue(requestedUrl.contains("spaces=appDataFolder"))
        assertTrue(requestedUrl.contains("pageToken=cursor"))
        assertFalse(requestedUrl.contains("secret-token"))
        assertEquals("Bearer secret-token", connection.getRequestProperty("Authorization"))
    }

    @Test
    fun `put writes immutable event with appDataFolder as only parent`() = runBlocking {
        val connection = FakeConnection("""{"id":"file-1","name":"opaque.json"}""")
        val store = AndroidDriveStore(
            accessToken = { "token" },
            connectionFactory = { connection },
        )
        val event = SyncEvent(
            schemaVersion = 1,
            accountNamespace = "issuer:subject",
            installationId = "install-1",
            entityId = "entity-1",
            entityType = "GymVisit",
            operationUuid = "operation-1",
            revision = 1,
            op = SyncEvent.Op.UPSERT,
            payloadJson = "{}",
            utc = Instant.parse("2026-10-16T00:00:00Z"),
        )

        val file = store.putEvent(event)
        val body = connection.output.toString(Charsets.UTF_8)

        assertEquals("file-1", file.id)
        assertEquals("POST", connection.requestMethod)
        assertTrue(body.contains("\"parents\":[\"appDataFolder\"]"))
        assertFalse(body.contains("\"parents\":[\"root\"]"))
        assertTrue(body.contains("operation-1"))
    }

    private class FakeConnection(private val response: String, private val status: Int = 200) :
        HttpURLConnection(URL("https://www.googleapis.com")) {
        val output = ByteArrayOutputStream()

        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun connect() = Unit
        override fun getResponseCode() = status
        override fun getInputStream() = ByteArrayInputStream(response.encodeToByteArray())
        override fun getErrorStream() = ByteArrayInputStream(response.encodeToByteArray())
        override fun getOutputStream() = output
    }
}
