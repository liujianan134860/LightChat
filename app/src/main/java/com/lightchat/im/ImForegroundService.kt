package com.lightchat.im

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.lightchat.LightChatApplication
import com.lightchat.MainActivity
import com.lightchat.R

class ImForegroundService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = LightChatApplication.instance
        val notifyIntent = Intent(this, MainActivity::class.java)
        notifyIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notifyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("LightChat")
            .setContentText("正在后台保持连接")
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        Log.d("LightChatIM", "Foreground service started")

        timeoutRunnable?.let { handler.removeCallbacks(it) }
        timeoutRunnable = Runnable {
            Log.d("LightChatIM", "Foreground service: 1-minute timeout, disconnecting")
            app.imClient.disconnect()
            stopSelf()
        }
        handler.postDelayed(timeoutRunnable!!, BACKGROUND_TIMEOUT_MS)

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        timeoutRunnable?.let { handler.removeCallbacks(it) }
        timeoutRunnable = null
        Log.d("LightChatIM", "Foreground service destroyed")
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "lightchat_connection"
        private const val NOTIFICATION_ID = 0x494D434E
        private const val BACKGROUND_TIMEOUT_MS = 60_000L

        fun start(context: Context) {
            val intent = Intent(context, ImForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ImForegroundService::class.java))
        }
    }
}
