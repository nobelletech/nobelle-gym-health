package com.nobelle.phone.auth

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class DriveAuthViewModel : ViewModel() {
    sealed interface State {
        data object Disconnected : State
        data object Authorizing : State
        data class Connected(
            val grantedScopes: Set<String>,
            val diagnosticStatus: String? = null,
            val diagnosticRunning: Boolean = false,
        ) : State
        data class Error(val message: String) : State
    }

    private val mutableState = MutableStateFlow<State>(State.Disconnected)
    private var token: String? = null

    val state: StateFlow<State> = mutableState

    fun authorizing() {
        mutableState.value = State.Authorizing
    }

    fun connected(accessToken: String, grantedScopes: Set<String>) {
        token = accessToken
        mutableState.value = State.Connected(grantedScopes)
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

    fun disconnect() {
        token = null
        mutableState.value = State.Disconnected
    }

    fun accessToken(): String = token ?: throw IllegalStateException("Google Drive is not authorized")
}
