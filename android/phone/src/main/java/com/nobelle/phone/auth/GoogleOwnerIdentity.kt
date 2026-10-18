package com.nobelle.phone.auth

import com.nobelle.core.model.OwnerId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.Base64

/**
 * Derives the stable local owner identity from a Google OIDC ID token.
 * The identity is issuer + subject only; the email claim is never read.
 */
object GoogleOwnerIdentity {
    private const val ISSUER_HTTPS = "https://accounts.google.com"
    private const val ISSUER_PLAIN = "accounts.google.com"

    fun fromIdToken(
        idToken: String,
        expectedAudience: String,
        expectedNonce: String,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000,
    ): OwnerId {
        val parts = idToken.split('.')
        require(parts.size == 3) { "Google ID token is malformed" }
        val payload = Base64.getUrlDecoder().decode(parts[1]).decodeToString()
        val claims = Json.parseToJsonElement(payload).jsonObject
        val issuer = claims["iss"]?.jsonPrimitive?.content
        val subject = claims["sub"]?.jsonPrimitive?.content
        val audience = claims["aud"]?.jsonPrimitive?.content
        val nonce = claims["nonce"]?.jsonPrimitive?.content
        val expiresAt = claims["exp"]?.jsonPrimitive?.longOrNull
        require(issuer == ISSUER_HTTPS || issuer == ISSUER_PLAIN) {
            "Unexpected Google identity issuer"
        }
        require(audience == expectedAudience) { "Google identity audience does not match Nobelle" }
        require(!subject.isNullOrBlank()) { "Google identity subject is missing" }
        require(nonce == expectedNonce) { "Google identity nonce does not match this request" }
        require(expiresAt != null && expiresAt > nowEpochSeconds) { "Google identity token is expired" }
        return OwnerId(issuer, subject)
    }
}
