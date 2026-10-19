package com.nobelle.phone.health

import android.app.Application
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nobelle.core.data.repository.HealthObservationRepository
import com.nobelle.core.health.CapabilityProbe
import com.nobelle.core.health.HealthConnectCategory
import com.nobelle.core.health.HealthConnectImporter
import com.nobelle.core.model.HealthObservation
import com.nobelle.core.model.OwnerId
import com.nobelle.phone.NobelleApplication
import java.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Health Connect import: availability probing, per-category read permission
 * and a manual 30-day import. Granting a permission never implies a source
 * wrote records; nothing is requested or imported automatically (§10/§24).
 */
class HealthConnectViewModel(application: Application) : AndroidViewModel(application) {
    data class HealthConnectUiState(
        val ownerAvailable: Boolean = false,
        val sdkStatus: Int = HealthConnectClient.SDK_UNAVAILABLE,
        val available: Boolean = false,
        val selected: Set<HealthConnectCategory> = emptySet(),
        val granted: Set<HealthConnectCategory> = emptySet(),
        val loading: Boolean = false,
        val lastImportCounts: Map<HealthConnectCategory, Int>? = null,
        val lastImported: List<HealthObservation> = emptyList(),
        val storedSummaries: List<MarkerSummary> = emptyList(),
        val storedCount: Int = 0,
        val message: String? = null,
    )

    private val app = application as NobelleApplication
    private val probe = CapabilityProbe(application)
    private val repository: HealthObservationRepository? = try {
        HealthObservationRepository(app.database, app.installationId)
    } catch (failure: Exception) {
        null // surfaced as a safe message; no secrets are logged
    }

    private val mutableState = MutableStateFlow(probeState())
    val state: StateFlow<HealthConnectUiState> = mutableState

    private var selectedOwner: OwnerId? = null
    private var selectionGeneration = 0L

    private fun probeState(): HealthConnectUiState {
        val status = HealthConnectClient.getSdkStatus(app)
        return HealthConnectUiState(
            sdkStatus = status,
            available = status == HealthConnectClient.SDK_AVAILABLE,
            message = if (repository == null) INIT_ERROR else null,
        )
    }

    fun selectOwner(owner: OwnerId?) {
        ++selectionGeneration
        val previous = selectedOwner
        selectedOwner = owner
        val probed = probeState()
        if (owner == null) {
            // Availability still displays without an owner; selection,
            // grants view and import results are owner-bound context.
            mutableState.value = probed.copy(ownerAvailable = false)
            return
        }
        val sameOwner = owner.uid == previous?.uid
        val current = mutableState.value
        mutableState.value = probed.copy(
            ownerAvailable = repository != null,
            // A different owner inherits no selection or import results;
            // granted permissions are app-level and are re-read below.
            selected = if (sameOwner) current.selected else emptySet(),
            lastImportCounts = if (sameOwner) current.lastImportCounts else null,
            lastImported = if (sameOwner) current.lastImported else emptyList(),
            storedSummaries = if (sameOwner) current.storedSummaries else emptyList(),
            storedCount = if (sameOwner) current.storedCount else 0,
        )
        refreshPermissions()
        refreshStored(owner)
    }

    /** Re-aggregates the stored-record summaries shown on My Health. */
    private fun refreshStored(owner: OwnerId) {
        val repo = repository ?: return
        val generation = selectionGeneration
        viewModelScope.launch {
            val stored = runCatching { repo.list(owner) }.getOrDefault(emptyList())
            if (generation != selectionGeneration || selectedOwner?.uid != owner.uid) return@launch
            val zone = TimeZone.currentSystemDefault()
            val today = Clock.System.now().toLocalDateTime(zone).date
            mutableState.value = mutableState.value.copy(
                storedSummaries = HealthSummary.summarize(stored, today, zone),
                storedCount = stored.count { it.deletedUtc == null },
            )
        }
    }

    fun toggle(category: HealthConnectCategory) {
        val current = mutableState.value
        mutableState.value = current.copy(
            selected = if (category in current.selected) {
                current.selected - category
            } else {
                current.selected + category
            },
        )
    }

    /** Reads which of our four permissions the app currently holds. */
    fun refreshPermissions() {
        val client = probe.clientOrNull() ?: return
        val generation = selectionGeneration
        viewModelScope.launch {
            val strings = runCatching { client.permissionController.getGrantedPermissions() }
                .getOrDefault(emptySet())
            if (generation != selectionGeneration) return@launch
            mutableState.value = mutableState.value.copy(
                granted = HealthConnectPermissions.categoriesFor(strings),
            )
        }
    }

    /** Permission strings for the currently selected categories only. */
    fun permissionsToRequest(): Set<String> =
        mutableState.value.selected.map { it.permission }.toSet()

    fun showMessage(message: String) {
        mutableState.value = mutableState.value.copy(message = message)
    }

    /**
     * Applies the system's actual (possibly partial) grant set. If at least
     * one selected category is granted, the import proceeds for those only;
     * denied selections are reported neutrally alongside the result.
     */
    fun onPermissionResult(grantedPermissions: Set<String>) {
        val granted = HealthConnectPermissions.categoriesFor(grantedPermissions)
        val denied = mutableState.value.selected - granted
        mutableState.value = mutableState.value.copy(granted = granted)
        if ((mutableState.value.selected intersect granted).isEmpty()) {
            mutableState.value = mutableState.value.copy(
                message = if (denied.isEmpty()) {
                    "No health categories were granted."
                } else {
                    "Access not granted for: ${denied.joinToString { it.displayName }}."
                },
            )
            return
        }
        importSelected(
            deniedNote = if (denied.isEmpty()) {
                null
            } else {
                "Access not granted for: ${denied.joinToString { it.displayName }}."
            },
        )
    }

    fun importSelected() = importSelected(deniedNote = null)

    private fun importSelected(deniedNote: String?) {
        val owner = selectedOwner ?: run {
            showMessage("Connect your Google identity first")
            return
        }
        val repo = repository ?: run {
            showMessage(INIT_ERROR)
            return
        }
        val client = probe.clientOrNull() ?: run {
            showMessage("Health Connect is not available or needs an update on this device")
            return
        }
        val toImport = mutableState.value.selected intersect mutableState.value.granted
        if (toImport.isEmpty()) {
            showMessage("Select at least one granted category to import")
            return
        }
        if (mutableState.value.loading) return
        val generation = selectionGeneration
        mutableState.value = mutableState.value.copy(loading = true, message = null)
        viewModelScope.launch {
            val importedAt = Clock.System.now()
            val rangeEnd = java.time.Instant.ofEpochSecond(
                importedAt.epochSeconds,
                importedAt.nanosecondsOfSecond.toLong(),
            )
            val rangeStart = rangeEnd.minus(IMPORT_RANGE)
            val outcome = runCatching {
                val result = HealthConnectImporter(client)
                    .import(owner, toImport, rangeStart, rangeEnd, importedAt)
                var stored = 0
                result.observations.forEach { observation ->
                    val (_, changed) = repo.upsertImported(owner, observation)
                    if (changed) stored += 1
                }
                result to stored
            }
            if (generation != selectionGeneration || selectedOwner?.uid != owner.uid) return@launch
            outcome.fold(
                onSuccess = { (result, stored) ->
                    val base = if (result.observations.isEmpty()) {
                        "Permission granted, but no records were found in the last 30 days."
                    } else {
                        "Imported $stored new or updated records. " +
                            "${result.observations.size} source records were available."
                    }
                    mutableState.value = mutableState.value.copy(
                        loading = false,
                        lastImportCounts = result.counts,
                        lastImported = result.observations,
                        message = (deniedNote?.let { "$it " } ?: "") + base,
                    )
                    refreshStored(owner)
                },
                onFailure = { failure ->
                    mutableState.value = mutableState.value.copy(
                        loading = false,
                        message = failure.message?.takeIf { it.isNotBlank() }
                            ?: "Health Connect import failed",
                    )
                },
            )
        }
    }

    private companion object {
        val IMPORT_RANGE: Duration = Duration.ofDays(30)
        const val INIT_ERROR = "Nobelle could not open its encrypted local store; your data was left untouched"
    }
}
