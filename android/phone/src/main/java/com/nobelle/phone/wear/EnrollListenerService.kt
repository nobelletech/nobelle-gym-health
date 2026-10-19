package com.nobelle.phone.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.nobelle.core.model.EnrollPaths
import com.nobelle.core.model.EnrollWire
import com.nobelle.core.model.PendingWatchEnrollRequest
import com.nobelle.phone.auth.EncryptedOwnerStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * Receives watch enrollment traffic while the phone app is in the background
 * or foreground. Message payloads never contain secrets — ephemeral public
 * keys and random nonces only — and nothing from a payload is ever logged.
 *
 * Requests persist as an owner-scoped pending file; the Today-screen watch
 * card observes that file while it is visible and offers Confirm/Reject.
 * Without an established owner identity there is no scope to persist under,
 * so the request is dropped.
 */
class EnrollListenerService : WearableListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(event: MessageEvent) {
        val ownerUid = EncryptedOwnerStore(this).read()?.uid ?: return
        when (event.path) {
            EnrollPaths.REQUEST -> handleRequest(event, ownerUid)
            EnrollPaths.REVOKE -> handleRevoke(event, ownerUid)
        }
    }

    private fun handleRequest(event: MessageEvent, ownerUid: String) {
        val request = runCatching { EnrollWire.decodeRequest(event.data) }
            .getOrNull() ?: return
        if (request.schemaVersion != EnrollPaths.SCHEMA_VERSION) return
        val pending = PendingWatchEnrollRequest(
            watchInstallationId = request.watchInstallationId,
            watchModel = request.watchModel,
            watchPubKeyX962 = request.watchPubKeyX962,
            nonce = request.nonce,
            sourceNodeId = event.sourceNodeId,
            receivedUtc = Clock.System.now(),
        )
        runCatching { PendingWatchRequestStore(this).write(ownerUid, pending) }
        scope.launch { PhoneEnrollSignals.refresh.emit(Unit) }
    }

    private fun handleRevoke(event: MessageEvent, ownerUid: String) {
        val revoke = runCatching { EnrollWire.decodeRevoke(event.data) }
            .getOrNull() ?: return
        if (revoke.schemaVersion != EnrollPaths.SCHEMA_VERSION) return
        val enrollments = WatchEnrollmentStore(this)
        val pendings = PendingWatchRequestStore(this)
        // Only delete what actually belongs to the revoking watch.
        if (enrollments.read(ownerUid)?.watchInstallationId == revoke.installationId) {
            enrollments.clear(ownerUid)
        }
        if (pendings.read(ownerUid)?.watchInstallationId == revoke.installationId) {
            pendings.clear(ownerUid)
        }
        scope.launch { PhoneEnrollSignals.refresh.emit(Unit) }
    }

    override fun onDestroy() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        super.onDestroy()
    }
}

/** In-process nudge so a visible enrollment card re-reads the pending file. */
internal object PhoneEnrollSignals {
    val refresh = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
}
