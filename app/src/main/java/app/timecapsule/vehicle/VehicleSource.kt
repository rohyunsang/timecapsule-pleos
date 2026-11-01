package app.timecapsule.vehicle

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Car state captured at the moment a capsule is buried (spec F-02).
 * A null field means the domain could not be read; the tag engine then skips that tag.
 */
data class VehicleSnapshot(
    val wiperOn: Boolean? = null,
    val headlightOn: Boolean? = null,
    val occupiedSeats: Int? = null,
    val outsideTempC: Float? = null,
    val anyWindowOpen: Boolean? = null,
    val gearInPark: Boolean? = null,
    val anyDoorOpen: Boolean? = null,
    val onHighway: Boolean? = null,
    val placeName: String? = null,
    val odometerKm: Double? = null,
    val carModel: String? = null,
    /** Weather at the car (weather.Sky name), from the weather service; not a car signal. */
    val sky: String? = null,
) {
    /** Raw values for Capsule.rawVehicleState (spec F-02: keep raw data so tags can be recomputed). */
    fun toJson(): String {
        fun q(v: String) = "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        val fields = listOf(
            "wiperOn" to wiperOn, "headlightOn" to headlightOn, "occupiedSeats" to occupiedSeats,
            "outsideTempC" to outsideTempC, "anyWindowOpen" to anyWindowOpen, "gearInPark" to gearInPark,
            "anyDoorOpen" to anyDoorOpen, "onHighway" to onHighway, "placeName" to placeName,
            "odometerKm" to odometerKm, "carModel" to carModel, "sky" to sky,
        )
        return fields.filter { it.second != null }.joinToString(",", "{", "}") { (k, v) ->
            q(k) + ":" + if (v is String) q(v) else v.toString()
        }
    }
}

/** Signals that must pause playback or end recording (spec F-05, F-13). */
enum class SafetySignal { SAFETY_WARNING, HARD_BRAKE, TURN_SIGNAL, NAVI_VOICE }

/**
 * Everything the app reads from the car. The Pleos implementation wraps Vehicle SDK and NaviHelper;
 * the fake one lets the whole flow run in unit tests and on any device.
 */
interface VehicleSource {
    val snapshot: StateFlow<VehicleSnapshot>

    /** Signals currently active. Empty set means it is safe to play. */
    val activeSafetySignals: Flow<Set<SafetySignal>>
}

class FakeVehicleSource(initial: VehicleSnapshot = VehicleSnapshot()) : VehicleSource {
    private val _snapshot = MutableStateFlow(initial)
    private val _signals = MutableStateFlow<Set<SafetySignal>>(emptySet())

    override val snapshot: StateFlow<VehicleSnapshot> = _snapshot.asStateFlow()
    override val activeSafetySignals: StateFlow<Set<SafetySignal>> = _signals.asStateFlow()

    fun set(block: (VehicleSnapshot) -> VehicleSnapshot) = _snapshot.update(block)

    fun setSignal(signal: SafetySignal, on: Boolean) =
        _signals.update { if (on) it + signal else it - signal }
}
