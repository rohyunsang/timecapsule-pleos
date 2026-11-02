package app.timecapsule.drive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import app.timecapsule.MainActivity

/**
 * Keeps the app alive behind navigation so an approach alert can come while the map is in
 * front, also right after the car starts (BootReceiver). It does no work of its own: the
 * drive loop lives in TimeCapsuleApp; this only holds the process with a quiet notification.
 * Type specialUse ("길 위 캡슐 알림"), which Android allows to start from BOOT_COMPLETED.
 */
class DriveService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = notification()
        runCatching {
            if (Build.VERSION.SDK_INT >= 34) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(ID, n)
            Log.i(TAG, "running (from ${intent?.getStringExtra(FROM) ?: "app"})")
        }.onFailure { Log.w(TAG, "cannot start in foreground", it); stopSelf() }
        return START_STICKY
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "길 위 캡슐 알림 대기", NotificationManager.IMPORTANCE_MIN))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("타임캡슐")
            .setContentText("캡슐이 있는 길에 오면 알려 드려요")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "DriveService"
        private const val CHANNEL = "resident"
        private const val ID = 7
        private const val FROM = "from"

        fun start(context: Context, from: String) {
            runCatching {
                context.startForegroundService(Intent(context, DriveService::class.java).putExtra(FROM, from))
            }.onFailure { Log.w(TAG, "start failed ($from)", it) }
        }
    }
}

/** Car started: begin waiting for capsules without the driver opening the app. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            DriveService.start(context, "boot")
        }
    }
}
