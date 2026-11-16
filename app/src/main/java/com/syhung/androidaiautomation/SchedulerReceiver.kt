package com.syhung.androidaiautomation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class SchedulerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "automation_cooldown"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Cooldown reminders", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val profileName = intent?.getStringExtra("profile_name").orEmpty()
        context.getSharedPreferences("scheduler_state", Context.MODE_PRIVATE).edit().clear().apply()
        val openApp = PendingIntent.getActivity(
            context,
            7302,
            Intent(context, MainActivity::class.java).putExtra("scheduled_profile_name", profileName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Automation cooldown finished")
            .setContentText(if (profileName.isBlank()) "Review your workflow and choose whether to continue." else "Scheduled profile '$profileName' is ready for review.")
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        manager.notify(7302, notification)
    }
}
