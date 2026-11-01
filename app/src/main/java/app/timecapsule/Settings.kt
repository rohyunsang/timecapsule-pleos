package app.timecapsule

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/**
 * F-13 settings. Recording tones are deliberately not here: they cannot be switched off.
 * Account sync is P2, so storage is always "차량만".
 */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _arrivalPrompt = MutableStateFlow(prefs.getBoolean(KEY_ARRIVAL, true))
    val arrivalPrompt: StateFlow<Boolean> = _arrivalPrompt.asStateFlow()

    fun setArrivalPrompt(on: Boolean) {
        prefs.edit().putBoolean(KEY_ARRIVAL, on).apply()
        _arrivalPrompt.value = on
    }

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
        const val KEY_PROMPT_DAY = "arrival_prompt_day"
        const val KEY_PROMPT_COUNT = "arrival_prompt_count"
    }
}
