package com.nobelle.phone.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import com.nobelle.core.health.HealthConnectCategory
import com.nobelle.core.model.FastingSchedule
import com.nobelle.core.model.FastingSession
import com.nobelle.core.model.HealthObservation
import com.nobelle.core.model.MarkerGroup
import com.nobelle.core.model.Provenance
import com.nobelle.phone.fasting.FastingBackdateInput
import com.nobelle.phone.fasting.FastingInputParser
import com.nobelle.phone.fasting.FastingViewModel
import com.nobelle.phone.health.HealthConnectViewModel
import com.nobelle.phone.health.MarkerSummary
import kotlin.time.Duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * My Health (§3/§10): overview cards by group; each marker opens a dedicated
 * history screen with units, range, source, last update, gaps and export.
 * The fasting card is an explicit opt-in tracker — the schedule is planning
 * context only and never starts, ends, extends or judges a fast.
 */
@Composable
fun MyHealthScreen(
    fasting: FastingViewModel.FastingUiState,
    onFastingEnable: (fastingHours: Int, startLocalTime: String) -> Unit,
    onFastingDisable: () -> Unit,
    onFastingStart: () -> Unit,
    onFastingEnd: () -> Unit,
    onFastingCancel: () -> Unit,
    onFastingBackdate: (FastingBackdateInput) -> Unit,
    onFastingDelete: (String) -> Unit,
    onFastingTick: () -> Unit,
    healthConnect: HealthConnectViewModel.HealthConnectUiState,
    onToggleHealthCategory: (HealthConnectCategory) -> Unit,
    onRequestHealthAccess: () -> Unit,
    onImportHealth: () -> Unit,
    onRefreshHealthAccess: () -> Unit,
    showHealthRationale: Boolean,
    onDismissHealthRationale: () -> Unit,
) {
    var showSetup by remember { mutableStateOf(false) }
    var showBackdate by remember { mutableStateOf(false) }
    var confirmDisable by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }
    var deletingId by remember { mutableStateOf<String?>(null) }

    // Elapsed time re-renders while a fast is active; tick() only updates the
    // clock in UI state — it never writes or auto-ends anything.
    if (fasting.active != null) {
        LaunchedEffect(fasting.active!!.id) {
            while (isActive) {
                delay(30_000)
                onFastingTick()
            }
        }
    }

    ScreenColumn {
        ScreenTitle("My Health")

        MarkerGroup.entries.forEach { group ->
            val groupMarkers = healthConnect.storedSummaries.filter { it.group == group }
            InfoCard(
                title = group.displayName,
                body = if (groupMarkers.isEmpty()) {
                    "No data yet. Connect a source or add a manual entry."
                } else {
                    val records = groupMarkers.sumOf { it.count }
                    val latest = groupMarkers.maxOf { it.latestMeasuredUtc }
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                    "${groupMarkers.size} marker${if (groupMarkers.size == 1) "" else "s"} · " +
                        "$records record${if (records == 1) "" else "s"} · latest ${latest.date}"
                },
            )
        }

        if (healthConnect.ownerAvailable && healthConnect.storedSummaries.isNotEmpty()) {
            StoredRecordsCard(summaries = healthConnect.storedSummaries)
        }

        fasting.message?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
        }

        FastingCard(
            fasting = fasting,
            onSetup = { showSetup = true },
            onStart = onFastingStart,
            onBackdate = { showBackdate = true },
            onEnd = onFastingEnd,
            onRequestCancel = { confirmCancel = true },
            onRequestDisable = { confirmDisable = true },
            onRequestDelete = { deletingId = it },
        )

        HealthConnectCard(
            health = healthConnect,
            onToggle = onToggleHealthCategory,
            onRequest = onRequestHealthAccess,
            onImport = onImportHealth,
            onRefresh = onRefreshHealthAccess,
        )

        InfoCard(
            title = "Ask Nobelle Health AI",
            body = "On-device explanations of your records. Disabled until you enable AI processing.",
            trailing = { OutlinedButton(onClick = {}) { Text("About") } },
        )
    }

    if (showSetup) {
        FastingSetupDialog(
            enabled = !fasting.loading,
            onDismiss = { showSetup = false },
            onEnable = { hours, start ->
                showSetup = false
                onFastingEnable(hours, start)
            },
        )
    }

    if (showBackdate) {
        FastingBackdateDialog(
            schedule = fasting.schedule,
            enabled = !fasting.loading,
            onDismiss = { showBackdate = false },
            onSubmit = { input ->
                showBackdate = false
                onFastingBackdate(input)
            },
        )
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Cancel this fast?") },
            text = { Text("The fast is marked cancelled in your history — nothing is erased.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmCancel = false
                        onFastingCancel()
                    }
                ) { Text("Cancel fast") }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) { Text("Keep") }
            },
        )
    }

    if (confirmDisable) {
        AlertDialog(
            onDismissRequest = { confirmDisable = false },
            title = { Text("Disable the fasting tracker?") },
            text = { Text("The schedule is turned off and any active fast stays active. Your fasting history remains on this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDisable = false
                        onFastingDisable()
                    }
                ) { Text("Disable") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisable = false }) { Text("Keep") }
            },
        )
    }

    deletingId?.let { id ->
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text("Delete this fast?") },
            text = { Text("This marks the record as deleted on this device; a deletion is queued for Drive sync once upload exists.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deletingId = null
                        onFastingDelete(id)
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletingId = null }) { Text("Keep") }
            },
        )
    }

    if (showHealthRationale) {
        AlertDialog(
            onDismissRequest = onDismissHealthRationale,
            title = { Text("How Nobelle uses health data") },
            text = {
                Text(
                    "Nobelle reads only the health categories you select and grant. " +
                        "Imported records are stored encrypted on this device and are queued " +
                        "only for your own Google Drive — they are never sent anywhere else. " +
                        "You can deny or revoke access at any time in Health Connect settings.",
                )
            },
            confirmButton = {
                TextButton(onClick = onDismissHealthRationale) { Text("Got it") }
            },
        )
    }
}

@Composable
private fun FastingCard(
    fasting: FastingViewModel.FastingUiState,
    onSetup: () -> Unit,
    onStart: () -> Unit,
    onBackdate: () -> Unit,
    onEnd: () -> Unit,
    onRequestCancel: () -> Unit,
    onRequestDisable: () -> Unit,
    onRequestDelete: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Fasting", style = MaterialTheme.typography.titleMedium)
            when {
                !fasting.ownerAvailable -> {
                    Text(
                        "Connect your Google identity in Profile once; the optional fasting tracker then stays available offline.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onSetup, enabled = false) { Text("Set up") }
                }
                fasting.schedule == null -> {
                    Text(
                        "Optional eating-window tracker. You pick a schedule — Nobelle never starts, ends or extends a fast for you.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onSetup, enabled = !fasting.loading) { Text("Set up") }
                }
                fasting.active != null -> {
                    val session = fasting.active!!
                    Text(
                        "Elapsed ${session.elapsedAt(fasting.now).formatHm()} · " +
                            "Target ${session.targetDurationSec / 3600}h · " +
                            "Started ${session.actualStartInstant.localHm(session.originalStartZone)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (session.targetReached(fasting.now)) {
                        Text(
                            "Target reached — end when you choose.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onEnd, enabled = !fasting.loading) { Text("End fast") }
                        OutlinedButton(onClick = onRequestCancel, enabled = !fasting.loading) {
                            Text("Cancel")
                        }
                    }
                }
                else -> {
                    val schedule = fasting.schedule!!
                    Text(
                        "${schedule.fastingHours}:${schedule.eatingHours} · starts ${schedule.startLocalTime} daily",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Planning context only — nothing starts automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onStart, enabled = !fasting.loading) { Text("Start fast") }
                        OutlinedButton(onClick = onBackdate, enabled = !fasting.loading) {
                            Text("Backdate")
                        }
                        TextButton(onClick = onRequestDisable, enabled = !fasting.loading) {
                            Text("Disable")
                        }
                    }
                }
            }
            if (fasting.ownerAvailable && fasting.history.isNotEmpty()) {
                fasting.history.take(5).forEach { session ->
                    FastingHistoryRow(
                        session = session,
                        enabled = !fasting.loading,
                        onDelete = { onRequestDelete(session.id) },
                    )
                }
                if (fasting.history.size > 5) {
                    Text(
                        "…and ${fasting.history.size - 5} earlier fast" +
                            if (fasting.history.size - 5 == 1) "" else "s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun FastingHistoryRow(
    session: FastingSession,
    enabled: Boolean,
    onDelete: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val startLocal = session.actualStartInstant.localHm(session.originalStartZone)
        val endLocal = session.actualEndInstant?.let {
            it.localHm(session.originalEndZone ?: session.originalStartZone)
        }
        val startDate = session.actualStartInstant.localDate(session.originalStartZone)
        Text(
            "$startDate · $startLocal${endLocal?.let { "–$it" } ?: "–ongoing"}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            buildString {
                append(session.elapsedAt(Clock.System.now()).formatHm())
                append(" · ")
                append(
                    when (session.status) {
                        FastingSession.Status.ACTIVE -> "Active"
                        FastingSession.Status.COMPLETED -> "Completed"
                        FastingSession.Status.CANCELLED -> "Cancelled"
                    }
                )
                if (session.endedEarly) append(" · Ended before target")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onDelete, enabled = enabled) { Text("Delete") }
    }
}

@Composable
private fun FastingSetupDialog(
    enabled: Boolean,
    onDismiss: () -> Unit,
    onEnable: (fastingHours: Int, startLocalTime: String) -> Unit,
) {
    var selectedHours by remember { mutableStateOf<Int?>(null) }
    var startTime by remember { mutableStateOf("20:00") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set up fasting") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Choose a daily template and when your fast usually starts. " +
                        "This is only a planning guide — Nobelle never starts or ends a fast for you.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FastingSchedule.TEMPLATES.forEach { (fasting, eating) ->
                        FilterChip(
                            selected = selectedHours == fasting,
                            onClick = { selectedHours = fasting },
                            label = { Text("$fasting:$eating") },
                        )
                    }
                }
                NativeTimeField(
                    label = "Usual start time",
                    value = startTime,
                    enabled = enabled,
                    onChange = { startTime = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = enabled && selectedHours != null,
                onClick = { selectedHours?.let { onEnable(it, startTime) } },
            ) { Text("Enable") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun FastingBackdateDialog(
    schedule: FastingSchedule?,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (FastingBackdateInput) -> Unit,
) {
    val zone = TimeZone.currentSystemDefault()
    val localNow = Clock.System.now().toLocalDateTime(zone)
    var startDate by remember {
        mutableStateOf(localNow.date.minus(DatePeriod(days = 1)).toString())
    }
    var startTime by remember { mutableStateOf(schedule?.startLocalTime ?: "20:00") }
    var endDate by remember { mutableStateOf(localNow.date.toString()) }
    var endTime by remember { mutableStateOf("%02d:%02d".format(localNow.hour, localNow.minute)) }
    var notes by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf<String?>(null) }
    // Synchronous submit guard: the repository is the authoritative
    // idempotency boundary, but a double-tap shouldn't fire twice.
    var submitted by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a past fast") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Start", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NativeDateField("Start date", startDate, enabled, { startDate = it }, Modifier.weight(1f))
                    NativeTimeField("Start time", startTime, enabled, { startTime = it }, Modifier.weight(1f))
                }
                Text("End", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NativeDateField("End date", endDate, enabled, { endDate = it }, Modifier.weight(1f))
                    NativeTimeField("End time", endTime, enabled, { endTime = it }, Modifier.weight(1f))
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = enabled && !submitted,
                onClick = {
                    when (val parsed = FastingInputParser.parse(
                        startDate = startDate, startTime = startTime,
                        endDate = endDate, endTime = endTime,
                        zoneId = zone.id, notes = notes,
                    )) {
                        is FastingInputParser.Result.Ok -> {
                            submitted = true
                            onSubmit(parsed.input)
                        }
                        is FastingInputParser.Result.Invalid -> formError = parsed.message
                    }
                },
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun NativeDateField(
    label: String,
    value: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val date = runCatching { LocalDate.parse(value) }
        .getOrElse { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date }
    OutlinedButton(
        onClick = {
            DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    onChange("%04d-%02d-%02d".format(year, month + 1, dayOfMonth))
                },
                date.year,
                date.monthNumber - 1,
                date.dayOfMonth,
            ).show()
        },
        enabled = enabled,
        modifier = modifier.semantics { contentDescription = "$label, $value" },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label)
            Text(value, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun NativeTimeField(
    label: String,
    value: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val time = runCatching { LocalTime.parse(value) }
        .getOrElse { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time }
    OutlinedButton(
        onClick = {
            TimePickerDialog(
                context,
                { _, hourOfDay, minute -> onChange("%02d:%02d".format(hourOfDay, minute)) },
                time.hour,
                time.minute,
                android.text.format.DateFormat.is24HourFormat(context),
            ).show()
        },
        enabled = enabled,
        modifier = modifier.semantics { contentDescription = "$label, $value" },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label)
            Text(value, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun Duration.formatHm(): String = "${inWholeHours}h %02dm".format(inWholeMinutes % 60)

private fun Instant.localHm(zoneId: String): String {
    val zone = runCatching { TimeZone.of(zoneId) }.getOrElse { TimeZone.currentSystemDefault() }
    val local = toLocalDateTime(zone)
    return "%02d:%02d".format(local.hour, local.minute)
}

private fun Instant.localDate(zoneId: String): String {
    val zone = runCatching { TimeZone.of(zoneId) }.getOrElse { TimeZone.currentSystemDefault() }
    return toLocalDateTime(zone).date.toString()
}

private val MarkerGroup.displayName: String
    get() = name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

@Composable
private fun HealthConnectCard(
    health: HealthConnectViewModel.HealthConnectUiState,
    onToggle: (HealthConnectCategory) -> Unit,
    onRequest: () -> Unit,
    onImport: () -> Unit,
    onRefresh: () -> Unit,
) {
    var showImportDetails by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Health Connect", style = MaterialTheme.typography.titleMedium)
            when {
                !health.available -> {
                    Text(
                        if (health.sdkStatus ==
                            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED
                        ) {
                            "Health Connect is installed but its provider needs an update before records can be read."
                        } else {
                            "Health Connect is not available on this device."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                !health.ownerAvailable -> {
                    Text(
                        "Health Connect is available. Connect your Google identity in Profile first — imported records stay scoped to your account on this device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    Text(
                        "Choose the categories Nobelle may read. Permission does not guarantee that a source has written records.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HealthConnectCategory.entries.forEach { category ->
                        FilterChip(
                            selected = category in health.selected,
                            onClick = { onToggle(category) },
                            enabled = !health.loading,
                            label = {
                                Text(
                                    "${category.displayName} · " +
                                        if (category in health.granted) "Granted" else "Not granted",
                                )
                            },
                            modifier = Modifier.semantics {
                                contentDescription = category.displayName
                            },
                        )
                    }
                    Button(
                        onClick = onRequest,
                        enabled = !health.loading && health.selected.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Request selected access") }
                    OutlinedButton(
                        onClick = onImport,
                        enabled = !health.loading &&
                            (health.selected intersect health.granted).isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Import selected") }
                    OutlinedButton(
                        onClick = onRefresh,
                        enabled = !health.loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Refresh access") }
                    if (health.storedCount > 0) {
                        Text(
                            "${health.storedCount} records stored on this device",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (health.lastImportCounts != null || health.lastImported.isNotEmpty()) {
                        TextButton(onClick = { showImportDetails = !showImportDetails }) {
                            Text(
                                if (showImportDetails) {
                                    "Hide import details"
                                } else {
                                    "Import details"
                                },
                            )
                        }
                        if (showImportDetails) {
                            health.lastImportCounts?.let { counts ->
                                Text("Last import", style = MaterialTheme.typography.labelLarge)
                                HealthConnectCategory.entries.forEach { category ->
                                    counts[category]?.let { count ->
                                        Text(
                                            "${category.displayName}: $count source records",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            health.lastImported.take(8).forEach { observation ->
                                HealthObservationRow(observation)
                            }
                        }
                    }
                }
            }
            health.message?.let { message ->
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * Aggregated view of every health observation stored for the owner —
 * imported or restored — grouped per marker. Raw per-record diagnostics stay
 * inside the Health Connect card's collapsed import details.
 */
@Composable
private fun StoredRecordsCard(summaries: List<MarkerSummary>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Records on this device", style = MaterialTheme.typography.titleMedium)
            summaries.forEach { summary ->
                val latest = summary.latestMeasuredUtc
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        summary.marker.replace('_', ' '),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        buildString {
                            append(
                                "${summary.count} record" +
                                    if (summary.count == 1) "" else "s",
                            )
                            append(
                                " · latest ${latest.date} " +
                                    "%02d:%02d".format(latest.hour, latest.minute),
                            )
                            summary.todayTotal?.let {
                                append(" · today ${it.formatNeedless()} ${summary.unit}")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun HealthObservationRow(observation: HealthObservation) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            "${observation.marker} · " +
                "${observation.value.formatNeedless()} ${observation.unit}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            buildString {
                append(observation.measuredLabel())
                observation.source?.let {
                    append(" · ${it.sourceNamespace.removePrefix("health_connect:")}")
                }
                append(
                    if (observation.provenance == Provenance.MANUALLY_ENTERED) {
                        " · Manual"
                    } else {
                        " · Imported"
                    },
                )
                if (observation.importedUtc != null) append(" · Imported now")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun HealthObservation.measuredLabel(): String {
    val offsetSec = timeZoneOffsetSec
    return if (offsetSec != null) {
        // Render the wall time the source observed: shift the UTC instant by
        // the stored offset, then format in UTC so the shift is not applied
        // a second time by the display zone.
        val wall = Instant.fromEpochMilliseconds(
            measuredUtc.toEpochMilliseconds() + offsetSec * 1000L,
        ).toLocalDateTime(TimeZone.UTC)
        val offsetHours = offsetSec / 3600
        "%04d-%02d-%02d %02d:%02d (UTC%+d)".format(
            wall.year, wall.monthNumber, wall.dayOfMonth, wall.hour, wall.minute,
            offsetHours,
        )
    } else {
        val utc = measuredUtc.toLocalDateTime(TimeZone.UTC)
        "%04d-%02d-%02d %02d:%02d UTC".format(
            utc.year, utc.monthNumber, utc.dayOfMonth, utc.hour, utc.minute,
        )
    }
}

private fun Double.formatNeedless(): String =
    if (this % 1.0 == 0.0) toInt().toString() else toString()
