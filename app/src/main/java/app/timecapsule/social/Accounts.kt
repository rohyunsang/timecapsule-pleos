package app.timecapsule.social

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * The account signed in on this car, plus accounts used here before (dev panel switching, so
 * one emulator can play both "me" and "my friend"). Stored in app-private prefs.
 */
class Accounts(context: Context, name: String) {
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    private val _current = MutableStateFlow(read(prefs.getString(KEY_CURRENT, null)))
    val current: StateFlow<Profile?> = _current.asStateFlow()

    private val _known = MutableStateFlow(readList(prefs.getString(KEY_KNOWN, null)))
    val known: StateFlow<List<Profile>> = _known.asStateFlow()

    fun set(p: Profile?) {
        _current.value = p
        if (p != null && _known.value.none { it.uid == p.uid }) _known.value = _known.value + p
        prefs.edit()
            .putString(KEY_CURRENT, p?.let { json(it).toString() })
            .putString(KEY_KNOWN, JSONArray(_known.value.map(::json)).toString())
            .apply()
    }

    private fun json(p: Profile) = JSONObject().put("uid", p.uid).put("name", p.name).put("code", p.code)

    private fun read(s: String?): Profile? = s?.let {
        runCatching { JSONObject(it).let { o -> Profile(o.getString("uid"), o.getString("name"), o.getString("code")) } }.getOrNull()
    }

    private fun readList(s: String?): List<Profile> = s?.let {
        runCatching {
            val a = JSONArray(it)
            (0 until a.length()).mapNotNull { i -> read(a.getJSONObject(i).toString()) }
        }.getOrNull()
    } ?: emptyList()

    private companion object {
        const val KEY_CURRENT = "current"
        const val KEY_KNOWN = "known"
    }
}
