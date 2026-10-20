package com.nobelle.phone.outdoor

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nobelle.core.model.OutdoorRoutePoint
import com.nobelle.core.model.OutdoorSession
import com.nobelle.phone.MainActivity
import com.nobelle.phone.R

/**
 * Foreground GPS recorder for outdoor walk/run/cycle sessions.
 *
 * Uses the platform [LocationManager] GPS provider only — no fused location
 * library. Started explicitly by the user from the Coach screen; while
 * running it holds a location-type foreground service with a persistent
 * notification, so tracking continues with the screen off but stops when the
 * user leaves it. No background-location permission is requested or needed.
 *
 * Collected fixes live in [OutdoorTrackStore]; on ACTION_STOP the store
 * exposes a finished track which the ViewModel persists via the repository.
 */
class OutdoorTrackService : Service(), LocationListener {

    private val locationManager by lazy { getSystemService(LocationManager::class.java) }
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }
    private var listening = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopTracking()
            else -> startTracking(intent?.getStringExtra(EXTRA_ACTIVITY))
        }
        return START_NOT_STICKY
    }

    private fun startTracking(activity: String?) {
        if (listening) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            // The UI requests permission before starting; reaching here means
            // a stale/intent-only start — refuse rather than run silently.
            stopSelf()
            return
        }
        ensureChannel()
        OutdoorTrackStore.begin(
            activity = activity ?: OutdoorSession.ACTIVITY_WALK,
            startedAtMs = System.currentTimeMillis(),
        )
        startForeground(
            NOTIFICATION_ID,
            buildNotification(activity ?: OutdoorSession.ACTIVITY_WALK, 0L),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        runCatching {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                MIN_TIME_MS,
                MIN_DISTANCE_M,
                this,
                mainLooper,
            )
        }.onFailure {
            // Provider disabled/unavailable — no track is recorded; the store
            // is reset so the UI never shows a phantom live session.
            OutdoorTrackStore.clearFinished()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        listening = true
    }

    private fun stopTracking() {
        if (listening) {
            locationManager.removeUpdates(this)
            listening = false
        }
        OutdoorTrackStore.finish(System.currentTimeMillis())
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onLocationChanged(location: Location) {
        OutdoorTrackStore.addPoint(
            OutdoorRoutePoint(
                lat = location.latitude,
                lon = location.longitude,
                epochMs = location.time,
                accuracyM = if (location.hasAccuracy()) location.accuracy.toDouble() else null,
            )
        )
        val state = OutdoorTrackStore.state.value
        val elapsedSec = state.startedAtMs?.let { (System.currentTimeMillis() - it) / 1_000 } ?: 0L
        notificationManager.notify(
            NOTIFICATION_ID,
            buildNotification(state.activity ?: "outdoor", elapsedSec),
        )
    }

    override fun onDestroy() {
        if (listening) {
            runCatching { locationManager.removeUpdates(this) }
            listening = false
        }
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Outdoor tracking",
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }
    }

    private fun buildNotification(activity: String, elapsedSec: Long): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = if (elapsedSec > 0) {
            "Nobelle is recording your outdoor session — ${formatElapsed(elapsedSec)}"
        } else {
            "Nobelle is recording your outdoor session"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Outdoor ${activity.replaceFirstChar { it.uppercase() }}")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    companion object {
        const val ACTION_START = "com.nobelle.phone.outdoor.START"
        const val ACTION_STOP = "com.nobelle.phone.outdoor.STOP"
        const val EXTRA_ACTIVITY = "com.nobelle.phone.outdoor.ACTIVITY"
        private const val CHANNEL_ID = "outdoor_tracking"
        private const val NOTIFICATION_ID = 4101
        private const val MIN_TIME_MS = 2_000L
        private const val MIN_DISTANCE_M = 2f

        fun formatElapsed(totalSec: Long): String {
            val h = totalSec / 3_600
            val m = (totalSec % 3_600) / 60
            val s = totalSec % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
        }
    }
}
