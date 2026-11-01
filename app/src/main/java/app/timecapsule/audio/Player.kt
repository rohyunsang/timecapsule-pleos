package app.timecapsule.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * F-05 playback: spoken narration, then the original voice.
 *
 * Narration uses the Pleos on-device TTS on a car (Android TTS elsewhere, see Speech); when no
 * engine is installed the narration is skipped and only shown on screen.
 *
 * Audio focus doubles as the "navigation is speaking" signal: when the navigation app takes
 * transient focus we get [navigationSpeaking] = true and the drive controller pauses us.
 */
class Player(context: Context) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val audio = app.getSystemService(AudioManager::class.java)
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    /** Pleos on-device TTS on a car, Android TTS elsewhere (see Speech). */
    private val speech: Speech = Speeches.create(app, attrs)
    val speechName get() = speech.name

    private val _navigationSpeaking = MutableStateFlow(false)
    val navigationSpeaking: StateFlow<Boolean> = _navigationSpeaking.asStateFlow()

    private enum class Phase { IDLE, NARRATION, VOICE, PAUSED_NARRATION, PAUSED_VOICE }
    private var phase = Phase.IDLE
    private var media: MediaPlayer? = null
    private var narration: String = ""
    private var audioPath: String = ""
    private var onFinished: () -> Unit = {}
    private var focus: AudioFocusRequest? = null

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> _navigationSpeaking.value = true
            AudioManager.AUDIOFOCUS_GAIN -> _navigationSpeaking.value = false
            AudioManager.AUDIOFOCUS_LOSS -> { _navigationSpeaking.value = false; stop() }
        }
    }

    val isActive get() = phase != Phase.IDLE

    fun progress(): Pair<Int, Int>? {
        if (phase != Phase.VOICE && phase != Phase.PAUSED_VOICE) return null
        val m = media ?: return null
        return runCatching { m.currentPosition to m.duration }.getOrNull()?.takeIf { it.second > 0 }
    }

    /** Starts from the beginning: narration, then the recording at [path]. */
    fun play(narrationText: String, path: String, done: () -> Unit) {
        stopInternal()
        narration = narrationText
        audioPath = path
        onFinished = done
        requestFocus()
        startNarration()
    }

    fun pause() {
        when (phase) {
            Phase.NARRATION -> { phase = Phase.PAUSED_NARRATION; speech.stop() }
            Phase.VOICE -> { media?.pause(); phase = Phase.PAUSED_VOICE }
            else -> {}
        }
    }

    /** Continues after a safety pause; narration restarts, the voice continues where it stopped. */
    fun resume() {
        when (phase) {
            Phase.PAUSED_NARRATION -> startNarration()
            Phase.PAUSED_VOICE -> { media?.start(); phase = Phase.VOICE }
            else -> {}
        }
    }

    fun restart() = play(narration, audioPath, onFinished)

    fun stop() {
        stopInternal()
        abandonFocus()
    }

    /** One-off spoken line (confirmations, F-03 forecast, F-04 alert). */
    fun say(text: String, onDone: () -> Unit = {}) {
        if (speech.ready && phase == Phase.IDLE) speech.speak(text, onDone) else onDone()
    }

    fun release() {
        stop()
        speech.release()
    }

    private fun startNarration() {
        if (speech.ready && narration.isNotBlank()) {
            phase = Phase.NARRATION
            speech.speak(narration) { if (phase == Phase.NARRATION) startVoice(0) }
        } else {
            startVoice(0)
        }
    }

    private fun startVoice(fromMs: Int) {
        media?.release()
        media = try {
            MediaPlayer().apply {
                setAudioAttributes(attrs)
                setDataSource(audioPath)
                setOnCompletionListener { finished() }
                prepare()
                if (fromMs > 0) seekTo(fromMs)
                start()
            }
        } catch (e: Exception) {
            Log.w(TAG, "cannot play $audioPath", e)
            finished()
            return
        }
        phase = Phase.VOICE
    }

    private fun finished() {
        stopInternal()
        abandonFocus()
        onFinished()
    }

    private fun stopInternal() {
        if (phase == Phase.NARRATION) speech.stop()
        media?.release()
        media = null
        phase = Phase.IDLE
    }

    private fun requestFocus() {
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attrs)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(focusListener, main)
            .build()
        focus = req
        audio.requestAudioFocus(req)
    }

    private fun abandonFocus() {
        focus?.let { audio.abandonAudioFocusRequest(it) }
        focus = null
        _navigationSpeaking.value = false
    }

    companion object {
        private const val TAG = "Player"
    }
}
