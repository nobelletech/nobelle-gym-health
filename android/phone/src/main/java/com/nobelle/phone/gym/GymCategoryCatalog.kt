package com.nobelle.phone.gym

/** Fixed list of gym visit categories; a manual free-text category remains
 *  available in the UI for anything outside this list. */
object GymCategoryCatalog {
    val all: List<String> = listOf(
        "Bodybuilding",
        "Cardio",
        "Circuit Training",
        "Cross-Training",
        "Group Class",
        "HIIT",
        "Martial Arts",
        "Mobility",
        "Personal Training",
        "Pilates",
        "Recovery",
        "Sauna",
        "Sports",
        "Strength",
        "Stretching",
        "Swimming",
        "Yoga",
    ).sortedBy { it.lowercase() }

    init {
        require(all.map { it.lowercase() }.distinct().size == all.size) {
            "Gym categories must be unique case-insensitively"
        }
    }

    /** Blank query returns everything A-Z; otherwise case-insensitive contains. */
    fun search(query: String): List<String> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return all
        return all.filter { it.lowercase().contains(needle) }
    }
}
