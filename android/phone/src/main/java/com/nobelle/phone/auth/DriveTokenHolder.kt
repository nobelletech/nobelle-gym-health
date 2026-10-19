package com.nobelle.phone.auth

/**
 * Process-wide holder for the current Drive access token so non-UI callers
 * (the watch sync listener, the foreground outbox drain) can use it while it
 * is valid. In memory only — the token is never persisted to disk and never
 * logged. [DriveAuthViewModel] keeps this in sync with its own token field.
 */
object DriveTokenHolder {
    @Volatile
    private var token: String? = null

    fun set(token: String?) {
        this.token = token
    }

    /** The current token, or null when Drive is not connected this process. */
    fun peek(): String? = token
}
