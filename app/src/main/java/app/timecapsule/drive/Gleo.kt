package app.timecapsule.drive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.timecapsule.TimeCapsuleApp
import org.json.JSONObject

/**
 * Gleo AI voice actions (assets/schema.json). "타임캡슐 녹음해 줘" reaches MainActivity as
 * START_CAPSULE_RECORDING (component "activity", so the mic is used with the app in front);
 * save and cancel arrive here as broadcasts (component "service"). Every call is answered with
 * Gleo's APP_ACTION_RESPONSE broadcast.
 * Docs: document.pleos.ai/docs/pleos-only/app-integration-guide/guide/{app-capability,examples,deliver-schema}
 */
object Gleo {
    const val START = "START_CAPSULE_RECORDING"
    const val SAVE = "SAVE_CAPSULE_RECORDING"
    const val CANCEL = "CANCEL_CAPSULE_RECORDING"
    private const val RESPONSE = "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE"

    fun reply(context: Context, function: String, ok: Boolean, message: String) {
        val body = JSONObject()
            .put("functionName", function)
            .put("status", if (ok) "success" else "fail")
            .put("message", message)
            .put("data", "{}")
        Log.i("Gleo", "$function -> ${if (ok) "success" else "fail"}: $message")
        runCatching {
            context.sendBroadcast(Intent(RESPONSE).putExtra("packageName", context.packageName).putExtra("response", body.toString()))
        }
    }
}

/** SAVE / CANCEL from Gleo while a capsule is being recorded. */
class GleoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val drive = (context.applicationContext as TimeCapsuleApp).drive
        val recording = drive.state.value == DriveState.Recording
        when (intent.action) {
            Gleo.SAVE -> {
                if (recording) drive.stopRecording()
                Gleo.reply(context, Gleo.SAVE, recording, if (recording) "Saved the voice capsule." else "Nothing is being recorded.")
            }
            Gleo.CANCEL -> {
                if (recording) drive.cancelRecording()
                Gleo.reply(context, Gleo.CANCEL, recording, if (recording) "Discarded the recording." else "Nothing is being recorded.")
            }
        }
    }
}
