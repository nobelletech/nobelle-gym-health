package com.nobelle.core.coaching

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Per-exercise media provenance metadata, bundled beside the curated catalog.
 *
 * Nothing here is assumed licence-free: a video asset may only carry a URL
 * when its source, licence, attribution, version and SHA-256 checksum are all
 * present and valid; TTS voice licences are explicitly the device engine's
 * responsibility (see the phone attribution screen). Every entry also carries
 * an honest technique-review status that the UI must surface — movement
 * demonstrations are marked pending review until a reviewer signs them off.
 */
@Serializable
data class ExerciseMediaEntry(
    val id: String,
    val video: VideoAsset? = null,
    val techniqueReview: TechniqueReview = TechniqueReview(),
    val tts: TtsConfig = TtsConfig(),
) {
    /** Honest, user-visible review status line for the detail UI. */
    val techniqueReviewLabel: String
        get() = if (techniqueReview.status == TechniqueReview.STATUS_REVIEWED) {
            "Technique review: reviewed by ${techniqueReview.reviewedBy} on " +
                (techniqueReview.reviewedUtc?.take(10) ?: "unknown date")
        } else {
            "Technique review: pending — content reviewed by Nobelle, " +
                "movement technique pending review before release"
        }
}

/**
 * A downloadable demonstration video. Every field stays null until the
 * content milestone ships real, licensed assets; when a url is present the
 * provenance fields are mandatory and the checksum must be valid hex.
 */
@Serializable
data class VideoAsset(
    val url: String? = null,
    val sha256: String? = null,
    val sizeBytes: Long? = null,
    val durationMs: Long? = null,
    val license: String? = null,
    val attribution: String? = null,
    val source: String? = null,
    val version: String? = null,
)

@Serializable
data class TechniqueReview(
    val status: String = STATUS_PENDING,
    val reviewedBy: String? = null,
    val reviewedUtc: String? = null,
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_REVIEWED = "REVIEWED"
    }
}

@Serializable
data class TtsConfig(
    /** Whether narration may speak instruction steps (captions always render). */
    val speakSteps: Boolean = true,
)

@Serializable
private data class MediaManifestFile(val entries: List<ExerciseMediaEntry>)

private val SHA256_HEX = Regex("^[0-9a-fA-F]{64}$")

/**
 * Loader + validation for the bundled media manifest. Ids must match the
 * catalog 1:1 in both directions — an unknown id is rejected and a catalog
 * entry without a manifest entry is rejected.
 */
object ExerciseMediaManifest {
    private const val RESOURCE = "/exercise_media_manifest.json"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val bundled: List<ExerciseMediaEntry> by lazy { load(resourceText()) }

    fun all(): List<ExerciseMediaEntry> = bundled

    fun byId(): Map<String, ExerciseMediaEntry> = bundled.associateBy { it.id }

    /** Parses [source] and enforces the manifest invariants. */
    fun load(source: String, validIds: Set<String> = catalogIds()): List<ExerciseMediaEntry> {
        val file = json.decodeFromString<MediaManifestFile>(source)
        val entries = file.entries
        require(entries.isNotEmpty()) { "Exercise media manifest is empty" }
        val ids = entries.map { it.id }
        require(ids.distinct().size == ids.size) { "Exercise media manifest ids must be unique" }
        val manifestIds = ids.toSet()
        val unknown = manifestIds - validIds
        val missing = validIds - manifestIds
        require(unknown.isEmpty()) { "Unknown exercise ids in media manifest: ${unknown.sorted()}" }
        require(missing.isEmpty()) { "Catalog exercises missing from media manifest: ${missing.sorted()}" }
        entries.forEach { entry -> validateEntry(entry) }
        return entries
    }

    private fun validateEntry(entry: ExerciseMediaEntry) {
        val review = entry.techniqueReview
        require(review.status == TechniqueReview.STATUS_PENDING ||
            review.status == TechniqueReview.STATUS_REVIEWED) {
            "${entry.id}: unknown technique review status '${review.status}'"
        }
        if (review.status == TechniqueReview.STATUS_REVIEWED) {
            require(!review.reviewedBy.isNullOrBlank()) {
                "${entry.id}: reviewedBy is required with status REVIEWED"
            }
            require(!review.reviewedUtc.isNullOrBlank()) {
                "${entry.id}: reviewedUtc is required with status REVIEWED"
            }
        } else {
            require(review.reviewedBy == null && review.reviewedUtc == null) {
                "${entry.id}: a PENDING review must not carry a reviewer or date"
            }
        }
        val video = entry.video
        if (video == null) return
        if (video.url.isNullOrBlank()) {
            // No URL yet — then nothing may be half-declared either.
            require(
                video.sha256 == null && video.sizeBytes == null && video.durationMs == null &&
                    video.license == null && video.attribution == null &&
                    video.source == null && video.version == null,
            ) { "${entry.id}: video fields must all be null while url is absent" }
            return
        }
        // A URL commits us to full, honest provenance — nothing is assumed
        // licence-free, and a checksum is mandatory per asset.
        require(!video.sha256.isNullOrBlank()) { "${entry.id}: video sha256 is required with a url" }
        require(SHA256_HEX.matches(video.sha256)) {
            "${entry.id}: video sha256 must be 64 hex characters"
        }
        require(video.sizeBytes != null && video.sizeBytes > 0) {
            "${entry.id}: video sizeBytes must be positive"
        }
        require(video.durationMs != null && video.durationMs > 0) {
            "${entry.id}: video durationMs must be positive"
        }
        require(!video.license.isNullOrBlank()) { "${entry.id}: video license is required" }
        require(!video.attribution.isNullOrBlank()) { "${entry.id}: video attribution is required" }
        require(!video.source.isNullOrBlank()) { "${entry.id}: video source is required" }
        require(!video.version.isNullOrBlank()) { "${entry.id}: video version is required" }
    }

    private fun catalogIds(): Set<String> =
        ExerciseCatalog.all().map { it.id }.toSet()

    private fun resourceText(): String =
        ExerciseMediaManifest::class.java.getResourceAsStream(RESOURCE)
            ?.bufferedReader().use { it?.readText() }
            ?: throw IllegalStateException("Bundled exercise media manifest resource is missing")
}