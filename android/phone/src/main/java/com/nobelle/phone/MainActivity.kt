package com.nobelle.phone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobelle.phone.auth.DriveAuthorizationController
import com.nobelle.phone.ui.NobelleApp
import com.nobelle.phone.ui.NobelleTheme

class MainActivity : ComponentActivity() {
    private lateinit var driveAuthorization: DriveAuthorizationController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        driveAuthorization = DriveAuthorizationController(this)
        enableEdgeToEdge()
        setContent {
            val driveState by driveAuthorization.state.collectAsStateWithLifecycle()
            NobelleTheme {
                NobelleApp(
                    driveStatus = driveState.label,
                    onConnectDrive = driveAuthorization::authorize,
                    onDisconnectDrive = driveAuthorization::disconnect,
                    driveConnected = driveState is DriveAuthorizationController.State.Connected,
                )
            }
        }
    }
}

private val DriveAuthorizationController.State.label: String
    get() = when (this) {
        DriveAuthorizationController.State.Disconnected -> "Not connected"
        DriveAuthorizationController.State.Authorizing -> "Waiting for Google authorization…"
        is DriveAuthorizationController.State.Connected -> "Connected with Drive app-data permission"
        is DriveAuthorizationController.State.Error -> message
    }
