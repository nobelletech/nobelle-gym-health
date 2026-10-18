package com.nobelle.phone.gym

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GymCategoryCatalogTest {
    private val required = listOf(
        "Bodybuilding", "Cardio", "Circuit Training", "Cross-Training",
        "Group Class", "HIIT", "Martial Arts", "Mobility", "Personal Training",
        "Pilates", "Recovery", "Sauna", "Sports", "Strength", "Stretching",
        "Swimming", "Yoga",
    )

    @Test
    fun `catalog contains every required category`() {
        assertEquals(required.toSet(), GymCategoryCatalog.all.toSet())
    }

    @Test
    fun `categories are unique and globally alphabetical`() {
        val lowered = GymCategoryCatalog.all.map { it.lowercase() }
        assertEquals(lowered.size, lowered.distinct().size)
        GymCategoryCatalog.all.zipWithNext().forEach { (a, b) ->
            assertTrue("$a should sort before $b", a.lowercase() <= b.lowercase())
        }
    }

    @Test
    fun `search is case-insensitive contains and blank returns all`() {
        assertEquals(GymCategoryCatalog.all, GymCategoryCatalog.search(""))
        assertEquals(GymCategoryCatalog.all, GymCategoryCatalog.search("   "))
        assertEquals(listOf("Strength"), GymCategoryCatalog.search("STRENGTH"))
        assertTrue(GymCategoryCatalog.search("train").containsAll(listOf("Circuit Training", "Cross-Training", "Personal Training")))
        GymCategoryCatalog.search("train").zipWithNext().forEach { (a, b) ->
            assertTrue(a.lowercase() <= b.lowercase())
        }
        assertTrue(GymCategoryCatalog.search("no such thing").isEmpty())
    }
}
