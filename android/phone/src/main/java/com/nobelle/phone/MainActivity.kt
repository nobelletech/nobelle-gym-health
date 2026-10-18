package com.nobelle.phone

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.nobelle.core.sync.AndroidDriveStore
import com.nobelle.core.sync.DriveRoundTripDiagnostic
import com.nobelle.phone.auth.DriveAuthorizationController
import com.nobelle.phone.auth.DriveAuthViewModel
import com.nobelle.phone.fasting.FastingViewModel
import com.nobelle.phone.gym.GymVisitViewModel
import com.nobelle.phone.health.HealthConnectController
import com.nobelle.phone.health.HealthConnectViewModel
import com.nobelle.phone.ui.NobelleApp
import com.nobelle.phone.ui.NobelleTheme
import com.nobelle.phone.workout.WorkoutViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val driveAuthModel by viewModels<DriveAuthViewModel>()
    private val workoutModel by viewModels<WorkoutViewModel>()
    private val gymVisitModel by viewModels<GymVisitViewModel>()
    private val fastingModel by viewModels<FastingViewModel>()
    private val healthConnectModel by viewModels<HealthConnectViewModel>()
    private lateinit var driveAuthorization: DriveAuthorizationController
    private lateinit var healthConnect: HealthConnectController
    private val healthRationaleRequested = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        driveAuthorization = DriveAuthorizationController(this, driveAuthModel)
        healthConnect = HealthConnectController(this, healthConnectModel)
        if (isHealthRationaleIntent(intent)) healthRationaleRequested.value = true
        enableEdgeToEdge()
        setContent {
            val driveState by driveAuthModel.state.collectAsStateWithLifecycle()
            val ownerId by driveAuthModel.ownerId.collectAsStateWithLifecycle()
            val workoutState by workoutModel.state.collectAsStateWithLifecycle()
            val gymVisitState by gymVisitModel.state.collectAsStateWithLifecycle()
            val fastingState by fastingModel.state.collectAsStateWithLifecycle()
            val healthConnectState by healthConnectModel.state.collectAsStateWithLifecycle()
            val showHealthRationale by healthRationaleRequested
            LaunchedEffect(ownerId?.uid) {
                workoutModel.selectOwner(ownerId)
                gymVisitModel.selectOwner(ownerId)
                fastingModel.selectOwner(ownerId)
                healthConnectModel.selectOwner(ownerId)
            }
            NobelleTheme {
                NobelleApp(
                    driveStatus = driveState.label,
                    onConnectDrive = driveAuthorization::authorize,
                    onDisconnectDrive = driveAuthorization::disconnect,
                    onTestDrive = ::testDriveRoundTrip,
                    driveConnected = driveState is DriveAuthViewModel.State.Connected,
                    driveDiagnosticStatus = (driveState as? DriveAuthViewModel.State.Connected)?.diagnosticStatus,
                    driveDiagnosticRunning = (driveState as? DriveAuthViewModel.State.Connected)?.diagnosticRunning == true,
                    workout = workoutState,
                    onStartWorkout = { workoutModel.start() },
                    onPauseOrResumeWorkout = workoutModel::pauseOrResume,
                    onFinishWorkout = workoutModel::finish,
                    onDiscardWorkout = workoutModel::discard,
                    onAddSet = { entry ->
                        workoutModel.addSet(
                            exerciseId = entry.exerciseId,
                            reps = entry.reps,
                            loadKg = entry.loadKg,
                            durationSec = entry.durationSec,
                            restSec = entry.restSec,
                            rpe = entry.rpe,
                            rir = entry.rir,
                            tempo = entry.tempo,
                            painFlag = entry.painFlag,
                            notes = entry.notes,
                            setType = entry.setType,
                            groupId = entry.groupId,
                            substitutedForExerciseId = entry.substitutedForExerciseId,
                        )
                    },
                    gymVisits = gymVisitState,
                    onLogVisitNow = gymVisitModel::logNow,
                    onBackdateVisit = gymVisitModel::addBackdated,
                    onEndVisit = gymVisitModel::endVisit,
                    onEditVisit = gymVisitModel::editVisit,
                    onDeleteVisit = gymVisitModel::deleteVisit,
                    fasting = fastingState,
                    onFastingEnable = fastingModel::enable,
                    onFastingDisable = fastingModel::disable,
                    onFastingStart = fastingModel::startNow,
                    onFastingEnd = fastingModel::endNow,
                    onFastingCancel = fastingModel::cancelActive,
                    onFastingBackdate = fastingModel::addBackdated,
                    onFastingDelete = fastingModel::deleteSession,
                    onFastingTick = fastingModel::tick,
                    healthConnect = healthConnectState,
                    onToggleHealthCategory = healthConnectModel::toggle,
                    onRequestHealthAccess = healthConnect::requestSelectedPermissions,
                    onImportHealth = healthConnectModel::importSelected,
                    onRefreshHealthAccess = healthConnectModel::refreshPermissions,
                    showHealthRationale = showHealthRationale,
                    onDismissHealthRationale = { healthRationaleRequested.value = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (isHealthRationaleIntent(intent)) healthRationaleRequested.value = true
    }

    private fun isHealthRationaleIntent(intent: Intent?): Boolean {
        if (intent == null) return false
        return intent.action == "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" ||
            (
                intent.action == Intent.ACTION_VIEW_PERMISSION_USAGE &&
                    intent.hasCategory("android.intent.category.HEALTH_PERMISSIONS")
                )
    }

    private fun testDriveRoundTrip() {
        driveAuthModel.diagnosticRunning()
        lifecycleScope.launch {
            val result = runCatching {
                val drive = AndroidDriveStore(accessToken = { driveAuthorization.accessToken() })
                DriveRoundTripDiagnostic(drive).run()
            }
            driveAuthModel.diagnosticResult(
                result.fold(
                    onSuccess = { "Passed: uploaded a non-personal event, read it back exactly, and deleted the temporary Drive file." },
                    onFailure = { "Failed: ${it.message ?: "Drive diagnostic error"}" },
                )
            )
        }
    }
}

private val DriveAuthViewModel.State.label: String
    get() = when (this) {
        DriveAuthViewModel.State.Disconnected -> "Not connected"
        DriveAuthViewModel.State.AuthenticatingIdentity -> "Waiting for Google sign-in…"
        DriveAuthViewModel.State.AuthorizingDrive -> "Identity established; waiting for separate Drive permission…"
        is DriveAuthViewModel.State.Connected -> "Connected with stable Google identity and Drive app-data permission"
        is DriveAuthViewModel.State.Error -> message
    }
