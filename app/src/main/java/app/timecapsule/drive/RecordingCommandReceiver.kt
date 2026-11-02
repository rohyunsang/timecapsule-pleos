package app.timecapsule.drive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.timecapsule.TimeCapsuleApp

/** System-authorized bridge; actual Gleo sender/schema compatibility needs device verification. */
class RecordingCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val drive = (context.applicationContext as TimeCapsuleApp).drive
        when (intent.action) {
            "app.timecapsule.RECORD_START" -> drive.bury()
            "app.timecapsule.RECORD_SAVE" -> drive.stopRecording()
            "app.timecapsule.RECORD_CANCEL" -> drive.cancelRecording()
        }
    }
}
