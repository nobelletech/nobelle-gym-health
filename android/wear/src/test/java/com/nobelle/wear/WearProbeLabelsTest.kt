package com.nobelle.wear

import org.junit.Assert.assertEquals
import org.junit.Test

class WearProbeLabelsTest {
    private fun capabilities(
        healthServices: Boolean = true,
        error: String? = null,
        phoneNodes: Int? = 0,
    ) = WearCapabilities(
        model = "SM-R000",
        sdkInt = 36,
        healthServices = healthServices,
        measureHeartRate = true,
        exerciseHeartRate = false,
        runningSupported = true,
        runningLocation = false,
        gpsFeature = true,
        vibrator = true,
        phoneNodes = phoneNodes,
        error = error,
    )

    @Test
    fun `supported true reports Supported`() {
        assertEquals(
            "Supported",
            WearProbeLabels.healthRow(capabilities(), supported = true),
        )
    }

    @Test
    fun `supported false reports Not reported — never unsupported`() {
        assertEquals(
            "Not reported",
            WearProbeLabels.healthRow(capabilities(), supported = false),
        )
    }

    @Test
    fun `probe error overrides any value to Probe failed`() {
        val failed = capabilities(error = "ServiceException: binder died")
        assertEquals("Probe failed", WearProbeLabels.healthRow(failed, supported = true))
        assertEquals("Probe failed", WearProbeLabels.healthRow(failed, supported = false))
    }

    @Test
    fun `hardware rows use the same neutral wording without probe failure`() {
        assertEquals("Supported", WearProbeLabels.label(true, probeFailed = false))
        assertEquals("Not reported", WearProbeLabels.label(false, probeFailed = false))
    }

    @Test
    fun `phone nodes render counts honestly and null as probe failure`() {
        assertEquals("0 reachable nodes", WearProbeLabels.nodes(0))
        assertEquals("1 reachable node", WearProbeLabels.nodes(1))
        assertEquals("2 reachable nodes", WearProbeLabels.nodes(2))
        assertEquals("Probe failed", WearProbeLabels.nodes(null))
    }
}
