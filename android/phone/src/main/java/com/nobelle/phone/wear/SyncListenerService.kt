package com.nobelle.phone.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.nobelle.core.data.repository.toOutboxEntity
import com.nobelle.core.model.EnrollCrypto
import com.nobelle.core.model.SyncPaths
import com.nobelle.core.model.WatchSyncAck
import com.nobelle.core.model.WatchSyncCodec
import com.nobelle.phone.NobelleApplication
import com.nobelle.phone.auth.EncryptedOwnerStore
import com.nobelle.phone.sync.OutboxSyncRunner
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * Receives HMAC-authenticated workout events from the enrolled watch on
 * [SyncPaths.EVENT]. A valid event is relayed into the encrypted outbox as a
 * regular SyncEvent (deduped by operationUuid), the enrollment's last-contact
 * and received count update, and the watch gets an ACK so it can drop its
 * queued copy. Drops are silent: nothing about a rejected payload is logged,
 * and the enrollment token is used only to verify, never revealed.
 */
class SyncListenerService : WearableListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != SyncPaths.EVENT) return
        scope.launch { handle(event) }
    }

    private suspend fun handle(event: MessageEvent) {
        val ownerUid = EncryptedOwnerStore(this).read()?.uid
            ?: return drop("no_owner")
        val enrollments = WatchEnrollmentStore(this)
        val enrollment = enrollments.read(ownerUid)
            ?: return drop("not_enrolled")
        val wire = runCatching { WatchSyncCodec.decodeEvent(event.data) }
            .getOrNull() ?: return drop("malformed")
        if (wire.schemaVersion != SyncPaths.SCHEMA_VERSION) {
            return drop("schema_version")
        }
        if (wire.watchInstallationId != enrollment.watchInstallationId) {
            return drop("unknown_watch")
        }
        val token = runCatching { EnrollCrypto.fromBase64(enrollment.tokenBase64) }
            .getOrNull() ?: return drop("no_token")
        if (!WatchSyncCodec.verify(token, wire)) return drop("bad_tag")

        val app = applicationContext as NobelleApplication
        val now = Clock.System.now()
        runCatching {
            app.database.outbox().enqueue(
                WatchSyncCodec.toSyncEvent(wire, ownerUid)
                    .toOutboxEntity(now.toEpochMilliseconds()),
            )
        }.onFailure { return drop("outbox_write_failed") }
        enrollments.write(
            ownerUid,
            enrollment.copy(
                lastContactUtc = now,
                eventsReceived = enrollment.eventsReceived + 1,
            ),
        )
        PhoneEnrollSignals.refresh.emit(Unit)
        // Confirm only after the insert committed — the watch deletes its
        // queued copy on this ACK, so losing it would lose the workout.
        runCatching {
            Wearable.getMessageClient(this@SyncListenerService).sendMessage(
                event.sourceNodeId,
                SyncPaths.ACK,
                WatchSyncCodec.encodeAck(
                    WatchSyncAck(operationUuid = wire.operationUuid),
                ),
            ).awaitTask()
        }
        // Best-effort Drive drain while a token is held in memory.
        OutboxSyncRunner.drainIfPossible(this@SyncListenerService, ownerUid)
    }

    private fun drop(reason: String) {
        WatchSyncDropCounters.count(reason)
    }

    override fun onDestroy() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        super.onDestroy()
    }
}

/**
 * In-memory tallies of silently dropped sync events, by reason. Never logged
 * and never persisted — diagnostics only, useful when wiring new states.
 */
internal object WatchSyncDropCounters {
    private val counts = ConcurrentHashMap<String, AtomicLong>()

    fun count(reason: String) {
        counts.computeIfAbsent(reason) { AtomicLong() }.incrementAndGet()
    }

    fun snapshot(): Map<String, Long> = counts.mapValues { it.value.get() }
}
