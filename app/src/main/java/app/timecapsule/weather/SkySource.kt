package app.timecapsule.weather

import android.os.SystemClock
import android.util.Log
import app.timecapsule.geo.Geo
import app.timecapsule.geo.GeoPoint
import app.timecapsule.navi.NaviPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** The sky at the car, saved with a capsule so a later listener sees the real weather of that day. */
enum class Sky { CLEAR, PARTLY, CLOUDY, FOG, RAIN, SNOW, STORM;

    companion object {
        /** WMO weather interpretation codes (Open-Meteo `weather_code`). */
        fun fromWmo(code: Int): Sky = when (code) {
            0, 1 -> CLEAR
            2 -> PARTLY
            3 -> CLOUDY
            45, 48 -> FOG
            in 51..67, in 80..82 -> RAIN
            in 71..77, 85, 86 -> SNOW
            in 95..99 -> STORM
            else -> CLOUDY
        }
    }
}

/**
 * Current weather from Open-Meteo (free, no key). Only a rough position leaves the car: the
 * coordinates are rounded to 0.1 degree (about 10 km). Refreshed every 20 minutes or after
 * moving 10 km; offline it keeps the last value, and a capsule simply has no sky then.
 * The wiper stays the car's own proof of rain (see TheDay).
 */
class SkySource(private val scope: CoroutineScope, private val position: StateFlow<NaviPosition?>) {
    private val _sky = MutableStateFlow<Sky?>(null)
    val sky: StateFlow<Sky?> = _sky.asStateFlow()

    private var lastAt = 0L
    private var lastPoint: GeoPoint? = null

    fun start() {
        scope.launch {
            while (true) {
                position.value?.point?.let { p ->
                    val stale = SystemClock.elapsedRealtime() - lastAt > REFRESH_MS
                    val moved = lastPoint?.let { Geo.distanceM(it, p) > MOVE_M } ?: true
                    if (stale || moved) fetch(p)
                }
                delay(30_000)
            }
        }
    }

    private suspend fun fetch(p: GeoPoint) {
        val lat = "%.1f".format(java.util.Locale.US, p.lat)
        val lng = "%.1f".format(java.util.Locale.US, p.lng)
        val code = withContext(Dispatchers.IO) {
            runCatching {
                val c = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lng&current=weather_code")
                    .openConnection() as HttpURLConnection
                c.connectTimeout = 5000; c.readTimeout = 5000
                c.inputStream.bufferedReader().use { it.readText() }.let { body ->
                    Regex("\"current\"\\s*:\\s*\\{[^}]*\"weather_code\"\\s*:\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toInt()
                }
            }.onFailure { Log.w(TAG, "weather failed: ${it.message}") }.getOrNull()
        } ?: return
        _sky.value = Sky.fromWmo(code)
        lastAt = SystemClock.elapsedRealtime(); lastPoint = p
        Log.i(TAG, "sky ${_sky.value} (code $code) near $lat,$lng")
    }

    private companion object {
        const val TAG = "SkySource"
        const val REFRESH_MS = 20 * 60_000L
        const val MOVE_M = 10_000.0
    }
}
