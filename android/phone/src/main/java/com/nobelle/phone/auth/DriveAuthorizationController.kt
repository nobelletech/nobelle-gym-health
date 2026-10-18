package com.nobelle.phone.auth

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.nobelle.phone.BuildConfig
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues

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
        authorizationService.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, tokenError ->
            val accessToken = tokenResponse?.accessToken
            if (accessToken.isNullOrBlank()) {
                model.error(tokenError?.errorDescription ?: "Google did not return a Drive access token")
            } else {
                model.connected(accessToken, setOf(DRIVE_APPDATA_SCOPE))
            }
        }
    }

    fun authorize() {
        model.authorizing()
        val configuration = AuthorizationServiceConfiguration(
            Uri.parse("https://accounts.google.com/o/oauth2/v2/auth"),
            Uri.parse("https://oauth2.googleapis.com/token"),
        )
        val clientPrefix = BuildConfig.GOOGLE_ANDROID_CLIENT_ID.removeSuffix(".apps.googleusercontent.com")
        val redirectUri = Uri.parse("com.googleusercontent.apps.$clientPrefix:/oauth2redirect")
        val request = AuthorizationRequest.Builder(
            configuration,
            BuildConfig.GOOGLE_ANDROID_CLIENT_ID,
            ResponseTypeValues.CODE,
            redirectUri,
        ).setScopes(DRIVE_APPDATA_SCOPE)
            .setPrompt("select_account consent")
            .build()
        authorizationLauncher.launch(authorizationService.getAuthorizationRequestIntent(request))
    }

    fun accessToken(): String = model.accessToken()

    fun disconnect() = model.disconnect()

    companion object {
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    }
}
