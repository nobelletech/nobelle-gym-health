package com.nobelle.wear

import android.content.Context
import android.os.Build
import com.google.android.gms.wearable.Wearable
import com.nobelle.core.model.EnrollCrypto
import com.nobelle.core.model.EnrollPaths
import com.nobelle.core.model.EnrollRequest
import com.nobelle.core.model.EnrollRevoke
import com.nobelle.core.model.EnrollWire
import com.nobelle.core.model.WatchEnrollState
import com.nobelle.core.model.WatchEnrollTransitions
import com.nobelle.core.model.WatchPhoneEnrollment
import java.security.KeyPair
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * Watch side of phone enrollment. Holds the in-memory ephemeral keypair and
 * nonce for a request in flight, drives the UI state machine, and persists
 * the wrapped enrollment record. The ephemeral private key lives only in
 * memory — a process restart mid-wait simply returns to not-enrolled.
 *
 * Singleton so the manifest-declared [WearEnrollListenerService] and the UI
 * share one session. Nothing secret is ever logged or placed in UI state.
 */
object WearEnrollmentCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val mutableState = MutableStateFlow<WatchEnrollState>(WatchEnrollState.NotEnrolled)
    val state: StateFlow<WatchEnrollState> = mutableState

    private val mutableMessage = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = mutableMessage

    private var store: WearEnrollmentStore? = null
    private var installationId: WearInstallationId? = null
    private var initialized = false

    // Ephemeral material for a request in flight — cleared on every exit path.
    private var pendingKeyPair: KeyPair? = null
    private var pendingNonce: ByteArray? = null
    private var timeoutJob: Job? = null

    @Synchronized
    private fun ensureInit(context: Context) {
        if (initialized) return
        val appContext = context.applicationContext
        store = WearEnrollmentStore(appContext)
        installationId = WearInstallationId(appContext)
        val stored = store?.read()
        mutableState.value = stored?.let { WatchEnrollState.Enrolled(it.enrolledUtc) }
            ?: WatchEnrollState.NotEnrolled
        initialized = true
    }

    /** Called once from the view model so a persisted enrollment shows. */
    fun attach(context: Context) = ensureInit(context)

    /** User tapped "Enroll watch": sends REQUEST to every connected node. */
    fun beginEnroll(context: Context) {
        ensureInit(context)
        if (mutableState.value is WatchEnrollState.Waiting) return
        mutableMessage.value = null
        scope.launch(Dispatchers.IO) {
            val nodes = runCatching {
                Wearable.getNodeClient(context).connectedNodes.awaitTask()
            }.getOrNull().orEmpty()
            if (nodes.isEmpty()) {
                mutableMessage.value = "No connected phone found"
                return@launch
            }
            val keyPair = EnrollCrypto.generateKeyPair()
            val nonce = EnrollCrypto.generateNonce()
            val pubX962 = EnrollCrypto.encodePubKeyX962(keyPair.public)
            val request = EnrollRequest(
                watchInstallationId = installationId!!.getOrCreate(),
                watchModel = Build.MODEL,
                watchPubKeyX962 = EnrollCrypto.toBase64(pubX962),
                nonce = EnrollCrypto.toBase64(nonce),
            )
            val payload = EnrollWire.encodeRequest(request)
            val sent = nodes.count { node ->
                runCatching {
                    Wearable.getMessageClient(context)
                        .sendMessage(node.id, EnrollPaths.REQUEST, payload)
                        .awaitTask()
                }.isSuccess
            }
            if (sent == 0) {
                dropPending()
                mutableMessage.value = "Enrollment request could not be sent"
                return@launch
            }
            pendingKeyPair = keyPair
            pendingNonce = nonce
            val nowMs = System.currentTimeMillis()
            mutableState.value = WatchEnrollTransitions.requestSent(
                code = EnrollCrypto.confirmationCode(pubX962, nonce),
                nowEpochMs = nowMs,
            )
            timeoutJob?.cancel()
            timeoutJob = scope.launch {
                delay(WatchEnrollTransitions.REQUEST_TIMEOUT_MS)
                val next = WatchEnrollTransitions.tick(
                    mutableState.value,
                    System.currentTimeMillis(),
                )
                if (next != mutableState.value) {
                    mutableState.value = next
                    dropPending()
                    mutableMessage.value =
                        "Enrollment timed out — no phone confirmation within 60 seconds"
                }
            }
        }
    }

    /** User cancelled while waiting. */
    fun cancel() {
        timeoutJob?.cancel()
        dropPending()
        mutableState.value = WatchEnrollTransitions.cleared()
        mutableMessage.value = null
    }

    /** Called by the listener service when a RESPONSE message arrives. */
    internal fun onResponse(context: Context, data: ByteArray) {
        ensureInit(context)
        val response = runCatching { EnrollWire.decodeResponse(data) }.getOrNull()
            ?: return
        if (response.schemaVersion != EnrollPaths.SCHEMA_VERSION) return
        if (mutableState.value !is WatchEnrollState.Waiting) return
        timeoutJob?.cancel()
        if (!response.accepted) {
            dropPending()
            mutableState.value = WatchEnrollTransitions.responseReceived(
                mutableState.value, response, Clock.System.now(),
            )
            mutableMessage.value = "The phone declined pairing"
            return
        }
        scope.launch(Dispatchers.IO) {
            val keyPair = pendingKeyPair
            val watchNonce = pendingNonce
            if (keyPair == null || watchNonce == null) {
                // Session material was lost (e.g. process restart) — fail
                // closed rather than persisting an unverifiable enrollment.
                mutableState.value = WatchEnrollState.NotEnrolled
                mutableMessage.value = "Enrollment session expired — try again"
                return@launch
            }
            runCatching {
                val phoneNonce = EnrollCrypto.fromBase64(response.nonce)
                val token = EnrollCrypto.deriveEnrollmentToken(
                    myPrivate = keyPair.private,
                    peerPubKeyX962 = EnrollCrypto.fromBase64(response.phonePubKeyX962),
                    watchNonce = watchNonce,
                    phoneNonce = phoneNonce,
                )
                val now = Clock.System.now()
                store!!.write(
                    WatchPhoneEnrollment(
                        phoneInstallationId = response.phoneInstallationId,
                        tokenBase64 = EnrollCrypto.toBase64(token),
                        enrolledUtc = now,
                    ),
                )
                mutableState.value = WatchEnrollTransitions.responseReceived(
                    mutableState.value, response, now,
                )
            }.onSuccess {
                mutableMessage.value = null
            }.onFailure {
                mutableState.value = WatchEnrollState.NotEnrolled
                mutableMessage.value = "Enrollment could not be completed — try again"
            }
            dropPending()
        }
    }

    /** Called by the listener service when a REVOKE message arrives. */
    internal fun onRevoked(context: Context, data: ByteArray) {
        ensureInit(context)
        val revoke = runCatching { EnrollWire.decodeRevoke(data) }.getOrNull()
            ?: return
        if (revoke.schemaVersion != EnrollPaths.SCHEMA_VERSION) return
        scope.launch(Dispatchers.IO) {
            // Only the enrolled phone may revoke this watch's enrollment.
            if (store?.read()?.phoneInstallationId == revoke.installationId) {
                store?.clear()
                timeoutJob?.cancel()
                dropPending()
                mutableState.value = WatchEnrollState.NotEnrolled
                mutableMessage.value = "The phone removed this enrollment"
            }
        }
    }

    /** "Forget phone": notify reachable nodes, then delete locally. */
    fun forgetPhone(context: Context) {
        ensureInit(context)
        scope.launch(Dispatchers.IO) {
            runCatching {
                val payload = EnrollWire.encodeRevoke(
                    EnrollRevoke(installationId = installationId!!.getOrCreate()),
                )
                val client = Wearable.getMessageClient(context)
                Wearable.getNodeClient(context).connectedNodes.awaitTask()
                    .forEach { node ->
                        runCatching {
                            client.sendMessage(node.id, EnrollPaths.REVOKE, payload)
                                .awaitTask()
                        }
                    }
            }
            store?.clear()
            timeoutJob?.cancel()
            dropPending()
            mutableState.value = WatchEnrollState.NotEnrolled
            mutableMessage.value = "Phone forgotten"
        }
    }

    private fun dropPending() {
        pendingKeyPair = null
        pendingNonce?.fill(0)
        pendingNonce = null
    }
}
