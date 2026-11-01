package app.timecapsule.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * F-05 playback: spoken narration, then the original voice.
 *
 * Narration uses Android TextToSpeech because the Pleos TTS SDK is not released yet; when no
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

    private var ttsReady = false

    // The init callback can fire synchronously inside the constructor (engine missing),
    // before `tts` is assigned, so handle it on the next main-loop tick.
    private val tts: TextToSpeech = TextToSpeech(app) { status -> main.post { onTtsInit(status) } }

    private fun onTtsInit(status: Int) {
        val lang = if (status == TextToSpeech.SUCCESS) tts.setLanguage(Locale.KOREAN) else TextToSpeech.LANG_NOT_SUPPORTED
        ttsReady = lang >= TextToSpeech.LANG_AVAILABLE
        Log.i(TAG, "TTS status=$status korean=$lang ready=$ttsReady")
        if (ttsReady) tts.setAudioAttributes(attrs)
    }

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

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                if (id == NARRATION_ID) main.post { if (phase == Phase.NARRATION) startVoice(0) }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) = onDone(id)
        })
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
            Phase.NARRATION -> { tts.stop(); phase = Phase.PAUSED_NARRATION }
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
    fun say(text: String) {
        if (ttsReady && phase == Phase.IDLE) tts.speak(text, TextToSpeech.QUEUE_ADD, null, "say")
    }

    fun release() {
        stop()
        tts.shutdown()
    }

    private fun startNarration() {
        if (ttsReady && narration.isNotBlank()) {
            phase = Phase.NARRATION
            tts.speak(narration, TextToSpeech.QUEUE_FLUSH, null, NARRATION_ID)
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
        if (phase == Phase.NARRATION) tts.stop()
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
        private const val NARRATION_ID = "narration"
    }
}
