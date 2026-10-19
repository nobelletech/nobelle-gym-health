package com.nobelle.phone.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.nobelle.core.model.OwnerId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class DriveAuthViewModel(application: Application) : AndroidViewModel(application) {
    sealed interface State {
        data object Disconnected : State
        data object AuthenticatingIdentity : State
        data object AuthorizingDrive : State
        data class Connected(
            val ownerId: OwnerId,
            val grantedScopes: Set<String>,
            val diagnosticStatus: String? = null,
            val diagnosticRunning: Boolean = false,
        ) : State
        data class Error(val message: String) : State
    }

    private val ownerStore = EncryptedOwnerStore(application)
    private val mutableState = MutableStateFlow<State>(State.Disconnected)
    private val mutableOwnerId = MutableStateFlow(ownerStore.read())

    /** In-memory only; mirrored into [DriveTokenHolder] so the outbox drain
     *  and the watch listener can use the current grant without UI access. */
    private var token: String? = null
        set(value) {
            field = value
            DriveTokenHolder.set(value)
        }

    val state: StateFlow<State> = mutableState
    val ownerId: StateFlow<OwnerId?> = mutableOwnerId

    /** First consent boundary: Google sign-in only, no Drive scope. */
    fun authenticatingIdentity() {
        mutableState.value = State.AuthenticatingIdentity
    }

    /** Second consent boundary: Drive app-data grant for the established owner. */
    fun authorizingDrive() {
        mutableState.value = State.AuthorizingDrive
    }

    /**
     * Persists the established owner encrypted first; only on success updates
     * the owner flow and advances to the Drive authorization phase.
     */
    fun establishIdentity(owner: OwnerId): Boolean {
        return try {
            ownerStore.write(owner)
            mutableOwnerId.value = owner
            mutableState.value = State.AuthorizingDrive
            true
        } catch (failure: Exception) {
            token = null
            mutableState.value = State.Error("Could not store the sign-in identity on this device; Drive was not connected")
            false
        }
    }

    /**
     * Completes the Drive grant only when the token's owner matches the
     * currently persisted owner. A mismatch retains no token.
     */
    fun connected(accessToken: String, grantedScopes: Set<String>, owner: OwnerId) {
        val persisted = mutableOwnerId.value
        if (persisted == null || persisted.uid != owner.uid) {
            token = null
            mutableState.value = State.Error("Drive was authorized for a different Google account; no token was retained")
            return
        }
        token = accessToken
        mutableState.value = State.Connected(owner, grantedScopes)
    }

    fun error(message: String) {
        token = null
        mutableState.value = State.Error(message)
    }

    fun diagnosticRunning() {
        val connected = mutableState.value as? State.Connected ?: return
        mutableState.value = connected.copy(diagnosticStatus = "Testing upload, read-back and deletion…", diagnosticRunning = true)
    }

    fun diagnosticResult(message: String) {
        val connected = mutableState.value as? State.Connected ?: return
        mutableState.value = connected.copy(diagnosticStatus = message, diagnosticRunning = false)
    }

    /** Disconnects Drive only: clears the in-memory token and grant state, never the owner identity. */
    fun disconnect() {
        token = null
        mutableState.value = State.Disconnected
    }

    /** Explicitly forgets the local owner identity; intentionally not wired to Drive Disconnect. */
    fun forgetLocalOwner() {
        token = null
        ownerStore.clear()
        mutableOwnerId.value = null
        mutableState.value = State.Disconnected
    }

    fun accessToken(): String = token ?: throw IllegalStateException("Google Drive is not authorized")
}
