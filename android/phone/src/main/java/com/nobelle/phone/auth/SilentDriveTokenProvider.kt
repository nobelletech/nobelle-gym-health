package com.nobelle.phone.auth

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import java.io.IOException

/**
 * Obtains a fresh Drive app-data access token for the Google account the
 * Drive consent ran under — without any UI — used only by the background
 * sync worker after the consent marker ([EncryptedOwnerStore]) says the
 * user completed the grant. [accountEmail] comes from the grant's ID-token
 * claim, persisted encrypted at consent time; it builds a local `Account`
 * handle only (the GoogleSignIn SDK is not involved, so no prior GoogleSignIn
 * session is needed). The returned token goes straight to [DriveTokenHolder]
 * — memory only, never persisted, never logged.
 *
 * Returns null — rather than prompting — when there is no recorded account
 * email or when Play Services reports the grant is not silently recoverable
 * (consent not registered for this app/signature, revoked access, transient
 * network failure). Sync then stays deferred until the app is foregrounded
 * and the interactive AppAuth flow can run again.
 *
 * GMS boundary: this calls into Google Play Services SDKs directly and is
 * intentionally thin — it is exercised on-device only, not in JVM tests.
 * Blocking: call from a background thread (the worker does).
 */
object SilentDriveTokenProvider {
    fun refreshToken(context: Context, accountEmail: String?): String? {
        if (accountEmail.isNullOrBlank()) return null
        val account = Account(accountEmail, GOOGLE_ACCOUNT_TYPE)
        return try {
            GoogleAuthUtil.getToken(
                context,
                account,
                "oauth2:${AuthScopes.DRIVE_APPDATA_SCOPE}",
            )
        } catch (failure: UserRecoverableAuthException) {
            null
        } catch (failure: GoogleAuthException) {
            null
        } catch (failure: IOException) {
            null
        }
    }

    private const val GOOGLE_ACCOUNT_TYPE = "com.google"
}
