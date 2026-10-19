package com.nobelle.phone.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualEntryInputParserTest {
    @Test
    fun `accepts valid weight input defaulting to now`() {
        val result = ManualEntryInputParser.parse(
            marker = "weight",
            value = "80.5",
            minutesAgo = "0",
        )
        assertTrue(result is ManualEntryInputParser.Result.Ok)
        val input = (result as ManualEntryInputParser.Result.Ok).input
        assertEquals("weight", input.marker)
        assertEquals(80.5, input.value, 0.0001)
        assertEquals(0, input.minutesAgo)
    }

    @Test
    fun `accepts offset measured time in minutes`() {
        val result = ManualEntryInputParser.parse(
            marker = "resting_heart_rate",
            value = "62",
            minutesAgo = "30",
        )
        assertTrue(result is ManualEntryInputParser.Result.Ok)
        assertEquals(30, (result as ManualEntryInputParser.Result.Ok).input.minutesAgo)
    }

    @Test
    fun `rejects unknown marker`() {
        val result = ManualEntryInputParser.parse(
            marker = "unknown_marker",
            value = "10",
            minutesAgo = "0",
        )
        assertTrue(result is ManualEntryInputParser.Result.Invalid)
    }

    @Test
    fun `rejects non-numeric value`() {
        val result = ManualEntryInputParser.parse(
            marker = "weight",
            value = "eighty",
            minutesAgo = "0",
        )
        assertTrue(result is ManualEntryInputParser.Result.Invalid)
    }

    @Test
    fun `rejects value below marker minimum`() {
        val result = ManualEntryInputParser.parse(
            marker = "weight",
            value = "-5",
            minutesAgo = "0",
        )
        assertTrue(result is ManualEntryInputParser.Result.Invalid)
    }

    @Test
    fun `rejects value above marker maximum`() {
        val result = ManualEntryInputParser.parse(
            marker = "resting_heart_rate",
            value = "400",
            minutesAgo = "0",
        )
        assertTrue(result is ManualEntryInputParser.Result.Invalid)
    }

    @Test
    fun `rejects negative minutes ago`() {
        val result = ManualEntryInputParser.parse(
            marker = "weight",
            value = "80",
            minutesAgo = "-1",
        )
        assertTrue(result is ManualEntryInputParser.Result.Invalid)
    }

    @Test
    fun `rejects minutes ago too far in the past`() {
        val result = ManualEntryInputParser.parse(
            marker = "weight",
            value = "80",
            minutesAgo = "999999",
        )
        assertTrue(result is ManualEntryInputParser.Result.Invalid)
    }

    @Test
    fun `allows zero value for steps`() {
        val result = ManualEntryInputParser.parse(
            marker = "steps",
            value = "0",
            minutesAgo = "0",
        )
        assertTrue(result is ManualEntryInputParser.Result.Ok)
        assertEquals(0.0, (result as ManualEntryInputParser.Result.Ok).input.value, 0.0001)
    }
}
