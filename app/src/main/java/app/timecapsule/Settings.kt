package app.timecapsule

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/**
 * F-13 settings. Recording tones are deliberately not here: they cannot be switched off.
 * [defaultAudience]: who a capsule is for when nobody is picked (while driving there is no
 * picking): all friends, or only me (stays in this car).
 */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _arrivalPrompt = MutableStateFlow(prefs.getBoolean(KEY_ARRIVAL, true))
    val arrivalPrompt: StateFlow<Boolean> = _arrivalPrompt.asStateFlow()

    fun setArrivalPrompt(on: Boolean) {
        prefs.edit().putBoolean(KEY_ARRIVAL, on).apply()
        _arrivalPrompt.value = on
    }

    private val _defaultAudience = MutableStateFlow(prefs.getString(KEY_AUDIENCE, "SELF") ?: "SELF")
    val defaultAudience: StateFlow<String> = _defaultAudience.asStateFlow()

    fun setDefaultAudience(a: String) {
        prefs.edit().putString(KEY_AUDIENCE, a).apply()
        _defaultAudience.value = a
    }

    /** Longest a capsule may run, in seconds (one of [Rules.RECORD_LENGTHS]). */
    private val _maxRecordSec = MutableStateFlow(
        prefs.getInt(KEY_MAX_SEC, app.timecapsule.capsule.Rules.DEFAULT_RECORD_SEC)
            .takeIf { it in app.timecapsule.capsule.Rules.RECORD_LENGTHS } ?: app.timecapsule.capsule.Rules.DEFAULT_RECORD_SEC,
    )
    val maxRecordSec: StateFlow<Int> = _maxRecordSec.asStateFlow()
    fun setMaxRecordSec(sec: Int) {
        if (sec !in app.timecapsule.capsule.Rules.RECORD_LENGTHS) return
        prefs.edit().putInt(KEY_MAX_SEC, sec).apply(); _maxRecordSec.value = sec
    }

    /** Seconds of quiet after which the recording saves itself; 0 = only by button. */
    private val _silenceSec = MutableStateFlow(
        prefs.getInt(KEY_SILENCE, app.timecapsule.capsule.Rules.DEFAULT_SILENCE_SEC)
            .takeIf { it in app.timecapsule.capsule.Rules.SILENCE_CHOICES } ?: app.timecapsule.capsule.Rules.DEFAULT_SILENCE_SEC,
    )
    val silenceSec: StateFlow<Int> = _silenceSec.asStateFlow()
    fun setSilenceSec(sec: Int) {
        if (sec !in app.timecapsule.capsule.Rules.SILENCE_CHOICES) return
        prefs.edit().putInt(KEY_SILENCE, sec).apply(); _silenceSec.value = sec
    }

    /** Friends' capsules the user removed from this car; sync must not bring them back. */
    fun hidden(): Set<String> = prefs.getStringSet(KEY_HIDDEN, emptySet()).orEmpty()
    fun hide(remoteId: String) { prefs.edit().putStringSet(KEY_HIDDEN, hidden() + remoteId).apply() }

    /** F-09: at most N prompts a day. */
    fun takeArrivalPrompt(today: LocalDate, perDay: Int): Boolean {
        val day = today.toString()
        val used = if (prefs.getString(KEY_PROMPT_DAY, null) == day) prefs.getInt(KEY_PROMPT_COUNT, 0) else 0
        if (used >= perDay) return false
        prefs.edit().putString(KEY_PROMPT_DAY, day).putInt(KEY_PROMPT_COUNT, used + 1).apply()
        return true
    }

    private companion object {
        const val KEY_ARRIVAL = "arrival_prompt"
        const val KEY_AUDIENCE = "default_audience"
        const val KEY_HIDDEN = "hidden_remote"
        const val KEY_MAX_SEC = "max_record_sec"
        const val KEY_SILENCE = "silence_sec"
        const val KEY_PROMPT_DAY = "arrival_prompt_day"
        const val KEY_PROMPT_COUNT = "arrival_prompt_count"
    }
}
