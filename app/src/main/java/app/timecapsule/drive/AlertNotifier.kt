package app.timecapsule.drive

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import app.timecapsule.MainActivity
import app.timecapsule.R
import app.timecapsule.TimeCapsuleApp
import app.timecapsule.capsule.Rules
import app.timecapsule.ui.alertDescription
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Most of the time the app is not on screen: navigation is. So the approach alert must also
 * work as an overlay on top of navigation. When the app is not visible, the alert is posted
 * as a heads-up notification (drawn by Pleos as a System Notification: icon, title,
 * description, CTA + sub action), and playback continues with the map left in place.
 */
class AlertNotifier(
    private val context: Context,
    private val scope: CoroutineScope,
    private val drive: DriveController,
    private val appVisible: StateFlow<Boolean>,
) {
    private val nm = NotificationManagerCompat.from(context)
    private val overlay = OverlayAlert(context)

    fun start() {
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "가까운 캡슐 알림", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "예전에 묻은 캡슐에 가까워지면 알려 줘요"
                setSound(null, null) // The app plays its own short chime; the map's voice wins.
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PLAYING, "듣는 중", NotificationManager.IMPORTANCE_LOW),
        )
        scope.launch {
            combine(drive.alert, appVisible) { a, v -> a.takeIf { !v } }.collect { a ->
                if (a != null) {
                    postAlert(a)
                    overlay.show(a.line, alertDescription(a), onListen = { nm.cancel(ID_ALERT); drive.listen() }, onLater = { nm.cancel(ID_ALERT); drive.later() })
                } else {
                    nm.cancel(ID_ALERT)
                    overlay.hide()
                }
            }
        }
        scope.launch {
            combine(drive.state, drive.playing, appVisible) { s, p, v ->
                p.takeIf { !v && (s is DriveState.Playing || s is DriveState.Paused) }
            }.collect { p ->
                if (p != null) postPlaying(p) else nm.cancel(ID_PLAYING)
            }
        }
    }

    private fun postAlert(a: AlertUi) {
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setLargeIcon(context.packageManager.getApplicationIcon(context.packageName).toBitmap(160, 160))
            .setContentTitle(a.line)
            .setContentText(alertDescription(a))
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setTimeoutAfter(Rules.ALERT_TIMEOUT_MS)
            .setContentIntent(openApp())
            .addAction(0, "들려줘", action(ACTION_LISTEN))
            .addAction(0, "다음에", action(ACTION_LATER))
            .build()
        post(ID_ALERT, n)
    }

    private fun postPlaying(p: PlayingUi) {
        val n = NotificationCompat.Builder(context, CHANNEL_PLAYING)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("캡슐 듣는 중 · " + (p.capsule.ownerName?.let { "${it}님의 " } ?: "") + (p.capsule.placeName ?: "이 길"))
            .setContentText(p.narration)
            .setOngoing(true)
            .setContentIntent(openApp())
            .addAction(0, "그만", action(ACTION_STOP))
            .build()
        post(ID_PLAYING, n)
    }

    private fun post(id: Int, n: android.app.Notification) {
        try {
            nm.notify(id, n)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS denied: the in-app overlay still works when the app is visible.
        }
    }

    private fun openApp() = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun action(name: String) = PendingIntent.getBroadcast(
        context, name.hashCode(), Intent(context, AlertActionReceiver::class.java).setAction(name),
        PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val CHANNEL = "approach"
        const val CHANNEL_PLAYING = "playing"
        const val ID_ALERT = 1
        const val ID_PLAYING = 2
        const val ACTION_LISTEN = "app.timecapsule.LISTEN"
        const val ACTION_LATER = "app.timecapsule.LATER"
        const val ACTION_STOP = "app.timecapsule.STOP"
    }
}

/** Notification buttons. Listening keeps the map on screen; only the voice plays. */
class AlertActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val drive = (context.applicationContext as TimeCapsuleApp).drive
        when (intent.action) {
            AlertNotifier.ACTION_LISTEN -> drive.listen()
            AlertNotifier.ACTION_LATER -> drive.later()
            AlertNotifier.ACTION_STOP -> drive.stopPlayback()
        }
        NotificationManagerCompat.from(context).cancel(AlertNotifier.ID_ALERT)
    }
}
