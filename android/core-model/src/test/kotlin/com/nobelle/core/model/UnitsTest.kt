package com.nobelle.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitsTest {
    private fun close(a: Double, b: Double) = assertEquals(a, b, 0.001)

    @Test
    fun `mass round trips`() {
        close(100.0, Units.lbToKg(Units.kgToLb(100.0)))
        close(2.2046, Units.kgToLb(1.0))
    }

    @Test
    fun `distance round trips`() {
        close(10.0, Units.miToKm(Units.kmToMi(10.0)))
        close(0.6214, Units.kmToMi(1.0))
    }

    @Test
    fun `glucose conversions`() {
        close(5.5, Units.mgDlToMmolL(Units.mmolLToMgDl(5.5)))
        close(99.1001, Units.mmolLToMgDl(5.5))
    }
}
