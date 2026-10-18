package com.nobelle.phone.health

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.health.connect.client.PermissionController

/**
 * Owns the Health Connect permission request launcher. Registered with the
 * activity's result registry like the Drive controller, so a request in
 * flight survives configuration-driven recreation.
 */
class HealthConnectController(
    activity: ComponentActivity,
    private val model: HealthConnectViewModel,
) {
    private val permissionLauncher: ActivityResultLauncher<Set<String>> =
        activity.registerForActivityResult(
            PermissionController.createRequestPermissionResultContract(),
        ) { granted: Set<String> ->
            model.onPermissionResult(granted)
        }

    fun requestSelectedPermissions() {
        if (!model.state.value.available) {
            model.showMessage(
                "Health Connect is not available or needs an update on this device",
            )
            return
        }
        val permissions = model.permissionsToRequest()
        if (permissions.isEmpty()) {
            model.showMessage("Select at least one category first")
            return
        }
        permissionLauncher.launch(permissions)
    }
}
