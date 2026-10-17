package com.nobelle.phone.auth

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class DriveAuthorizationController(private val activity: ComponentActivity) {
    sealed interface State {
        data object Disconnected : State
        data object Authorizing : State
        data class Connected(val grantedScopes: Set<String>) : State
        data class Error(val message: String) : State
    }

    private val client = Identity.getAuthorizationClient(activity)
    private val mutableState = MutableStateFlow<State>(State.Disconnected)
    private var token: String? = null

    val state: StateFlow<State> = mutableState

    private val resolutionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            mutableState.value = State.Disconnected
            return@registerForActivityResult
        }
        runCatching { client.getAuthorizationResultFromIntent(result.data!!) }
            .onSuccess(::accept)
            .onFailure { mutableState.value = State.Error(it.message ?: "Google Drive authorization failed") }
    }

    fun authorize() {
        mutableState.value = State.Authorizing
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
            .build()
        client.authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pendingIntent = result.pendingIntent
                    if (pendingIntent == null) {
                        mutableState.value = State.Error("Google authorization required but no resolution was provided")
                    } else {
                        resolutionLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                    }
                } else {
                    accept(result)
                }
            }
            .addOnFailureListener {
                mutableState.value = State.Error(it.message ?: "Google Drive authorization failed")
            }
    }

    fun accessToken(): String = token ?: error("Google Drive is not authorized")

    fun disconnect() {
        token = null
        mutableState.value = State.Disconnected
    }

    private fun accept(result: AuthorizationResult) {
        val granted = result.grantedScopes.toSet()
        val accessToken = result.accessToken
        if (DRIVE_APPDATA_SCOPE !in granted || accessToken.isNullOrBlank()) {
            token = null
            mutableState.value = State.Error("Google did not grant the required Drive app-data permission")
            return
        }
        token = accessToken
        mutableState.value = State.Connected(granted)
    }

    companion object {
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    }
}
