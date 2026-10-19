package com.nobelle.phone.auth

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleOwnerIdentityTest {
    private val now = 1_700_000_000L
    private val notExpired = now + 600

    @Test
    fun `uses issuer and subject and never email`() {
        val token = token(
            """{"iss":"https://accounts.google.com","sub":"stable-123","aud":"client-id",""" +
                """"nonce":"nonce-1","exp":$notExpired,"email":"person@example.com"}"""
        )

        val owner = GoogleOwnerIdentity.fromIdToken(token, "client-id", "nonce-1", now)

        assertEquals("https://accounts.google.com:stable-123", owner.uid)
        assertFalse(owner.uid.contains("person@example.com"))
    }

    @Test
    fun `grant returns email claim separately from the owner identity`() {
        val token = token(
            """{"iss":"https://accounts.google.com","sub":"stable-123","aud":"client-id",""" +
                """"nonce":"nonce-1","exp":$notExpired,"email":"person@example.com"}"""
        )

        val grant = GoogleOwnerIdentity.grantFromIdToken(token, "client-id", "nonce-1", now)

        assertEquals("https://accounts.google.com:stable-123", grant.owner.uid)
        assertEquals("person@example.com", grant.accountEmail)
        assertFalse(grant.owner.uid.contains(grant.accountEmail!!))
    }

    @Test
    fun `grant tolerates a missing email claim`() {
        val token = token(
            """{"iss":"accounts.google.com","sub":"stable-123","aud":"client-id","nonce":"nonce-1","exp":$notExpired}"""
        )

        val grant = GoogleOwnerIdentity.grantFromIdToken(token, "client-id", "nonce-1", now)

        assertEquals("accounts.google.com:stable-123", grant.owner.uid)
        assertNull(grant.accountEmail)
    }

    @Test
    fun `accepts the plain accounts dot google dot com issuer`() {
        val token = token(
            """{"iss":"accounts.google.com","sub":"stable-123","aud":"client-id","nonce":"nonce-1","exp":$notExpired}"""
        )

        val owner = GoogleOwnerIdentity.fromIdToken(token, "client-id", "nonce-1", now)

        assertEquals("accounts.google.com:stable-123", owner.uid)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects wrong audience`() {
        GoogleOwnerIdentity.fromIdToken(
            token("""{"iss":"accounts.google.com","sub":"s","aud":"other","nonce":"nonce-1","exp":$notExpired}"""),
            "client-id",
            "nonce-1",
            now,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects wrong nonce`() {
        GoogleOwnerIdentity.fromIdToken(
            token("""{"iss":"accounts.google.com","sub":"s","aud":"client-id","nonce":"other-nonce","exp":$notExpired}"""),
            "client-id",
            "nonce-1",
            now,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects expired token`() {
        GoogleOwnerIdentity.fromIdToken(
            token("""{"iss":"accounts.google.com","sub":"s","aud":"client-id","nonce":"nonce-1","exp":${now - 1}}"""),
            "client-id",
            "nonce-1",
            now,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects missing subject`() {
        GoogleOwnerIdentity.fromIdToken(
            token("""{"iss":"accounts.google.com","aud":"client-id","nonce":"nonce-1","exp":$notExpired}"""),
            "client-id",
            "nonce-1",
            now,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects wrong issuer`() {
        GoogleOwnerIdentity.fromIdToken(
            token("""{"iss":"https://evil.example.com","sub":"s","aud":"client-id","nonce":"nonce-1","exp":$notExpired}"""),
            "client-id",
            "nonce-1",
            now,
        )
    }

    private fun token(payload: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        return "${encoder.encodeToString("{}".encodeToByteArray())}.${encoder.encodeToString(payload.encodeToByteArray())}.signature"
    }
}
