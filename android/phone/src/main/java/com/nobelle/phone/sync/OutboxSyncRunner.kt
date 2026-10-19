package com.nobelle.phone.sync

import android.content.Context
import com.nobelle.core.model.SyncEvent
import com.nobelle.core.sync.AndroidDriveStore
import com.nobelle.core.sync.OutboxDaoWriter
import com.nobelle.core.sync.SyncEngine
import com.nobelle.phone.NobelleApplication
import com.nobelle.phone.auth.DriveTokenHolder
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Drains the phone's encrypted outbox to the owner's Drive appDataFolder via
 * [SyncEngine.upload]. Runs only when a Drive access token is currently held
 * in memory (grant given this session and not yet disconnected); without a
 * token events simply stay PENDING — correct, not an error.
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
                applier = NoOpApplier,
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
        }
    }

    /** Download/apply of remote events is not implemented on the phone yet. */
    private object NoOpApplier : SyncEngine.RemoteApplier {
        override suspend fun seenOperation(operationUuid: String): Boolean = true
        override suspend fun apply(event: SyncEvent) = Unit
    }

    private const val MAX_BATCHES = 60
}
