package com.nobelle.phone.auth

import android.net.Uri
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.nobelle.core.model.OwnerId
import com.nobelle.phone.BuildConfig
import java.security.SecureRandom
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues

/**
 * Runs two sequential AppAuth authorization-code + PKCE requests so Google
 * sign-in and the Drive app-data grant are separate consent boundaries.
 * The response phase is derived from the request's scope, not controller
 * fields, so it survives Activity recreation.
 */
class DriveAuthorizationController(
    private val activity: ComponentActivity,
    private val model: DriveAuthViewModel,
) {
    private val authorizationService = AuthorizationService(activity)

    private val authorizationLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val response = data?.let(AuthorizationResponse::fromIntent)
        val authorizationError = data?.let(AuthorizationException::fromIntent)
        if (response == null) {
            val detail = authorizationError?.let {
                listOfNotNull(it.error, it.errorDescription, "type=${it.type}", "code=${it.code}").joinToString("; ")
            }
            model.error(detail ?: "Browser authorization was canceled before Google returned a result")
            return@registerForActivityResult
        }
        if (AuthScopes.requestsDriveScope(response.request.scope)) {
            exchangeDriveGrant(response)
        } else {
            exchangeIdentity(response)
        }
    }

    /** Phase 1: Google sign-in only — no Drive scope in this request. */
    fun authorize() {
        model.authenticatingIdentity()
        authorizationLauncher.launch(
            authorizationService.getAuthorizationRequestIntent(identityRequest())
        )
    }

    /** Phase 2: separate Drive app-data grant for the already-established owner. */
    private fun authorizeDrive() {
        model.authorizingDrive()
        authorizationLauncher.launch(
            authorizationService.getAuthorizationRequestIntent(driveRequest())
        )
    }

    private fun exchangeIdentity(response: AuthorizationResponse) {
        authorizationService.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, _ ->
            val idToken = tokenResponse?.idToken
            if (idToken.isNullOrBlank()) {
                model.error("Google did not return an identity token for the signed-in account")
                return@performTokenRequest
            }
            val owner = parseOwner(idToken, response) ?: return@performTokenRequest
            if (!model.establishIdentity(owner)) return@performTokenRequest
            // The second consent screen must be launched from the UI thread.
            activity.runOnUiThread { authorizeDrive() }
        }
    }

    private fun exchangeDriveGrant(response: AuthorizationResponse) {
        authorizationService.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, tokenError ->
            val accessToken = tokenResponse?.accessToken
            if (accessToken.isNullOrBlank()) {
                model.error(tokenError?.errorDescription ?: "Google did not return a Drive access token")
                return@performTokenRequest
            }
            val idToken = tokenResponse?.idToken
            if (idToken.isNullOrBlank()) {
                model.error("Google did not return an identity token for the Drive authorization")
                return@performTokenRequest
            }
            val owner = parseOwner(idToken, response) ?: return@performTokenRequest
            val expectedOwner = model.ownerId.value
            if (expectedOwner == null) {
                model.error("No Google identity is established on this device; Drive was not connected")
                return@performTokenRequest
            }
            if (owner.uid != expectedOwner.uid) {
                model.error("Drive was authorized for a different Google account; no token was retained")
                return@performTokenRequest
            }
            model.connected(accessToken, setOf(AuthScopes.DRIVE_APPDATA_SCOPE), owner)
        }
    }

    private fun parseOwner(idToken: String, response: AuthorizationResponse): OwnerId? {
        val expectedNonce = response.request.nonce
        if (expectedNonce.isNullOrBlank()) {
            model.error("The authorization request was missing its identity nonce")
            return null
        }
        return try {
            GoogleOwnerIdentity.fromIdToken(
                idToken = idToken,
                expectedAudience = BuildConfig.GOOGLE_ANDROID_CLIENT_ID,
                expectedNonce = expectedNonce,
            )
        } catch (failure: Exception) {
            model.error("Google identity token failed validation: ${failure.message ?: "invalid claims"}")
            null
        }
    }

    private fun identityRequest(): AuthorizationRequest = baseRequest()
        .setScopes(*AuthScopes.IDENTITY_SCOPES.toTypedArray())
        .setNonce(newNonce())
        .setPrompt("select_account")
        .build()

    private fun driveRequest(): AuthorizationRequest = baseRequest()
        .setScopes(*AuthScopes.DRIVE_SCOPES.toTypedArray())
        .setNonce(newNonce())
        .setPrompt("consent")
        .build()

    private fun baseRequest(): AuthorizationRequest.Builder {
        val configuration = AuthorizationServiceConfiguration(
            Uri.parse("https://accounts.google.com/o/oauth2/v2/auth"),
            Uri.parse("https://oauth2.googleapis.com/token"),
        )
        val clientPrefix = BuildConfig.GOOGLE_ANDROID_CLIENT_ID.removeSuffix(".apps.googleusercontent.com")
        val redirectUri = Uri.parse("com.googleusercontent.apps.$clientPrefix:/oauth2redirect")
        return AuthorizationRequest.Builder(
            configuration,
            BuildConfig.GOOGLE_ANDROID_CLIENT_ID,
            ResponseTypeValues.CODE,
            redirectUri,
        )
    }

    fun accessToken(): String = model.accessToken()

    fun disconnect() = model.disconnect()

    companion object {
        private fun newNonce(): String {
            val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
            return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }
    }
}
