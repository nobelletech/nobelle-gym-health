package com.nobelle.phone.workout

import com.nobelle.core.model.SetType

/**
 * Convenience defaults for common exercises. Presets deliberately carry no
 * load value — the user always chooses a load they already know they can use
 * safely. These are not personalized prescriptions.
 */
data class ExercisePreset(
    val name: String,
    val category: String,
    val equipment: String,
    val reps: Int? = null,
    val durationSec: Int? = null,
    val restSec: Int,
    val rpe: Double? = null,
    val rir: Double? = null,
    val tempo: String? = null,
    val setType: SetType = SetType.STANDARD,
)

object ExercisePresetCatalog {
    private fun strength(name: String, category: String, equipment: String) =
        ExercisePreset(name, category, equipment, reps = 8, restSec = 120, rpe = 7.0, rir = 3.0, tempo = "3-1-1")

    private fun resistance(name: String, category: String, equipment: String) =
        ExercisePreset(name, category, equipment, reps = 10, restSec = 90, rpe = 7.0, rir = 3.0, tempo = "2-1-2")

    private fun isolation(name: String, category: String, equipment: String) =
        ExercisePreset(name, category, equipment, reps = 12, restSec = 60, rpe = 7.0, rir = 3.0, tempo = "2-1-2")

    private fun bodyweight(name: String, category: String, equipment: String = "Bodyweight") =
        ExercisePreset(name, category, equipment, reps = 10, restSec = 60, rpe = 6.0, rir = 3.0, tempo = "2-1-2")

    private fun hold(name: String, category: String, equipment: String = "Bodyweight") =
        ExercisePreset(name, category, equipment, durationSec = 30, restSec = 30, rpe = 5.0, setType = SetType.TIMED)

    private fun stretch(name: String, category: String) =
        ExercisePreset(name, category, "Bodyweight", durationSec = 30, restSec = 15, rpe = 3.0, setType = SetType.TIMED)

    private fun cardio(name: String, equipment: String) =
        ExercisePreset(name, "Conditioning", equipment, durationSec = 300, restSec = 60, rpe = 6.0, setType = SetType.TIMED)

    private fun interval(name: String, category: String, equipment: String) =
        ExercisePreset(name, category, equipment, durationSec = 30, restSec = 60, rpe = 7.0, setType = SetType.TIMED)

    private fun warmup(name: String, category: String, equipment: String = "Bodyweight") =
        ExercisePreset(name, category, equipment, reps = 12, restSec = 30, rpe = 4.0, rir = 4.0, tempo = "2-1-2", setType = SetType.WARM_UP)

    val all: List<ExercisePreset> = listOf(
        // Chest
        strength("Barbell Bench Press", "Chest", "Barbell"),
        strength("Incline Barbell Bench Press", "Chest", "Barbell"),
        strength("Decline Bench Press", "Chest", "Barbell"),
        resistance("Dumbbell Bench Press", "Chest", "Dumbbell"),
        resistance("Incline Dumbbell Press", "Chest", "Dumbbell"),
        resistance("Machine Chest Press", "Chest", "Machine"),
        bodyweight("Push-Up", "Chest"),
        bodyweight("Knee Push-Up", "Chest"),
        isolation("Cable Chest Fly", "Chest", "Cable"),
        isolation("Dumbbell Chest Fly", "Chest", "Dumbbell"),
        isolation("Pec Deck", "Chest", "Machine"),
        // Back
        strength("Conventional Deadlift", "Back", "Barbell"),
        strength("Romanian Deadlift", "Back", "Barbell"),
        strength("Barbell Bent-Over Row", "Back", "Barbell"),
        resistance("One-Arm Dumbbell Row", "Back", "Dumbbell"),
        resistance("Seated Cable Row", "Back", "Cable"),
        resistance("Chest-Supported Row", "Back", "Machine"),
        resistance("Lat Pulldown", "Back", "Cable"),
        resistance("Assisted Pull-Up", "Back", "Machine"),
        bodyweight("Pull-Up", "Back"),
        bodyweight("Chin-Up", "Back"),
        isolation("Straight-Arm Pulldown", "Back", "Cable"),
        isolation("Face Pull", "Back", "Cable"),
        // Legs
        strength("Back Squat", "Legs", "Barbell"),
        strength("Front Squat", "Legs", "Barbell"),
        resistance("Goblet Squat", "Legs", "Dumbbell"),
        resistance("Leg Press", "Legs", "Machine"),
        resistance("Bulgarian Split Squat", "Legs", "Dumbbell"),
        resistance("Reverse Lunge", "Legs", "Dumbbell"),
        resistance("Walking Lunge", "Legs", "Dumbbell"),
        resistance("Step-Up", "Legs", "Dumbbell"),
        isolation("Leg Extension", "Legs", "Machine"),
        isolation("Seated Leg Curl", "Legs", "Machine"),
        isolation("Lying Leg Curl", "Legs", "Machine"),
        isolation("Standing Calf Raise", "Legs", "Machine"),
        isolation("Seated Calf Raise", "Legs", "Machine"),
        // Glutes
        strength("Hip Thrust", "Glutes", "Barbell"),
        bodyweight("Glute Bridge", "Glutes"),
        isolation("Cable Kickback", "Glutes", "Cable"),
        isolation("Hip Abduction Machine", "Glutes", "Machine"),
        isolation("Hip Adduction Machine", "Glutes", "Machine"),
        // Shoulders
        strength("Overhead Barbell Press", "Shoulders", "Barbell"),
        resistance("Seated Dumbbell Shoulder Press", "Shoulders", "Dumbbell"),
        resistance("Arnold Press", "Shoulders", "Dumbbell"),
        resistance("Machine Shoulder Press", "Shoulders", "Machine"),
        isolation("Dumbbell Lateral Raise", "Shoulders", "Dumbbell"),
        isolation("Cable Lateral Raise", "Shoulders", "Cable"),
        isolation("Dumbbell Front Raise", "Shoulders", "Dumbbell"),
        isolation("Reverse Pec Deck", "Shoulders", "Machine"),
        isolation("Dumbbell Rear-Delt Fly", "Shoulders", "Dumbbell"),
        isolation("Barbell Shrug", "Shoulders", "Barbell"),
        isolation("Dumbbell Shrug", "Shoulders", "Dumbbell"),
        // Arms
        strength("Close-Grip Bench Press", "Arms", "Barbell"),
        resistance("Assisted Dip", "Arms", "Machine"),
        bodyweight("Dip", "Arms"),
        isolation("Barbell Curl", "Arms", "Barbell"),
        isolation("Dumbbell Curl", "Arms", "Dumbbell"),
        isolation("Hammer Curl", "Arms", "Dumbbell"),
        isolation("Incline Dumbbell Curl", "Arms", "Dumbbell"),
        isolation("Preacher Curl", "Arms", "Barbell"),
        isolation("Cable Curl", "Arms", "Cable"),
        isolation("Triceps Pushdown", "Arms", "Cable"),
        isolation("Overhead Cable Triceps Extension", "Arms", "Cable"),
        isolation("Skull Crusher", "Arms", "Barbell"),
        isolation("Dumbbell Triceps Kickback", "Arms", "Dumbbell"),
        isolation("Wrist Curl", "Arms", "Dumbbell"),
        isolation("Reverse Wrist Curl", "Arms", "Dumbbell"),
        // Core
        hold("Plank", "Core"),
        hold("Side Plank", "Core"),
        hold("Hollow Hold", "Core"),
        hold("Pallof Press", "Core", "Cable"),
        bodyweight("Dead Bug", "Core"),
        bodyweight("Bird Dog", "Core"),
        bodyweight("Crunch", "Core"),
        bodyweight("Reverse Crunch", "Core"),
        bodyweight("Bicycle Crunch", "Core"),
        bodyweight("Hanging Knee Raise", "Core"),
        bodyweight("Hanging Leg Raise", "Core"),
        bodyweight("Russian Twist", "Core"),
        isolation("Cable Crunch", "Core", "Cable"),
        interval("Mountain Climber", "Core", "Bodyweight"),
        // Conditioning
        cardio("Treadmill Walk", "Treadmill"),
        cardio("Treadmill Run", "Treadmill"),
        cardio("Outdoor Walk", "Outdoor"),
        cardio("Outdoor Run", "Outdoor"),
        cardio("Stationary Bike", "Bike"),
        cardio("Rowing Machine", "Rower"),
        cardio("Elliptical", "Elliptical"),
        cardio("Stair Climber", "Stair climber"),
        interval("Jump Rope", "Conditioning", "Jump rope"),
        interval("Battle Rope", "Conditioning", "Battle ropes"),
        interval("Burpee", "Conditioning", "Bodyweight"),
        interval("Sled Push", "Conditioning", "Sled"),
        ExercisePreset(
            "Kettlebell Swing", "Conditioning", "Kettlebell",
            reps = 10, restSec = 60, rpe = 7.0,
        ),
        // Mobility / recovery
        stretch("Cat-Cow", "Mobility"),
        stretch("Child's Pose", "Mobility"),
        stretch("Hip Flexor Stretch", "Mobility"),
        stretch("Hamstring Stretch", "Mobility"),
        stretch("Calf Stretch", "Mobility"),
        stretch("Figure-Four Stretch", "Mobility"),
        stretch("Thoracic Rotation", "Mobility"),
        stretch("World's Greatest Stretch", "Mobility"),
        warmup("Shoulder Wall Slide", "Mobility"),
        warmup("Band Pull-Apart", "Mobility", "Band"),
        warmup("Bodyweight Squat", "Mobility"),
    ).sortedBy { it.name.lowercase() }

    init {
        require(all.map { it.name.lowercase() }.distinct().size == all.size) {
            "Exercise preset names must be unique case-insensitively"
        }
    }

    /** Blank query returns everything; otherwise matches name/category/equipment. */
    fun search(query: String): List<ExercisePreset> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return all
        return all.filter {
            it.name.lowercase().contains(needle) ||
                it.category.lowercase().contains(needle) ||
                it.equipment.lowercase().contains(needle)
        }
    }
}
