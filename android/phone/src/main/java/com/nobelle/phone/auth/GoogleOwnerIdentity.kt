package com.nobelle.phone.auth

import com.nobelle.core.model.OwnerId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.Base64

/**
 * Derives the stable local owner identity from a Google OIDC ID token.
 * The identity is issuer + subject only; the email claim is captured
 * separately for token minting and never becomes part of the identity.
 */
object GoogleOwnerIdentity {
    private const val ISSUER_HTTPS = "https://accounts.google.com"
    private const val ISSUER_PLAIN = "accounts.google.com"

    /**
     * Validated Drive-grant claims: the stable owner identity plus the Google
     * account email the grant ran under. The email is used only to build a
     * local `Account` for a silent token refresh — it is never an identity,
     * never logged, never sent anywhere.
     */
    data class DriveGrant(val owner: OwnerId, val accountEmail: String?)

    fun fromIdToken(
        idToken: String,
        expectedAudience: String,
        expectedNonce: String,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000,
    ): OwnerId = grantFromIdToken(idToken, expectedAudience, expectedNonce, nowEpochSeconds).owner

    /**
     * Same validation as [fromIdToken]; additionally returns the token's
     * `email` claim when the grant carried the `email` scope (absent → null,
     * which callers treat as "no account to refresh silently for").
     */
    fun grantFromIdToken(
        idToken: String,
        expectedAudience: String,
        expectedNonce: String,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000,
    ): DriveGrant {
        val parts = idToken.split('.')
        require(parts.size == 3) { "Google ID token is malformed" }
        val payload = Base64.getUrlDecoder().decode(parts[1]).decodeToString()
        val claims = Json.parseToJsonElement(payload).jsonObject
        val issuer = claims["iss"]?.jsonPrimitive?.content
        val subject = claims["sub"]?.jsonPrimitive?.content
        val audience = claims["aud"]?.jsonPrimitive?.content
        val nonce = claims["nonce"]?.jsonPrimitive?.content
        val expiresAt = claims["exp"]?.jsonPrimitive?.longOrNull
        val email = claims["email"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        require(issuer == ISSUER_HTTPS || issuer == ISSUER_PLAIN) {
            "Unexpected Google identity issuer"
        }
        require(audience == expectedAudience) { "Google identity audience does not match Nobelle" }
        require(!subject.isNullOrBlank()) { "Google identity subject is missing" }
        require(nonce == expectedNonce) { "Google identity nonce does not match this request" }
        require(expiresAt != null && expiresAt > nowEpochSeconds) { "Google identity token is expired" }
        return DriveGrant(OwnerId(issuer, subject), email)
    }
}
