package com.nobelle.phone.auth

/**
 * Two distinct Google consent boundaries: sign-in (identity only) first, then
 * a separate Drive app-data grant. The phase of an authorization response is
 * determined by its requested scope, never by mutable controller state, so it
 * survives Activity recreation.
 */
object AuthScopes {
    const val OPENID_SCOPE = "openid"
    const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

    val IDENTITY_SCOPES = listOf(OPENID_SCOPE)
    val DRIVE_SCOPES = listOf(OPENID_SCOPE, DRIVE_APPDATA_SCOPE)

    fun requestsDriveScope(scope: String?): Boolean =
        scope?.split(' ')?.contains(DRIVE_APPDATA_SCOPE) == true
}
