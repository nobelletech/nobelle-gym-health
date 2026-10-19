package com.nobelle.phone

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Diagnostic probe, not a product test: reads the live encrypted outbox in
 * the running app's own process so sync-drain verification can see real
 * state counts without touching the SQLCipher passphrase or Drive token.
 * Logs only aggregated state counts — no owner ids, entity ids or payloads.
 */
@RunWith(AndroidJUnit4::class)
class OutboxStateProbe {

    @Test
    fun reportOutboxStateCounts() {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as NobelleApplication
        app.database.query(
            "SELECT state, COUNT(*) FROM sync_outbox GROUP BY state",
            null,
        ).use { cursor ->
            var any = false
            while (cursor.moveToNext()) {
                any = true
                Log.i(TAG, "outbox state ${cursor.getString(0)} = ${cursor.getLong(1)}")
            }
            if (!any) Log.i(TAG, "outbox is empty")
        }
        app.database.query(
            "SELECT MAX(attempts) FROM sync_outbox",
            null,
        ).use { cursor ->
            if (cursor.moveToNext()) Log.i(TAG, "max attempts = ${cursor.getLong(0)}")
        }
        app.database.query(
            "SELECT COUNT(*) FROM watch_workouts",
            null,
        ).use { cursor ->
            if (cursor.moveToNext()) Log.i(TAG, "watch workouts applied = ${cursor.getLong(0)}")
        }
        app.database.query(
            "SELECT DISTINCT lastError FROM sync_outbox WHERE lastError IS NOT NULL LIMIT 5",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                Log.i(TAG, "lastError: ${cursor.getString(0)}")
            }
        }
    }

    /**
     * Background-sync diagnostic: marks the most recent APPLIED row PENDING
     * so the WorkManager drain has a real event to re-upload. Re-uploading an
     * already-uploaded operation is harmless — ingest dedupes by
     * operationUuid on every device, including this one.
     */
    @Test
    fun seedPendingEvent() {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as NobelleApplication
        val updated = app.database.compileStatement(
            "UPDATE sync_outbox SET state = 'PENDING' WHERE operationUuid = (" +
                "SELECT operationUuid FROM sync_outbox WHERE state = 'APPLIED' " +
                "ORDER BY createdUtc DESC LIMIT 1)",
        ).executeUpdateDelete()
        Log.i(TAG, "seeded pending events = $updated")
    }

    private companion object {
        const val TAG = "OutboxProbe"
    }
}
