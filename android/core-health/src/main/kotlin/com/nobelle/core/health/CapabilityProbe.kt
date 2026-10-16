package com.nobelle.core.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import com.nobelle.core.model.CapabilityReport
import kotlinx.datetime.Clock

/**
 * Runtime capability probe (M0). Reports what is actually available on the
 * device; never assumes support from vendor marketing (§9).
 */
class CapabilityProbe(private val context: Context) {

    fun probe(deviceId: String): CapabilityReport {
        val status = HealthConnectClient.getSdkStatus(context)
        val available = status == HealthConnectClient.SDK_AVAILABLE
        return CapabilityReport(
            deviceId = deviceId,
            probedUtc = Clock.System.now(),
            healthConnectAvailable = available,
            healthConnectSdk = status.takeIf { it != HealthConnectClient.SDK_AVAILABLE },
            notes = buildList {
                if (!available) add(
                    when (status) {
                        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                            "Health Connect installed but provider update required"
                        else -> "Health Connect not available on this device"
                    }
                )
            },
        )
    }

    fun clientOrNull(): HealthConnectClient? =
        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE)
            HealthConnectClient.getOrCreate(context) else null
}
