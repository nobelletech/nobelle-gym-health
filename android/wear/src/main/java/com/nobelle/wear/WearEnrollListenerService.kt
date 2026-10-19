package com.nobelle.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.nobelle.core.model.EnrollPaths

/**
 * Receives enrollment RESPONSE/REVOKE messages on the watch, foreground or
 * background. Payloads carry no secrets and are never logged.
 */
class WearEnrollListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            EnrollPaths.RESPONSE ->
                WearEnrollmentCoordinator.onResponse(this, event.data)
            EnrollPaths.REVOKE ->
                WearEnrollmentCoordinator.onRevoked(this, event.data)
        }
    }
}
