package io.timecapsule.vehicle

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONObject

/** System media permission is required in the manifest; never expose an unguarded microphone trigger.
 * Gleo sender permission and schema approval must be validated by the platform before voice rollout.
 */
class GleoRecordingReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        val e=CapsuleEngine.get(context)
        val action=intent.action ?: return
        val success=when(action) {
            "START_CAPSULE_RECORDING"->e.recordCommand()
            "SAVE_CAPSULE_RECORDING"->e.recording.also { if(it)e.finishRecording(true) }
            "CANCEL_CAPSULE_RECORDING"->e.recording.also { if(it)e.finishRecording(false) }
            else->return
        }
        val response=JSONObject().put("functionName",action).put("status",if(success)"success" else "fail")
            .put("message",if(success)"The recording command was handled." else "Recording is unavailable. Prepare recording while parked and check the vehicle state.")
            .put("mode",if(success)"silent" else "voice")
        context.sendBroadcast(Intent("ai.pleos.playground.intent.action.APP_ACTION_RESPONSE")
            .putExtra("packageName",context.packageName).putExtra("response",response.toString()),"android.permission.MEDIA_CONTENT_CONTROL")
    }
}
