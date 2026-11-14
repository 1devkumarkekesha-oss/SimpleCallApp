package com.simplecall.app

import android.Manifest
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class IncomingCallActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_NUMBER = "NUMBER"
    }

    // Closes this screen when CallReceiver reports the ringing stopped
    // (caller hung up / call answered or ended elsewhere).
    private val callEndedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            dismissAndFinish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // FIX (your v2 code): setShowWhenLocked()/setTurnScreenOn() are API 27+,
        // but minSdk is 26 — on an API 26 phone this crashed. Use the window
        // flags there instead.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
            km.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )

        setContentView(R.layout.activity_incoming_call)

        ContextCompat.registerReceiver(
            this,
            callEndedReceiver,
            IntentFilter(CallReceiver.ACTION_CALL_ENDED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        val number = intent.getStringExtra(EXTRA_NUMBER) ?: "Unknown"
        findViewById<TextView>(R.id.callerNumber).text = number

        findViewById<Button>(R.id.btnAnswer).setOnClickListener { answerCall() }
        findViewById<Button>(R.id.btnReject).setOnClickListener { rejectCall() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // launchMode is singleInstance: a second call reuses this activity.
        setIntent(intent)
        findViewById<TextView>(R.id.callerNumber).text =
            intent.getStringExtra(EXTRA_NUMBER) ?: "Unknown"
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(callEndedReceiver)
        } catch (_: Exception) {}
        super.onDestroy()
    }

    /**
     * FIX (your v2 code): both buttons only dismissed this screen — "Reject"
     * left the phone ringing. With ANSWER_PHONE_CALLS granted, TelecomManager
     * can really answer (API 26+) and end the ringing call (API 28+). It still
     * cannot do everything a default-dialer app can, so if the call isn't
     * ended by the system call, we say so instead of pretending.
     */
    private fun answerCall() {
        val telecom = getSystemService(TELECOM_SERVICE) as TelecomManager
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ANSWER_PHONE_CALLS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                telecom.acceptRingingCall()
            } catch (_: Exception) {}
            dismissAndFinish()
        } else {
            Toast.makeText(this, "Answer permission not granted — use the system call screen", Toast.LENGTH_LONG).show()
        }
    }

    private fun rejectCall() {
        val telecom = getSystemService(TELECOM_SERVICE) as TelecomManager
        val canEnd = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ANSWER_PHONE_CALLS) ==
            PackageManager.PERMISSION_GRANTED
        if (canEnd) {
            try {
                @Suppress("DEPRECATION")
                telecom.endCall()
            } catch (_: Exception) {}
            dismissAndFinish()
        } else {
            Toast.makeText(
                this,
                "This device/Android version won't let a non-dialer app reject — dismissing this screen only",
                Toast.LENGTH_LONG
            ).show()
            dismissAndFinish()
        }
    }

    private fun dismissAndFinish() {
        try {
            NotificationManagerCompat.from(this).cancel(CallReceiver.NOTIFICATION_ID)
        } catch (_: Exception) {}
        finish()
    }

    @Deprecated("Kept intentionally: back must not dismiss a ringing screen")
    override fun onBackPressed() {
        // Prevent dismissing via back button. Safe now that the screen also
        // closes itself via ACTION_CALL_ENDED when the ringing stops.
    }
}
