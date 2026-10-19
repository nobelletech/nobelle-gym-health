package com.nobelle.phone.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nobelle.phone.auth.DriveTokenHolder
import com.nobelle.phone.auth.EncryptedOwnerStore
import com.nobelle.phone.auth.SilentDriveTokenProvider
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Background drain of the encrypted outbox to the owner's Drive
 * appDataFolder, scheduled by WorkManager so sync continues while the app is
 * not in the foreground. When the foreground path's token is still held it is
 * reused; otherwise a fresh token is fetched silently through Google Play
 * Services for the account email recorded at consent — allowed only because
 * the user completed the Drive consent screen before (persisted marker in
 * [EncryptedOwnerStore]). The token still lives
 * only in [DriveTokenHolder] for the duration of the drain and is never
 * persisted or logged.
 *
 * Nothing to do (no owner, no consent marker) is a terminal state → success,
 * not retry. A missing silent token or a failing drain → bounded retry; the
 * periodic schedule tries again on its next interval regardless.
 */
class OutboxSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ownerStore = EncryptedOwnerStore(applicationContext)
        val outcome = OutboxSyncDecision(
            ownerUid = { ownerStore.read()?.uid },
            driveConsentGranted = ownerStore::isDriveConsentGranted,
            driveAccountEmail = ownerStore::driveAccountEmail,
            heldToken = DriveTokenHolder::peek,
            refreshToken = { email ->
                SilentDriveTokenProvider.refreshToken(applicationContext, email)
            },
            setHeldToken = DriveTokenHolder::set,
            drain = { uid -> OutboxSyncRunner.drainIfPossible(applicationContext, uid) },
        ).run(runAttemptCount)
        when (outcome) {
            OutboxSyncDecision.Outcome.SUCCESS -> Result.success()
            OutboxSyncDecision.Outcome.RETRY -> Result.retry()
            OutboxSyncDecision.Outcome.FAILURE -> Result.failure()
        }
    }
}

/**
 * Decision core of [OutboxSyncWorker]: every Android/GMS collaborator is
 * injected so JVM unit tests cover each branch. Not part of the public API.
 */
internal class OutboxSyncDecision(
    private val ownerUid: () -> String?,
    private val driveConsentGranted: () -> Boolean,
    private val driveAccountEmail: () -> String?,
    private val heldToken: () -> String?,
    private val refreshToken: (accountEmail: String?) -> String?,
    private val setHeldToken: (String) -> Unit,
    private val drain: suspend (ownerUid: String) -> Unit,
) {
    enum class Outcome { SUCCESS, RETRY, FAILURE }

    suspend fun run(attempt: Int): Outcome {
        // No established owner, or Drive consent was never granted (or was
        // revoked): nothing to sync — and that is a terminal state for this
        // run, not an error worth retrying.
        val uid = ownerUid() ?: return Outcome.SUCCESS
        if (!driveConsentGranted()) return Outcome.SUCCESS
        if (heldToken() == null) {
            // Silent refresh is the whole point of background sync; a null
            // result means no recorded account or consent not silently
            // recoverable right now (or no network), so retry within this
            // run's bounded budget.
            val token = runCatching { refreshToken(driveAccountEmail()) }.getOrNull()
                ?: return retryOrFail(attempt)
            setHeldToken(token)
        }
        return try {
            drain(uid)
            Outcome.SUCCESS
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            retryOrFail(attempt)
        }
    }

    private fun retryOrFail(attempt: Int): Outcome =
        // runAttemptCount is zero-based: at most MAX_ATTEMPTS tries per run —
        // the next periodic interval restarts the budget.
        if (attempt + 1 >= MAX_ATTEMPTS) Outcome.FAILURE else Outcome.RETRY

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}

/**
 * WorkManager scheduling for [OutboxSyncWorker]. The periodic request is the
 * durable schedule; the one-time expedited request catches work that should
 * not wait for the next interval (right after the Drive grant completes, and
 * on app start while consent still stands).
 */
object OutboxSyncScheduler {
    const val PERIODIC_WORK_NAME = "nobelle-outbox-sync"
    const val ONE_TIME_WORK_NAME = "nobelle-outbox-sync-now"

    /** Idempotent: UPDATE replaces the existing periodic request in place. */
    fun ensurePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<OutboxSyncWorker>(
            PERIODIC_INTERVAL_MINUTES, TimeUnit.MINUTES,
        )
            .setConstraints(networkConstraint())
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                BACKOFF_DELAY_SECONDS, TimeUnit.SECONDS,
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /**
     * Expedited single pass; degrades to a normal request when the expedited
     * quota is spent. KEEP leaves an already queued pass in place instead of
     * replacing it.
     */
    fun runOnceNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<OutboxSyncWorker>()
            .setConstraints(networkConstraint())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_TIME_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /** Drops both schedules — Drive disconnect or local owner forgotten. */
    fun cancel(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
        workManager.cancelUniqueWork(ONE_TIME_WORK_NAME)
    }

    private fun networkConstraint(): Constraints =
        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    private const val PERIODIC_INTERVAL_MINUTES = 15L // platform minimum
    private const val BACKOFF_DELAY_SECONDS = 30L
}
