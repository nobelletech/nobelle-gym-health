package com.nobelle.phone.coach

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.nobelle.core.model.PlannedSession
import com.nobelle.core.model.TrainingPlan
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Local workout reminders: for each planned session with a startTimeLocal,
 * exact alarms fire 60 and 30 minutes before. No server involved — alarms
 * are re-armed whenever the plan changes; armed request codes are stored
 * in SharedPreferences so stale alarms are cancelled on re-sync.
 */
object PlanReminders {
    const val CHANNEL_ID = "plan_reminders"
    const val ACTION_REMIND = "com.nobelle.gymhealth.PLAN_REMIND"
    private const val PREFS = "plan_reminders"
    private const val KEY_ARMED = "armed_ids"
    private const val EXTRA_TITLE = "title"
    private const val EXTRA_BODY = "body"
    private const val EXTRA_NOTIFICATION_ID = "nid"

    private fun alarm(context: Context) =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, "Workout reminders", NotificationManager.IMPORTANCE_HIGH,
                ),
            )
        }
    }

    /** Rearm all reminders for the current plan. Call after any plan change. */
    fun sync(context: Context, plan: TrainingPlan?) {
        val alarms = alarm(context)
        val now = LocalDateTime.now()
        val zone = ZoneId.systemDefault()

        cancelAll(context, alarms)
        val armed = mutableSetOf<String>()

        plan?.plannedSessions
            ?.filter { it.status == PlannedSession.Status.PLANNED && it.startTimeLocal != null }
            ?.forEach { session ->
                val time = runCatching { LocalTime.parse(session.startTimeLocal) }
                    .getOrNull() ?: return@forEach
                val date = runCatching { LocalDate.parse(session.localDate) }
                    .getOrNull() ?: return@forEach
                val at = LocalDateTime.of(date, time)
                listOf(60L to "in 1 hour", 30L to "in 30 minutes").forEachIndexed { i, (min, label) ->
                    val trigger = at.minusMinutes(min)
                    if (trigger.isAfter(now)) {
                        val nid = schedule(context, alarms, session, label,
                            trigger.atZone(zone).toInstant().toEpochMilli(), i)
                        armed += nid.toString()
                    }
                }
            }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_ARMED, armed).apply()
    }

    private fun schedule(
        context: Context,
        alarms: AlarmManager,
        session: PlannedSession,
        label: String,
        triggerMs: Long,
        slot: Int,
    ): Int {
        val nid = 31 * session.id.hashCode() + slot
        val intent = Intent(context, PlanReminderReceiver::class.java)
            .setAction(ACTION_REMIND)
            .putExtra(EXTRA_TITLE, "${session.category.replace('_', ' ')} $label")
            .putExtra(EXTRA_BODY, "Planned session starts at ${session.startTimeLocal}.")
            .putExtra(EXTRA_NOTIFICATION_ID, nid)
        val pending = PendingIntent.getBroadcast(
            context, nid, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pending)
        } catch (e: SecurityException) {
            // SCHEDULE_EXACT_ALARM denied — fall back to inexact delivery.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pending)
        }
        return nid
    }

    private fun cancelAll(context: Context, alarms: AlarmManager) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getStringSet(KEY_ARMED, emptySet())?.forEach { idString ->
            val nid = idString.toIntOrNull() ?: return@forEach
            val pending = PendingIntent.getBroadcast(
                context, nid,
                Intent(context, PlanReminderReceiver::class.java).setAction(ACTION_REMIND),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarms.cancel(pending)
            pending.cancel()
        }
        prefs.edit().remove(KEY_ARMED).apply()
    }
}

class PlanReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PlanReminders.ACTION_REMIND) return
        if (Build.VERSION.SDK_INT >= 26) {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
            mgr.createNotificationChannel(
                NotificationChannel(
                    PlanReminders.CHANNEL_ID, "Workout reminders",
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            )
        }
        val title = intent.getStringExtra("title") ?: "Workout"
        val body = intent.getStringExtra("body") ?: ""
        val nid = intent.getIntExtra("nid", 0)
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, PlanReminders.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .build()
        try {
            mgr.notify(nid, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS denied — reminder is skipped silently.
        }
    }
}
