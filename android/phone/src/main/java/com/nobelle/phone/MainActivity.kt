package com.nobelle.phone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobelle.phone.auth.DriveAuthorizationController
import com.nobelle.phone.auth.DriveAuthViewModel
import com.nobelle.phone.ui.NobelleApp
import com.nobelle.phone.ui.NobelleTheme

class MainActivity : ComponentActivity() {
    private val driveAuthModel by viewModels<DriveAuthViewModel>()
    private lateinit var driveAuthorization: DriveAuthorizationController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        driveAuthorization = DriveAuthorizationController(this, driveAuthModel)
        enableEdgeToEdge()
        setContent {
            val driveState by driveAuthModel.state.collectAsStateWithLifecycle()
            NobelleTheme {
                NobelleApp(
                    driveStatus = driveState.label,
                    onConnectDrive = driveAuthorization::authorize,
                    onDisconnectDrive = driveAuthorization::disconnect,
                    driveConnected = driveState is DriveAuthViewModel.State.Connected,
                )
            }
        }
    }
}

private val DriveAuthViewModel.State.label: String
    get() = when (this) {
        DriveAuthViewModel.State.Disconnected -> "Not connected"
        DriveAuthViewModel.State.Authorizing -> "Waiting for Google authorization…"
        is DriveAuthViewModel.State.Connected -> "Connected with Drive app-data permission"
        is DriveAuthViewModel.State.Error -> message
    }
