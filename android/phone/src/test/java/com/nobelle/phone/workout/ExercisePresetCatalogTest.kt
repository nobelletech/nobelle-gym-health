package com.nobelle.phone.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExercisePresetCatalogTest {
    private val requiredNames = listOf(
        "Barbell Bench Press", "Dumbbell Bench Press", "Incline Barbell Bench Press",
        "Incline Dumbbell Press", "Decline Bench Press", "Machine Chest Press",
        "Push-Up", "Knee Push-Up", "Cable Chest Fly", "Dumbbell Chest Fly", "Pec Deck",
        "Conventional Deadlift", "Romanian Deadlift", "Barbell Bent-Over Row",
        "One-Arm Dumbbell Row", "Seated Cable Row", "Chest-Supported Row",
        "Lat Pulldown", "Assisted Pull-Up", "Pull-Up", "Chin-Up",
        "Straight-Arm Pulldown", "Face Pull",
        "Back Squat", "Front Squat", "Goblet Squat", "Leg Press",
        "Bulgarian Split Squat", "Reverse Lunge", "Walking Lunge", "Step-Up",
        "Hip Thrust", "Glute Bridge", "Leg Extension", "Seated Leg Curl",
        "Lying Leg Curl", "Standing Calf Raise", "Seated Calf Raise",
        "Cable Kickback", "Hip Abduction Machine", "Hip Adduction Machine",
        "Overhead Barbell Press", "Seated Dumbbell Shoulder Press", "Arnold Press",
        "Machine Shoulder Press", "Dumbbell Lateral Raise", "Cable Lateral Raise",
        "Dumbbell Front Raise", "Reverse Pec Deck", "Dumbbell Rear-Delt Fly",
        "Barbell Shrug", "Dumbbell Shrug",
        "Barbell Curl", "Dumbbell Curl", "Hammer Curl", "Incline Dumbbell Curl",
        "Preacher Curl", "Cable Curl", "Triceps Pushdown",
        "Overhead Cable Triceps Extension", "Skull Crusher", "Close-Grip Bench Press",
        "Assisted Dip", "Dip", "Dumbbell Triceps Kickback", "Wrist Curl", "Reverse Wrist Curl",
        "Plank", "Side Plank", "Dead Bug", "Bird Dog", "Hollow Hold", "Crunch",
        "Reverse Crunch", "Bicycle Crunch", "Hanging Knee Raise", "Hanging Leg Raise",
        "Cable Crunch", "Russian Twist", "Pallof Press", "Mountain Climber",
        "Treadmill Walk", "Treadmill Run", "Outdoor Walk", "Outdoor Run",
        "Stationary Bike", "Rowing Machine", "Elliptical", "Stair Climber",
        "Jump Rope", "Battle Rope", "Kettlebell Swing", "Burpee", "Sled Push",
        "Cat-Cow", "Child's Pose", "Hip Flexor Stretch", "Hamstring Stretch",
        "Calf Stretch", "Figure-Four Stretch", "Thoracic Rotation",
        "Shoulder Wall Slide", "Band Pull-Apart", "Bodyweight Squat",
        "World's Greatest Stretch",
    )

    @Test
    fun `catalog has at least 100 entries and unique names`() {
        assertTrue(ExercisePresetCatalog.all.size >= 100)
        val names = ExercisePresetCatalog.all.map { it.name.lowercase() }
        assertEquals(names.size, names.distinct().size)
    }

    @Test
    fun `every required exercise name is present`() {
        val names = ExercisePresetCatalog.all.map { it.name }.toSet()
        val missing = requiredNames.filter { it !in names }
        assertTrue("Missing presets: $missing", missing.isEmpty())
    }

    @Test
    fun `blank search returns all in global alphabetical order`() {
        val results = ExercisePresetCatalog.search("")
        assertEquals(ExercisePresetCatalog.all, results)
        assertEquals(ExercisePresetCatalog.search("   "), results)
        results.zipWithNext().forEach { (a, b) ->
            assertTrue(
                "${a.name} should sort before ${b.name}",
                a.name.lowercase() <= b.name.lowercase(),
            )
        }
    }

    @Test
    fun `filtered search preserves global alphabetical order`() {
        listOf("press", "curl", "squat", "machine").forEach { query ->
            ExercisePresetCatalog.search(query).zipWithNext().forEach { (a, b) ->
                assertTrue(
                    "$query: ${a.name} should sort before ${b.name}",
                    a.name.lowercase() <= b.name.lowercase(),
                )
            }
        }
    }

    @Test
    fun `search matches name category and equipment case-insensitively`() {
        assertTrue(ExercisePresetCatalog.search("BENCH").any { it.name == "Barbell Bench Press" })
        assertTrue(ExercisePresetCatalog.search("dumbbell bench").single().name == "Dumbbell Bench Press")
        val byCategory = ExercisePresetCatalog.search("core")
        assertTrue(byCategory.isNotEmpty() && byCategory.all { it.category == "Core" })
        val byEquipment = ExercisePresetCatalog.search("CABLE")
        assertTrue(byEquipment.isNotEmpty() && byEquipment.all { it.equipment == "Cable" })
        assertTrue(ExercisePresetCatalog.search("no such exercise").isEmpty())
    }

    @Test
    fun `presets have exactly one of reps or duration and sane values`() {
        ExercisePresetCatalog.all.forEach { preset ->
            assertTrue(
                "${preset.name} must have exactly one of reps/duration",
                (preset.reps == null) != (preset.durationSec == null),
            )
            preset.reps?.let { assertTrue(it > 0) }
            preset.durationSec?.let { assertTrue(it > 0) }
            assertTrue(preset.restSec > 0)
            preset.rpe?.let { assertTrue(it in 0.0..10.0) }
            preset.rir?.let { assertTrue(it >= 0.0) }
        }
    }

    @Test
    fun `presets never carry a load suggestion`() {
        // The model has no load field by design; reflectively assert it stays absent.
        assertNull(ExercisePreset::class.java.declaredFields.firstOrNull { it.name.contains("load", ignoreCase = true) })
    }
}
