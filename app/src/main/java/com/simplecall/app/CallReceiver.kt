package com.simplecall.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class CallReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "incoming_call_channel"
        const val NOTIFICATION_ID = 1001
        /** Sent inside the app when ringing stops, so IncomingCallActivity can close itself. */
        const val ACTION_CALL_ENDED = "com.simplecall.app.ACTION_CALL_ENDED"

        // PHONE_STATE fires RINGING more than once for one call; without this
        // guard your v2 code re-posted the notification and re-launched the
        // activity on every repeat broadcast.
        @Volatile
        private var isRinging = false
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                if (isRinging) return
                isRinging = true
                @Suppress("DEPRECATION")
                val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                    ?.takeIf { it.isNotBlank() } ?: "Unknown"
                showIncomingCallScreen(context, number)
            }
            // FIX (your v2 code): IDLE / OFFHOOK were ignored, so the ongoing
            // full-screen notification stayed forever and IncomingCallActivity —
            // which blocks the Back button — never closed when the caller hung
            // up or the call was picked up elsewhere. The user was stuck on it.
            TelephonyManager.EXTRA_STATE_IDLE,
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                isRinging = false
                try {
                    NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
                } catch (_: Exception) {}
                context.sendBroadcast(
                    Intent(ACTION_CALL_ENDED).setPackage(context.packageName)
                )
            }
        }
    }

    private fun showIncomingCallScreen(context: Context, number: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setBypassDnd(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(channel)
        }

        val fullScreenIntent = Intent(context, IncomingCallActivity::class.java).apply {
            putExtra(IncomingCallActivity.EXTRA_NUMBER, number)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentTitle("Incoming Call")
            .setContentText(number)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {}

        // Direct launch only works while the app is in the foreground on
        // Android 10+ (background activity starts are blocked); the
        // full-screen-intent notification above is the reliable path.
        try {
            context.startActivity(fullScreenIntent)
        } catch (_: Exception) {}
    }
}
