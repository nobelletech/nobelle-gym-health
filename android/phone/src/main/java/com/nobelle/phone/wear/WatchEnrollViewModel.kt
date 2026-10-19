package com.nobelle.phone.wear

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.Wearable
import com.nobelle.core.model.EnrollCrypto
import com.nobelle.core.model.EnrollPaths
import com.nobelle.core.model.EnrollResponse
import com.nobelle.core.model.EnrollRevoke
import com.nobelle.core.model.EnrollWire
import com.nobelle.core.model.OwnerId
import com.nobelle.core.model.PhoneWatchEnrollment
import com.nobelle.phone.NobelleApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Phone side of watch enrollment. Surfaces the pending request file written
 * by [EnrollListenerService] to the Today-screen card, and performs confirm /
 * reject / forget. Enrollment is per-owner: stores are keyed by owner uid and
 * an owner identity must exist before pairing is offered.
 */
class WatchEnrollViewModel(application: Application) : AndroidViewModel(application) {
    data class PendingUi(val watchModel: String, val code: String)

    data class EnrolledUi(
        val watchModel: String,
        val enrolledUtc: Instant,
        val lastContactUtc: Instant,
        val eventsReceived: Int,
    )

    data class UiState(
        val ownerAvailable: Boolean = false,
        val pending: PendingUi? = null,
        val enrolled: EnrolledUi? = null,
        /** PENDING rows in the encrypted outbox awaiting Drive upload. */
        val outboxPending: Int = 0,
        /** True when the bounded count hit its cap and the real total is higher. */
        val outboxPendingCapped: Boolean = false,
        val busy: Boolean = false,
        val message: String? = null,
    )

    private val app = application as NobelleApplication
    private val enrollments = WatchEnrollmentStore(application)
    private val pendings = PendingWatchRequestStore(application)

    private val mutableState = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = mutableState

    private var ownerUid: String? = null
    private var observeJob: Job? = null

    /** Follows the signed-in owner, like the other owner-scoped view models. */
    fun selectOwner(owner: OwnerId?) {
        val uid = owner?.uid
        if (uid == ownerUid) return
        ownerUid = uid
        observeJob?.cancel()
        mutableState.value = UiState(ownerAvailable = uid != null)
        if (uid != null) startObserving(uid)
    }

    /**
     * Re-reads the owner-scoped files on a short interval while an owner is
     * active, and immediately when the listener service signals a change.
     */
    private fun startObserving(uid: String) {
        observeJob = viewModelScope.launch {
            launch { PhoneEnrollSignals.refresh.collect { reload(uid) } }
            while (isActive) {
                reload(uid)
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun reload(uid: String) = withContext(Dispatchers.IO) {
        val enrolled = runCatching { enrollments.read(uid) }.getOrNull()
        val pending = runCatching { pendings.read(uid) }.getOrNull()
        // Bounded scan; a dedicated count query is unnecessary at this scale.
        // Hitting the cap is reported honestly instead of reading as exact.
        val outboxPending = runCatching {
            app.database.outbox().pending(uid, OUTBOX_COUNT_LIMIT).size
        }.getOrDefault(0)
        val outboxPendingCapped = outboxPending >= OUTBOX_COUNT_LIMIT
        mutableState.value = mutableState.value.copy(
            ownerAvailable = true,
            pending = pending?.let {
                PendingUi(
                    watchModel = it.watchModel,
                    code = EnrollCrypto.confirmationCode(
                        EnrollCrypto.fromBase64(it.watchPubKeyX962),
                        EnrollCrypto.fromBase64(it.nonce),
                    ),
                )
            },
            enrolled = enrolled?.let {
                EnrolledUi(
                    it.watchModel,
                    it.enrolledUtc,
                    it.lastContactUtc,
                    it.eventsReceived,
                )
            },
            outboxPending = outboxPending,
            outboxPendingCapped = outboxPendingCapped,
        )
    }

    /**
     * Confirms the pending request: generates the phone ephemeral keypair and
     * nonce, sends RESPONSE to the requesting node, derives the shared token,
     * and persists the owner-scoped enrollment. Nothing secret is logged or
     * shown — the card only ever displayed the confirmation code.
     */
    fun confirmPending() {
        val uid = ownerUid ?: return
        if (mutableState.value.busy) return
        mutableState.value = mutableState.value.copy(busy = true, message = null)
        viewModelScope.launch(Dispatchers.IO) {
            val pending = runCatching { pendings.read(uid) }.getOrNull()
            if (pending == null) {
                finish(busy = false, message = "Pairing request is no longer pending")
                reload(uid)
                return@launch
            }
            val run = runCatching {
                val keyPair = EnrollCrypto.generateKeyPair()
                val phoneNonce = EnrollCrypto.generateNonce()
                val phonePub = EnrollCrypto.encodePubKeyX962(keyPair.public)
                val response = EnrollResponse(
                    phoneInstallationId = app.installationId,
                    phonePubKeyX962 = EnrollCrypto.toBase64(phonePub),
                    nonce = EnrollCrypto.toBase64(phoneNonce),
                    accepted = true,
                )
                Wearable.getMessageClient(app).sendMessage(
                    pending.sourceNodeId,
                    EnrollPaths.RESPONSE,
                    EnrollWire.encodeResponse(response),
                ).awaitTask()
                val token = EnrollCrypto.deriveEnrollmentToken(
                    myPrivate = keyPair.private,
                    peerPubKeyX962 = EnrollCrypto.fromBase64(pending.watchPubKeyX962),
                    watchNonce = EnrollCrypto.fromBase64(pending.nonce),
                    phoneNonce = phoneNonce,
                )
                val now = Clock.System.now()
                enrollments.write(
                    uid,
                    PhoneWatchEnrollment(
                        watchInstallationId = pending.watchInstallationId,
                        watchModel = pending.watchModel,
                        tokenBase64 = EnrollCrypto.toBase64(token),
                        enrolledUtc = now,
                        lastContactUtc = now,
                    ),
                )
                pendings.clear(uid)
            }
            if (run.isSuccess) {
                finish(busy = false, message = "Watch enrolled")
            } else {
                finish(
                    busy = false,
                    message = "Could not reach the watch — pairing was not completed",
                )
            }
            reload(uid)
        }
    }

    /** Declines the pending request and tells the watch. */
    fun rejectPending() {
        val uid = ownerUid ?: return
        if (mutableState.value.busy) return
        mutableState.value = mutableState.value.copy(busy = true, message = null)
        viewModelScope.launch(Dispatchers.IO) {
            val pending = runCatching { pendings.read(uid) }.getOrNull()
            if (pending != null) {
                runCatching {
                    Wearable.getMessageClient(app).sendMessage(
                        pending.sourceNodeId,
                        EnrollPaths.RESPONSE,
                        EnrollWire.encodeResponse(EnrollResponse(accepted = false)),
                    ).awaitTask()
                }
                pendings.clear(uid)
            }
            finish(busy = false, message = "Pairing declined")
            reload(uid)
        }
    }

    /** Sends REVOKE to reachable nodes, then deletes the local enrollment. */
    fun forgetWatch() {
        val uid = ownerUid ?: return
        if (mutableState.value.busy) return
        mutableState.value = mutableState.value.copy(busy = true, message = null)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val payload = EnrollWire.encodeRevoke(
                    EnrollRevoke(installationId = app.installationId),
                )
                val client = Wearable.getMessageClient(app)
                Wearable.getNodeClient(app).connectedNodes.awaitTask()
                    .forEach { node ->
                        runCatching {
                            client.sendMessage(node.id, EnrollPaths.REVOKE, payload)
                                .awaitTask()
                        }
                    }
            }
            enrollments.clear(uid)
            pendings.clear(uid)
            finish(busy = false, message = "Watch forgotten")
            reload(uid)
        }
    }

    private fun finish(busy: Boolean, message: String?) {
        mutableState.value = mutableState.value.copy(busy = busy, message = message)
    }

    private companion object {
        const val POLL_INTERVAL_MS = 1_500L
        const val OUTBOX_COUNT_LIMIT = 500
    }
}
