package com.nobelle.core.coaching

import com.nobelle.core.model.ExerciseDefinition
import kotlinx.serialization.json.Json

/**
 * The bundled, reviewed exercise catalog. Ships as a classpath resource so
 * it works in pure-JVM tests and inside the Android app without res/raw
 * plumbing; it is curated data, never synced and never personalized.
 */
object ExerciseCatalog {
    private const val RESOURCE = "/exercise_catalog.json"
    const val REVIEWED_AUTHOR = "Nobelle reviewed"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val bundled: List<ExerciseDefinition> by lazy { load(resourceText()) }

    /** The reviewed catalog. Parsing and link validation happen once, lazily. */
    fun all(): List<ExerciseDefinition> = bundled

    fun byId(): Map<String, ExerciseDefinition> = bundled.associateBy { it.id }

    /** Parses [source] and enforces catalog invariants. */
    fun load(source: String): List<ExerciseDefinition> {
        val definitions = json.decodeFromString<List<ExerciseDefinition>>(source)
        require(definitions.isNotEmpty()) { "Exercise catalog is empty" }
        val ids = definitions.map { it.id }
        require(ids.distinct().size == ids.size) { "Exercise ids must be unique" }
        val byId = ids.toSet()
        definitions.forEach { def ->
            require(def.author == REVIEWED_AUTHOR) {
                "${def.id}: catalog entries must carry the reviewed author"
            }
            require(def.instructions.size >= 3) { "${def.id}: needs at least 3 instruction steps" }
            require(def.commonMistakes.isNotEmpty()) { "${def.id}: needs at least 1 common mistake" }
            def.easierAlternativeId?.let {
                require(it in byId) { "${def.id}: easier alternative '$it' does not resolve" }
            }
            def.harderAlternativeId?.let {
                require(it in byId) { "${def.id}: harder alternative '$it' does not resolve" }
            }
        }
        return definitions
    }

    private fun resourceText(): String =
        ExerciseCatalog::class.java.getResourceAsStream(RESOURCE)
            ?.bufferedReader().use { it?.readText() }
            ?: throw IllegalStateException("Bundled exercise catalog resource is missing")
}
