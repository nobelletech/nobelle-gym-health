package com.nobelle.core.coaching

import com.nobelle.core.model.Equipment
import com.nobelle.core.model.ExperienceLevel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    fun everyExerciseHasACanonicalMotionPrimitive() {
        assertEquals(16, MOTION_PRIMITIVES.size)
        catalog.forEach { def ->
            assertTrue("${def.id}: motion must not be blank", def.motion.isNotBlank())
            assertTrue("${def.id}: unknown motion '${def.motion}'", def.motion in MOTION_PRIMITIVES)
        }
        // The raw resource must carry the field explicitly — the model default
        // ("press") exists only for backward-compatible decoding and must not
        // mask a missing "motion" key in the curated file.
        val raw = Json.parseToJsonElement(resourceText()).jsonArray
        raw.forEach { entry ->
            val id = entry.jsonObject.getValue("id").jsonPrimitive.content
            val motion = entry.jsonObject.getValue("motion").jsonPrimitive.content
            assertTrue("$id: raw motion must be canonical", motion in MOTION_PRIMITIVES)
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
    fun loaderRejectsUnknownMotionPrimitive() {
        val bad = """
            [{"id":"a","name":"A","muscleGroups":["chest"],"equipment":"BODYWEIGHT",
             "difficulty":"BEGINNER","instructions":["1","2","3"],
             "commonMistakes":["x"],"motion":"breakdancing",
             "author":"Nobelle reviewed","reviewedUtc":"2024-01-01T00:00:00Z"}]
        """.trimIndent()
        try {
            ExerciseCatalog.load(bad)
            fail("unknown motion primitive must be rejected")
        } catch (expected: IllegalArgumentException) {
        }
        assertEquals(Equipment.BODYWEIGHT, byId["push-up"]?.equipment)
    }

    @Test
    fun loaderRejectsDanglingLinks() {
        val bad = """
            [{"id":"a","name":"A","muscleGroups":["chest"],"equipment":"BODYWEIGHT",
             "difficulty":"BEGINNER","instructions":["1","2","3"],
             "commonMistakes":["x"],"motion":"press","easierAlternativeId":"ghost",
             "author":"Nobelle reviewed","reviewedUtc":"2024-01-01T00:00:00Z"}]
        """.trimIndent()
        try {
            ExerciseCatalog.load(bad)
            fail("dangling easierAlternativeId must be rejected")
        } catch (expected: IllegalArgumentException) {
        }
        assertEquals(Equipment.BODYWEIGHT, byId["push-up"]?.equipment)
    }

    private fun resourceText(): String =
        checkNotNull(javaClass.getResourceAsStream("/exercise_catalog.json")).bufferedReader().readText()
}
