package com.nobelle.core.coaching

import com.nobelle.core.model.Equipment
import com.nobelle.core.model.ExperienceLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.fail

class ExerciseCatalogTest {
    private val catalog = ExerciseCatalog.all()
    private val byId = ExerciseCatalog.byId()

    @Test
    fun bundledCatalogParsesAndIsCurated() {
        assertTrue(catalog.size >= 40)
        assertTrue(catalog.all { it.author == ExerciseCatalog.REVIEWED_AUTHOR })
        assertTrue(catalog.all { it.instructions.size in 3..6 })
        assertTrue(catalog.all { it.commonMistakes.isNotEmpty() })
        // No demonstration media yet — §5 placeholder rule keeps media out.
        assertTrue(catalog.all { it.demonstration == null })
        // Coverage: major muscle groups, equipment variety, every level.
        val muscles = catalog.flatMap { it.muscleGroups }.toSet()
        listOf("chest", "lats", "quads", "side delts", "biceps", "triceps", "core").forEach {
            assertTrue("missing muscle coverage: $it", it in muscles)
        }
        assertTrue(catalog.map { it.equipment }.toSet().size >= 4)
        ExperienceLevel.entries.forEach { level ->
            assertTrue(catalog.any { it.difficulty == level })
        }
    }

    @Test
    fun everyAlternativeLinkResolves() {
        catalog.forEach { def ->
            def.easierAlternativeId?.let { assertNotNull(byId[it]) }
            def.harderAlternativeId?.let { assertNotNull(byId[it]) }
        }
        assertTrue(catalog.any { it.easierAlternativeId != null })
        assertTrue(catalog.any { it.harderAlternativeId != null })
    }

    @Test
    fun loaderRejectsDanglingLinks() {
        val bad = """
            [{"id":"a","name":"A","muscleGroups":["chest"],"equipment":"BODYWEIGHT",
             "difficulty":"BEGINNER","instructions":["1","2","3"],
             "commonMistakes":["x"],"easierAlternativeId":"ghost",
             "author":"Nobelle reviewed","reviewedUtc":"2024-01-01T00:00:00Z"}]
        """.trimIndent()
        try {
            ExerciseCatalog.load(bad)
            fail("dangling easierAlternativeId must be rejected")
        } catch (expected: IllegalArgumentException) {
        }
        assertEquals(Equipment.BODYWEIGHT, byId["push-up"]?.equipment)
    }
}
