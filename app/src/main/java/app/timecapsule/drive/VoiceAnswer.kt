package app.timecapsule.drive

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/** What the driver said back to an approach alert. */
enum class Answer { LISTEN, LATER }

object AnswerParser {
    private val listen = listOf("들려", "들을", "재생", "틀어", "켜 줘", "켜줘", "응", "그래", "좋아", "네")
    private val later = listOf("다음", "나중", "아니", "됐어", "괜찮", "싫어", "말고", "그만", "안 들", "안들")

    /** "다음에" wins over "응" in "응 다음에"; nothing recognised gives null. */
    fun parse(text: String): Answer? {
        val t = " ${text.trim()} "
        return when {
            later.any { it in t } -> Answer.LATER
            listen.any { it in t } -> Answer.LISTEN
            else -> null
        }
    }
}

/**
 * Hands-free answer to the approach alert with the Pleos on-device speech recognizer: after
 * the alert line has been read out, the mic listens once, only while the alert is open, for
 * "들려줘" or "다음에". It never listens at any other time (the recorder owns the mic then).
 */
class VoiceAnswer(context: Context, private val scope: CoroutineScope, private val drive: DriveController) {
    private val main = Handler(Looper.getMainLooper())
    private var stt: ai.pleos.playground.stt.SpeechToText? = null
    private var ready = false
    private var target: Long? = null

    private val _listening = MutableStateFlow(false)
    /** True while the mic is open for an answer (shown on the alert card). */
    val listening: StateFlow<Boolean> = _listening.asStateFlow()

    private val results = object : ai.pleos.playground.stt.listener.ResultListener {
        override fun onReady() {}
        override fun onStartedRecognition() = main.post { _listening.value = true }.let {}
        override fun onUpdated(text: String, completed: Boolean) = main.post { heard(text, completed) }.let {}
        override fun onEndedRecognition() = main.post { _listening.value = false }.let {}
        override fun onError(errorType: ai.pleos.playground.stt.constant.ErrorType) =
            main.post { Log.w(TAG, "error $errorType"); _listening.value = false }.let {}
        override fun onUpdatedEpdData(on: Long, off: Long) {}
        override fun onUpdatedRms(rms: Double) {}
    }

    fun start(context: Context) {
        val installed = runCatching { context.packageManager.getPackageInfo("ai.pleos.playground.caas", 0) }.isSuccess
        if (!installed) return
        runCatching {
            val s = ai.pleos.playground.stt.SpeechToText(context.applicationContext, ai.pleos.playground.stt.constant.Mode.ON_DEVICE)
            stt = s
            s.initialize(object : ai.pleos.playground.stt.listener.OnInitializeListener {
                override fun onSuccess() = main.post {
                    runCatching { s.setLocale(Locale.KOREA); s.addListener(results); ready = true; Log.i(TAG, "ready (on-device, ko-KR)") }
                        .onFailure { Log.w(TAG, "setup failed", it) }
                }.let {}
                override fun onFailure(errorType: ai.pleos.playground.stt.constant.ErrorType) =
                    main.post { Log.w(TAG, "init failed $errorType") }.let {}
            })
        }.onFailure { Log.w(TAG, "unavailable", it) }

        scope.launch {
            drive.answerWindow.collect { id ->
                if (id != null) open(id) else close()
            }
        }
    }

    val available get() = ready

    private fun open(id: Long) {
        val s = stt ?: return
        if (!ready) return
        target = id
        Log.i(TAG, "listening for an answer to capsule $id")
        _listening.value = true
        runCatching { s.request() }.onFailure { Log.w(TAG, "request failed", it) }
    }

    private fun close() {
        if (target == null) return
        target = null
        _listening.value = false
        runCatching { stt?.stop() }
    }

    private fun heard(text: String, completed: Boolean) {
        val id = target ?: return
        val answer = AnswerParser.parse(text) ?: return
        if (!completed && answer == Answer.LISTEN && text.trim().length <= 1) return // wait for more than "어"
        Log.i(TAG, "heard \"$text\" -> $answer")
        close()
        if (drive.state.value != DriveState.ApproachAlert(id)) return
        when (answer) {
            Answer.LISTEN -> drive.listen()
            Answer.LATER -> drive.later()
        }
    }

    private companion object { const val TAG = "VoiceAnswer" }
}
