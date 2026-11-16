package com.syhung.androidaiautomation

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock

class SchedulerBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences("scheduler_state", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("enabled", false)) return

        val profileName = prefs.getString("profile_name", null)?.takeIf { it.isNotBlank() } ?: return
        val recurring = prefs.getBoolean("recurring", false)
        val intervalMinutes = prefs.getLong("interval_minutes", 0L)
        if (recurring && intervalMinutes !in 15L..10_080L) return

        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = prefs.getLong("trigger_at", 0L).let { saved ->
            if (saved > System.currentTimeMillis()) saved
            else if (recurring) System.currentTimeMillis() + intervalMinutes * 60_000L
            else System.currentTimeMillis() + 60_000L
        }
        val pending = PendingIntent.getBroadcast(
            context,
            7301,
            Intent(context, SchedulerReceiver::class.java)
                .putExtra("profile_name", profileName)
                .putExtra("recurring", recurring),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (recurring) {
            alarm.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                intervalMinutes * 60_000L,
                pending
            )
        } else {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
        prefs.edit().putLong("trigger_at", triggerAt).apply()
    }
}
