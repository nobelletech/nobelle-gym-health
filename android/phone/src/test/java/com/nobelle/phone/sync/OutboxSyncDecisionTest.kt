package com.nobelle.phone.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Covers every branch of the worker's decision core with injected fakes —
 * the Android/GMS boundaries themselves ([SilentDriveTokenProvider],
 * [EncryptedOwnerStore] on-device paths) stay untested on JVM by design.
 */
class OutboxSyncDecisionTest {
    private class Harness(
        var owner: String? = "iss:sub",
        var consent: Boolean = true,
        var accountEmail: String? = "owner@example.com",
        var held: String? = null,
        var refreshed: String? = null,
        var drainFailure: Exception? = null,
    ) {
        var refreshCalls = 0
        var requestedEmails = mutableListOf<String?>()
        var setTokens = mutableListOf<String>()
        var drainedUids = mutableListOf<String>()

        fun decision() = OutboxSyncDecision(
            ownerUid = { owner },
            driveConsentGranted = { consent },
            driveAccountEmail = { accountEmail },
            heldToken = { held },
            refreshToken = { email -> refreshCalls++; requestedEmails.add(email); refreshed },
            setHeldToken = { setTokens.add(it); held = it },
            drain = { uid -> drainedUids.add(uid); drainFailure?.let { throw it } },
        )
    }

    @Test
    fun `no owner succeeds without touching token or drain`() = runBlocking {
        val h = Harness(owner = null)
        assertEquals(OutboxSyncDecision.Outcome.SUCCESS, h.decision().run(0))
        assertEquals(0, h.refreshCalls)
        assertTrue(h.drainedUids.isEmpty())
    }

    @Test
    fun `consent off succeeds without touching token or drain`() = runBlocking {
        val h = Harness(consent = false)
        assertEquals(OutboxSyncDecision.Outcome.SUCCESS, h.decision().run(0))
        assertEquals(0, h.refreshCalls)
        assertTrue(h.drainedUids.isEmpty())
    }

    @Test
    fun `held token drains directly without a silent refresh`() = runBlocking {
        val h = Harness(held = "existing")
        assertEquals(OutboxSyncDecision.Outcome.SUCCESS, h.decision().run(0))
        assertEquals(0, h.refreshCalls)
        assertEquals(listOf("iss:sub"), h.drainedUids)
    }

    @Test
    fun `missing token refreshes for the recorded account email then drains`() = runBlocking {
        val h = Harness(refreshed = "fresh")
        assertEquals(OutboxSyncDecision.Outcome.SUCCESS, h.decision().run(0))
        assertEquals(listOf("owner@example.com"), h.requestedEmails)
        assertEquals(listOf("fresh"), h.setTokens)
        assertEquals("fresh", h.held)
        assertEquals(listOf("iss:sub"), h.drainedUids)
    }

    @Test
    fun `absent account email is passed through and retries like any failed refresh`() = runBlocking {
        // The provider itself returns null for a null email — the decision
        // treats it as a retryable refresh failure, not a terminal state.
        val h = Harness(accountEmail = null, refreshed = null)
        assertEquals(OutboxSyncDecision.Outcome.RETRY, h.decision().run(0))
        assertEquals(listOf(null), h.requestedEmails)
        assertTrue(h.drainedUids.isEmpty())
    }

    @Test
    fun `refresh returning null retries while attempts remain`() = runBlocking {
        val h = Harness(refreshed = null)
        assertEquals(OutboxSyncDecision.Outcome.RETRY, h.decision().run(0))
        assertEquals(1, h.refreshCalls)
        assertTrue(h.drainedUids.isEmpty())
        assertNull(h.held)
    }

    @Test
    fun `refresh returning null fails once the attempt budget is spent`() = runBlocking {
        val h = Harness(refreshed = null)
        assertEquals(OutboxSyncDecision.Outcome.RETRY, h.decision().run(3))
        assertEquals(OutboxSyncDecision.Outcome.FAILURE, h.decision().run(4))
    }

    @Test
    fun `refresh throwing is treated like a failed refresh`() = runBlocking {
        val h = Harness()
        val decision = OutboxSyncDecision(
            ownerUid = { h.owner },
            driveConsentGranted = { h.consent },
            driveAccountEmail = { h.accountEmail },
            heldToken = { h.held },
            refreshToken = { throw RuntimeException("gms down") },
            setHeldToken = { h.setTokens.add(it) },
            drain = { h.drainedUids.add(it) },
        )
        assertEquals(OutboxSyncDecision.Outcome.RETRY, decision.run(0))
        assertTrue(h.drainedUids.isEmpty())
    }

    @Test
    fun `drain failure retries within budget then fails`() = runBlocking {
        val h = Harness(held = "existing", drainFailure = RuntimeException("io"))
        assertEquals(OutboxSyncDecision.Outcome.RETRY, h.decision().run(0))
        assertEquals(OutboxSyncDecision.Outcome.FAILURE, h.decision().run(4))
        assertEquals(2, h.drainedUids.size) // attempted both times
    }

    @Test
    fun `drain cancellation propagates instead of retrying`() = runBlocking {
        val h = Harness(held = "existing", drainFailure = CancellationException("stopped"))
        try {
            h.decision().run(0)
            fail("CancellationException must propagate")
        } catch (expected: CancellationException) {
            // propagated — worker cancellation is honoured
        }
    }
}
