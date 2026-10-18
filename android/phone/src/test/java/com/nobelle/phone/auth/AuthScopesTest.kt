package com.nobelle.phone.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthScopesTest {
    @Test
    fun `identity request excludes the Drive scope entirely`() {
        assertEquals(listOf("openid"), AuthScopes.IDENTITY_SCOPES)
        assertFalse(AuthScopes.IDENTITY_SCOPES.contains(AuthScopes.DRIVE_APPDATA_SCOPE))
        assertFalse(AuthScopes.requestsDriveScope(AuthScopes.IDENTITY_SCOPES.joinToString(" ")))
    }

    @Test
    fun `drive request contains exactly openid and appdata scopes`() {
        assertEquals(
            listOf("openid", "https://www.googleapis.com/auth/drive.appdata"),
            AuthScopes.DRIVE_SCOPES,
        )
        assertTrue(AuthScopes.requestsDriveScope(AuthScopes.DRIVE_SCOPES.joinToString(" ")))
    }

    @Test
    fun `phase detection ignores absent or unrelated scopes`() {
        assertFalse(AuthScopes.requestsDriveScope(null))
        assertFalse(AuthScopes.requestsDriveScope(""))
        assertFalse(AuthScopes.requestsDriveScope("openid profile"))
        assertTrue(AuthScopes.requestsDriveScope("openid https://www.googleapis.com/auth/drive.appdata"))
    }
}
