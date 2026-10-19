package com.nobelle.phone.sync

import android.content.Context
import com.nobelle.core.sync.AndroidDriveStore
import com.nobelle.core.sync.OutboxDaoWriter
import com.nobelle.core.sync.RemoteEventApplier
import com.nobelle.core.sync.SyncEngine
import com.nobelle.phone.NobelleApplication
import com.nobelle.phone.auth.DriveTokenHolder
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Drains the phone's encrypted outbox to the owner's Drive appDataFolder via
 * [SyncEngine.upload], then pulls remote event files down through
 * [SyncEngine.download] so changes made on other devices (or this device's
 * own pre-reinstall history) are applied locally. Runs only when a Drive
 * access token is currently held in memory (grant given this session and not
 * yet disconnected); without a token events simply stay PENDING — correct,
 * not an error.
 *
 * Triggered after a watch event is relayed into the outbox and once whenever
 * the app returns to the foreground. No scheduler/WorkManager yet.
 */
object OutboxSyncRunner {
    private val mutex = Mutex()

    suspend fun drainIfPossible(context: Context, ownerUid: String) {
        if (DriveTokenHolder.peek() == null) return
        val app = context.applicationContext as NobelleApplication
        mutex.withLock {
            // Re-read the token per request: a mid-drain disconnect stops
            // further calls instead of reusing a revoked value.
            val engine = SyncEngine(
                drive = AndroidDriveStore(
                    accessToken = {
                        DriveTokenHolder.peek()
                            ?: throw IllegalStateException("Drive access is no longer connected")
                    },
                ),
                outbox = OutboxDaoWriter(app.database.outbox(), ownerUid),
                applier = RemoteEventApplier(app.database, ownerUid),
                accountNamespace = ownerUid,
            )
            // Drain in batches: one foreground pass should empty a backlog
            // (e.g. a bulk Health Connect import), not just its first page.
            val outbox = app.database.outbox()
            var batches = 0
            while (batches++ < MAX_BATCHES &&
                outbox.pending(ownerUid, 1).isNotEmpty() &&
                // Zero uploads in a batch means every event failed (expired
                // token, no network) — retrying the same batch just spins.
                engine.upload() > 0
            ) {
                // Batch drained successfully; loop re-checks pending.
            }
            // Download every page: the cursor only tracks position, and a
            // partial pass is still correct — remaining files apply next run.
            var cursor: String? = null
            do {
                cursor = engine.download(cursor)
            } while (cursor != null)
        }
    }

    private const val MAX_BATCHES = 60
}
