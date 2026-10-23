package com.nobelle.core.coaching

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.fail

class ExerciseMediaManifestTest {
    private val catalogIds = ExerciseCatalog.all().map { it.id }.toSet()
    private val manifest = ExerciseMediaManifest.all()
    private val byId = ExerciseMediaManifest.byId()

    @Test
    fun manifestCoversTheCatalogOneToOne() {
        assertEquals(catalogIds, manifest.map { it.id }.toSet())
        assertEquals(catalogIds.size, manifest.size)
        manifest.forEach { entry -> assertNotNull(byId[entry.id]) }
    }

    @Test
    fun bundledEntriesHaveNoVideoAndPendingTechniqueReview() {
        // Pending content milestone: every video field stays null/absent, and
        // every movement demonstration honestly reports a pending review.
        assertTrue(manifest.isNotEmpty())
        manifest.forEach { entry ->
            assertNull("${entry.id}: video must be null for now", entry.video)
            assertEquals("${entry.id}: review status", "PENDING", entry.techniqueReview.status)
            assertNull("${entry.id}: reviewedBy", entry.techniqueReview.reviewedBy)
            assertNull("${entry.id}: reviewedUtc", entry.techniqueReview.reviewedUtc)
            assertTrue("${entry.id}: speakSteps default", entry.tts.speakSteps)
        }
    }

    @Test
    fun techniqueReviewLabelIsHonestWhilePending() {
        val entry = byId.getValue("push-up")
        assertTrue(entry.techniqueReviewLabel.contains("pending"))
        assertTrue(entry.techniqueReviewLabel.contains("Nobelle"))
    }

    @Test
    fun minimalNullVideoEntryIsAccepted() {
        val source = """
            {"entries":[{"id":"push-up"}]}
        """.trimIndent()
        val entries = ExerciseMediaManifest.load(source, validIds = setOf("push-up"))
        assertEquals(1, entries.size)
        assertNull(entries.single().video)
        assertTrue(entries.single().tts.speakSteps)
        assertEquals("PENDING", entries.single().techniqueReview.status)
    }

    @Test
    fun urlWithMissingAttributionIsRejected() {
        val source = """
            {"entries":[{"id":"push-up","video":{
              "url":"https://example.test/v.mp4",
              "sha256":"${"a".repeat(64)}",
              "sizeBytes":1234,"durationMs":5000,
              "license":"CC BY 4.0","source":"https://example.test","version":"1"}}]}
        """.trimIndent()
        try {
            ExerciseMediaManifest.load(source, validIds = setOf("push-up"))
            fail("missing attribution must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("attribution"))
        }
    }

    @Test
    fun urlWithMissingChecksumIsRejected() {
        val source = """
            {"entries":[{"id":"push-up","video":{
              "url":"https://example.test/v.mp4",
              "sizeBytes":1234,"durationMs":5000,
              "license":"CC BY 4.0","attribution":"Example",
              "source":"https://example.test","version":"1"}}]}
        """.trimIndent()
        try {
            ExerciseMediaManifest.load(source, validIds = setOf("push-up"))
            fail("missing checksum must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("sha256"))
        }
    }

    @Test
    fun malformedChecksumIsRejected() {
        val source = manifestJson(sha256 = "not-hex-and-too-short")
        try {
            ExerciseMediaManifest.load(source, validIds = setOf("push-up"))
            fail("malformed checksum must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("64 hex"))
        }
    }

    @Test
    fun validUrlEntryWithFullProvenanceIsAccepted() {
        val source = manifestJson(sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
        val entries = ExerciseMediaManifest.load(source, validIds = setOf("push-up"))
        val video = entries.single().video!!
        assertEquals("https://example.test/v.mp4", video.url)
        assertEquals(1234L, video.sizeBytes)
        assertEquals(5000L, video.durationMs)
        assertEquals("CC BY 4.0", video.license)
        assertEquals("Example Films", video.attribution)
        assertEquals("https://example.test", video.source)
        assertEquals("1", video.version)
    }

    @Test
    fun urlWithoutLicenseIsRejected() {
        val source = """
            {"entries":[{"id":"push-up","video":{
              "url":"https://example.test/v.mp4",
              "sha256":"${"b".repeat(64)}",
              "sizeBytes":1234,"durationMs":5000,
              "attribution":"Example Films",
              "source":"https://example.test","version":"1"}}]}
        """.trimIndent()
        try {
            ExerciseMediaManifest.load(source, validIds = setOf("push-up"))
            fail("missing licence must be rejected — nothing is assumed licence-free")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("license"))
        }
    }

    @Test
    fun halfDeclaredVideoFieldsWithoutUrlAreRejected() {
        // A checksum (or any provenance) without a url must not slip through.
        val source = """
            {"entries":[{"id":"push-up","video":{"sha256":"${"c".repeat(64)}"}}]}
        """.trimIndent()
        try {
            ExerciseMediaManifest.load(source, validIds = setOf("push-up"))
            fail("half-declared video fields must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("null"))
        }
    }

    @Test
    fun unknownIdIsRejected() {
        val source = """
            {"entries":[{"id":"push-up"},{"id":"ghost-exercise"}]}
        """.trimIndent()
        try {
            ExerciseMediaManifest.load(source, validIds = setOf("push-up"))
            fail("unknown id must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("ghost-exercise"))
        }
    }

    @Test
    fun missingIdIsRejected() {
        val source = """
            {"entries":[{"id":"push-up"}]}
        """.trimIndent()
        try {
            ExerciseMediaManifest.load(source, validIds = setOf("push-up", "plank"))
            fail("missing id must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("plank"))
        }
    }

    @Test
    fun reviewedStatusRequiresReviewerAndDate() {
        val source = """
            {"entries":[{"id":"push-up",
              "techniqueReview":{"status":"REVIEWED","reviewedBy":null,"reviewedUtc":null}}]}
        """.trimIndent()
        try {
            ExerciseMediaManifest.load(source, validIds = setOf("push-up"))
            fail("REVIEWED without a reviewer must be rejected")
        } catch (expected: IllegalArgumentException) {
        }
    }

    private fun manifestJson(sha256: String): String = """
        {"entries":[{"id":"push-up","video":{
          "url":"https://example.test/v.mp4",
          "sha256":"$sha256",
          "sizeBytes":1234,"durationMs":5000,
          "license":"CC BY 4.0","attribution":"Example Films",
          "source":"https://example.test","version":"1"}}]}
    """.trimIndent()
}