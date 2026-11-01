package app.timecapsule.audio

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * The voice that reads the short lines (narration, confirmations, alerts).
 * On a Pleos car this is the Pleos on-device TTS (Korean, no network, no app registration);
 * elsewhere, or if it fails to start, Android's TextToSpeech.
 */
interface Speech {
    val ready: Boolean
    val name: String
    /** Speaks [text]; [onDone] runs on the main thread when it finishes, fails or is stopped. */
    fun speak(text: String, onDone: () -> Unit = {})
    fun stop()
    fun release()
}

object Speeches {
    private const val CAAS = "ai.pleos.playground.caas"

    fun create(context: Context, attrs: AudioAttributes): Speech {
        val pleosAvailable = runCatching { context.packageManager.getPackageInfo(CAAS, 0) }.isSuccess
        return if (pleosAvailable) Fallback(PleosSpeech(context, attrs)) { AndroidSpeech(context, attrs) } else AndroidSpeech(context, attrs)
    }
}

/** Uses [primary]; if it reports a failure before being ready, switches to the backup for good. */
private class Fallback(primary: PleosSpeech, private val backup: () -> Speech) : Speech {
    private var current: Speech = primary

    init {
        primary.onUnavailable = {
            Log.w("Speech", "Pleos TTS unavailable, using Android TTS")
            current = backup()
        }
    }

    override val ready get() = current.ready
    override val name get() = current.name
    override fun speak(text: String, onDone: () -> Unit) = current.speak(text, onDone)
    override fun stop() = current.stop()
    override fun release() = current.release()
}

private class PleosSpeech(context: Context, private val attrs: AudioAttributes) : Speech {
    private val main = Handler(Looper.getMainLooper())
    private val tts = ai.pleos.playground.tts.TextToSpeech(context.applicationContext, ai.pleos.playground.tts.constant.Mode.ON_DEVICE)
    private var pending: (() -> Unit)? = null
    var onUnavailable: () -> Unit = {}
    override var ready = false
        private set
    override val name = "Pleos TTS"

    private val listener = object : ai.pleos.playground.tts.listener.EventListener {
        override fun onReady() {}
        override fun onStart() { Log.i(TAG, "speaking") }
        override fun onUpdatedRms(rms: Double) {}
        override fun onDone() = main.post { Log.i(TAG, "done"); finish() }.let {}
        override fun onStop() = main.post { finish() }.let {}
        override fun onError(errorType: ai.pleos.playground.tts.constant.ErrorType) =
            main.post { Log.w(TAG, "error $errorType"); finish() }.let {}
    }

    private val timeout = Runnable { if (!ready) onUnavailable() }

    init {
        main.postDelayed(timeout, 15_000)
        runCatching {
            tts.initialize(object : ai.pleos.playground.tts.listener.OnInitializeListener {
                override fun onSuccess() = main.post {
                    runCatching {
                        tts.addEventListener(listener)
                        tts.setAudioAttributes(attrs)
                        tts.setLocale(Locale.KOREA) // the on-device engine needs ko-KR, not just ko
                        ready = true
                        main.removeCallbacks(timeout)
                        Log.i(TAG, "ready (on-device, ko-KR)")
                    }.onFailure { Log.w(TAG, "setup failed", it); onUnavailable() }
                }.let {}
                override fun onFailure(errorType: ai.pleos.playground.tts.constant.ErrorType) =
                    main.post { Log.w(TAG, "init failed $errorType"); main.removeCallbacks(timeout); onUnavailable() }.let {}
            })
        }.onFailure { Log.w(TAG, "init threw", it); main.post { onUnavailable() } }
    }

    private fun finish() {
        val done = pending ?: return
        pending = null
        done()
    }

    override fun speak(text: String, onDone: () -> Unit) {
        if (!ready) return onDone()
        // A new line replaces the one in progress; its listener is told it ended.
        val previous = pending
        pending = null
        if (previous != null) { runCatching { tts.stop() }; previous() }
        pending = onDone
        runCatching { tts.speak(text) }.onFailure { Log.w(TAG, "speak failed", it); finish() }
    }

    override fun stop() {
        pending = null
        runCatching { tts.stop() }
    }

    override fun release() {
        stop()
        runCatching { tts.removeEventListener(listener); tts.release() }
    }

    private companion object { const val TAG = "PleosSpeech" }
}

private class AndroidSpeech(context: Context, private val attrs: AudioAttributes) : Speech {
    private val main = Handler(Looper.getMainLooper())
    private var callbacks = mutableMapOf<String, () -> Unit>()
    private var seq = 0
    override var ready = false
        private set
    override val name = "Android TTS"

    // The init callback can fire synchronously inside the constructor (engine missing),
    // before `tts` is assigned, so handle it on the next main-loop tick.
    private val tts: android.speech.tts.TextToSpeech =
        android.speech.tts.TextToSpeech(context.applicationContext) { status -> main.post { onInit(status) } }

    private fun onInit(status: Int) {
        val lang = if (status == android.speech.tts.TextToSpeech.SUCCESS) tts.setLanguage(Locale.KOREAN) else android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED
        ready = lang >= android.speech.tts.TextToSpeech.LANG_AVAILABLE
        Log.i("AndroidSpeech", "status=$status korean=$lang ready=$ready")
        if (!ready) return
        tts.setAudioAttributes(attrs)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { main.post { id?.let { callbacks.remove(it)?.invoke() } } }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) = onDone(id)
            override fun onStop(id: String?, interrupted: Boolean) = onDone(id)
        })
    }

    override fun speak(text: String, onDone: () -> Unit) {
        if (!ready) return onDone()
        val id = "u${seq++}"
        callbacks[id] = onDone
        tts.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, id)
    }

    override fun stop() { callbacks.clear(); tts.stop() }
    override fun release() { stop(); tts.shutdown() }
}
