package app.timecapsule.drive

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import app.timecapsule.MainActivity
import app.timecapsule.R
import app.timecapsule.TimeCapsuleApp

/** Started from the visible parked UI. Preparation does not open the microphone. */
class DriveService : Service() {
    private val drive get() = (application as TimeCapsuleApp).drive
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { drive.endDrive(); stopSelf(); return START_NOT_STICKY }
        if (intent?.action != PREPARE) { stopSelf(); return START_NOT_STICKY }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "이번 주행", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 40, Intent(this, DriveService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 41, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome).setContentTitle("타임캡슐 주행 준비됨")
            .setContentText("요청할 때만 녹음해요. 이번 주행을 종료할 수 있어요")
            .setContentIntent(open).setOngoing(true).addAction(0, "이번 주행 종료", stop).build()
        try {
            if (Build.VERSION.SDK_INT >= 30) startForeground(40, n,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            else startForeground(40, n)
            // startForeground always comes first: a started-but-not-foreground service crashes.
            if (drive.prepared.value) return START_NOT_STICKY
            if (!drive.completePreparation()) stopSelf()
        } catch (_: Exception) { drive.endDrive(); stopSelf() }
        return START_NOT_STICKY
    }
    override fun onTaskRemoved(rootIntent: Intent?) { drive.endDrive(); stopSelf() }
    override fun onDestroy() { drive.endDrive(); super.onDestroy() }
    companion object {
        const val PREPARE = "app.timecapsule.PREPARE"
        const val STOP = "app.timecapsule.END_DRIVE"
        const val CHANNEL = "drive_session"
    }
}
