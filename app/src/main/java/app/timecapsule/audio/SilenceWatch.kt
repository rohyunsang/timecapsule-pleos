package app.timecapsule.audio

/**
 * Ends a recording by itself after the driver stops talking for [silenceMs]. Fed with the mic
 * level (0..1) a few times a second. If no voice was heard at all, the recording is empty and
 * should be thrown away rather than saved. [silenceMs] 0 = off.
 */
class SilenceWatch(private val silenceMs: Long, private val startAt: Long, private val voiceLevel: Float = VOICE) {
    var heardVoice = false; private set
    private var lastVoiceAt = startAt

    /** True once the quiet has lasted long enough. */
    fun feed(level: Float, now: Long): Boolean {
        if (level >= voiceLevel) { heardVoice = true; lastVoiceAt = now }
        return silenceMs > 0 && now - lastVoiceAt >= silenceMs
    }

    companion object {
        /** Mic level (sqrt of peak / 20000) that counts as talking, about peak 800: above cabin hum. */
        const val VOICE = 0.2f
    }
}
