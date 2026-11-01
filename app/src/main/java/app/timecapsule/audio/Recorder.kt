package app.timecapsule.audio

import android.content.Context
import android.media.AudioManager
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.Build
import android.os.SystemClock
import android.util.Log
import app.timecapsule.capsule.Rules
import java.io.File

/**
 * F-13: tones that mark recording start and end. There is no setting to turn them off,
 * so a passenger always knows when the microphone is live.
 */
class Beeper {
    private val tone = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90) }.getOrNull()

    fun recordStart() = tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
    fun recordEnd() = tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 250)
    fun alert() = tone?.startTone(ToneGenerator.TONE_PROP_ACK, 300)
    fun release() = tone?.release()
}

data class Recording(val file: File, val durationSec: Int)

/** F-01: AAC recording into app-private storage, capped at 30 s. */
class Recorder(private val context: Context, private val beeper: Beeper) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    val isRecording get() = recorder != null

    /** How loud the mic is right now, 0..1 (peak since the last call), for the recording screen. */
    fun level(): Float {
        val amp = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
        // Speech peaks sit around 2k-20k; a square root makes quiet talk still visible.
        return kotlin.math.sqrt((amp / 20_000f).coerceIn(0f, 1f))
    }

    /** Starts recording; [onLimit] fires on the main thread when 30 s is reached. */
    fun start(onLimit: () -> Unit): Boolean {
        if (recorder != null) return true
        val dir = File(context.filesDir, "capsules").apply { mkdirs() }
        val out = File(dir, "capsule_${System.currentTimeMillis()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(44_100)
            r.setAudioEncodingBitRate(64_000) // voice; keeps a 30 s clip ~240 KB for upload
            r.setMaxDuration(Rules.MAX_RECORD_SEC * 1000)
            r.setOutputFile(out.absolutePath)
            r.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) onLimit()
            }
            r.prepare()
            beeper.recordStart()
            r.start()
            recorder = r
            file = out
            startedAt = SystemClock.elapsedRealtime()
            true
        } catch (e: Exception) {
            Log.w(TAG, "cannot record", e)
            r.release()
            out.delete()
            false
        }
    }

    /** Stops and returns the recording, or null when it was shorter than 3 s (spec F-01). */
    fun stop(): Recording? {
        val r = recorder ?: return null
        val out = file
        val sec = ((SystemClock.elapsedRealtime() - startedAt) / 1000).toInt().coerceAtMost(Rules.MAX_RECORD_SEC)
        recorder = null
        file = null
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        beeper.recordEnd()
        if (!ok || out == null || sec < Rules.MIN_RECORD_SEC) {
            out?.delete()
            return null
        }
        return Recording(out, sec)
    }

    fun cancel() {
        recorder?.let { runCatching { it.stop() }; it.release() }
        file?.delete()
        recorder = null
        file = null
    }

    companion object { private const val TAG = "Recorder" }
}
