package app.timecapsule.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.telltale.Telltales
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState
import ai.pleos.playground.vehicle.constant.telltale.TelltaleType
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.HvacTemperatureListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.vehicle.listener.TelltaleInfoListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.vehicle.listener.WindowPositionListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Reads the car through the Pleos Vehicle SDK (read-only APIs, spec 07).
 *
 * Every domain is subscribed separately. A domain whose callback fails (unsupported area,
 * missing permission) simply stays null, so the tag engine skips it (spec F-02).
 */
class PleosVehicleSource(context: Context, private val scope: CoroutineScope) : VehicleSource {
    private val vehicle = Vehicle(context.applicationContext)
    private val _snapshot = MutableStateFlow(VehicleSnapshot(carModel = Build.MODEL))
    private val _signals = MutableStateFlow<Set<SafetySignal>>(emptySet())
    private val unregister = mutableListOf<() -> Unit>()

    private val seats = mutableMapOf<SeatValue, Boolean>()
    private val windows = mutableMapOf<WindowValue, Boolean>()
    private val doors = mutableMapOf<DoorValue, Boolean>()
    private var lowBeam = false
    private var highBeam = false
    private var brakePulse: Job? = null

    override val snapshot: StateFlow<VehicleSnapshot> = _snapshot.asStateFlow()
    override val activeSafetySignals: StateFlow<Set<SafetySignal>> = _signals.asStateFlow()

    fun start() {
        vehicle.initialize()
        subscribeWiper()
        subscribeLights()
        subscribeSeats()
        subscribeWindows()
        subscribeDoors()
        subscribeGear()
        subscribeTemperature()
        subscribeOdometer()
        subscribeTurnSignal()
        subscribeSteering()
        subscribeBrake()
        subscribeTelltales()
    }

    fun stop() {
        unregister.forEach { runCatching(it) }
        unregister.clear()
        runCatching { vehicle.release() }
    }

    private fun failed(domain: String): (Exception) -> Unit = { e -> Log.w(TAG, "$domain unavailable: ${e.message}") }

    private inline fun sub(domain: String, register: () -> Unit, noinline undo: () -> Unit) {
        try {
            register()
            unregister += undo
        } catch (e: Exception) {
            failed(domain)(e)
        }
    }

    private fun subscribeWiper() {
        val area = WindowArea(WindowValue.FRONT_WINDSHIELD)
        val l = object : WiperStateListener {
            override fun onWiperStateUpdated(state: WiperState?) { _snapshot.update { it.copy(wiperOn = state == WiperState.ON) } }
            override fun onFailed(e: Exception) = failed("Wiper")(e)
        }
        sub("Wiper", { vehicle.getWiper().registerWiperState(area, l) }) { vehicle.getWiper().unregisterWiperState(area, l) }
    }

    private fun subscribeLights() {
        fun publish() = _snapshot.update { it.copy(headlightOn = lowBeam || highBeam) }
        val low = object : LightStateListener {
            override fun onLightStateUpdated(state: LightState?) { lowBeam = state == LightState.ON; publish() }
            override fun onFailed(e: Exception) = failed("LowBeam")(e)
        }
        val high = object : LightStateListener {
            override fun onLightStateUpdated(state: LightState?) { highBeam = state == LightState.ON; publish() }
            override fun onFailed(e: Exception) = failed("HighBeam")(e)
        }
        val light = vehicle.getLight()
        sub("LowBeam", { light.registerLowBeamLightState(low) }) { light.unregisterLowBeamLightState(low) }
        sub("HighBeam", { light.registerHighBeamLightState(high) }) { light.unregisterHighBeamLightState(high) }
    }

    private fun subscribeSeats() {
        val seat = vehicle.getSeat()
        SeatValue.entries.forEach { value ->
            val area = SeatArea(value)
            val l = object : SeatOccupancyListener {
                override fun onSeatOccupancyUpdated(occupied: Boolean?) {
                    synchronized(seats) { seats[value] = occupied == true }
                    // The driver is always aboard even when the sensor only reports passengers.
                    val count = synchronized(seats) { seats.count { it.value } }.coerceAtLeast(1)
                    _snapshot.update { it.copy(occupiedSeats = count) }
                }
                override fun onFailed(e: Exception) {} // Areas the car does not have.
            }
            sub("Seat", { seat.registerSeatOccupancy(area, l) }) { seat.unregisterSeatOccupancy(area, l) }
        }
    }

    private fun subscribeWindows() {
        val window = vehicle.getWindow()
        listOf(WindowValue.ROW_1_LEFT, WindowValue.ROW_1_RIGHT, WindowValue.ROW_2_LEFT, WindowValue.ROW_2_RIGHT,
            WindowValue.ROOF_TOP_1).forEach { value ->
            val area = WindowArea(value)
            val l = object : WindowPositionListener {
                override fun onWindowPositionUpdated(position: Int?) {
                    val open = synchronized(windows) { windows[value] = (position ?: 0) > 0; windows.any { it.value } }
                    _snapshot.update { it.copy(anyWindowOpen = open) }
                }
                override fun onFailed(e: Exception) {}
            }
            sub("Window", { window.registerWindowPosition(area, l) }) { window.unregisterWindowPosition(area, l) }
        }
    }

    private fun subscribeDoors() {
        val door = vehicle.getDoor()
        listOf(DoorValue.ROW_1_LEFT, DoorValue.ROW_1_RIGHT, DoorValue.ROW_2_LEFT, DoorValue.ROW_2_RIGHT,
            DoorValue.REAR).forEach { value ->
            val area = DoorArea(value)
            val l = object : DoorPositionListener {
                override fun onPositionUpdated(position: Int?) {
                    val open = synchronized(doors) { doors[value] = (position ?: 0) > 0; doors.any { it.value } }
                    _snapshot.update { it.copy(anyDoorOpen = open) }
                }
                override fun onFailed(e: Exception) {}
            }
            sub("Door", { door.registerDoorPosition(area, l) }) { door.unregisterDoorPosition(area, l) }
        }
    }

    private fun subscribeGear() {
        val l = object : GearSelectionListener {
            override fun onSelected(gear: VehicleGear?) { _snapshot.update { it.copy(gearInPark = gear == VehicleGear.GEAR_PARK) } }
            override fun onFailed(e: Exception) = failed("Gear")(e)
        }
        sub("Gear", { vehicle.getGear().registerGearSelection(l) }) { vehicle.getGear().unregisterGearSelection(l) }
    }

    /**
     * The SDK has no outside-air sensor API, so the HVAC current temperature of the driver zone
     * stands in for it. Revisit when 42dot confirms an outside temperature property.
     */
    private fun subscribeTemperature() {
        val area = SeatArea(SeatValue.ROW_1_LEFT)
        val l = object : HvacTemperatureListener {
            override fun onHvacTemperatureUpdated(value: Float?) { if (value != null) _snapshot.update { it.copy(outsideTempC = value) } }
            override fun onFailed(e: Exception) = failed("HVAC")(e)
        }
        val hvac = vehicle.getHVAC()
        sub("HVAC", { hvac.registerHvacTemperatureCurrent(area, l) }) { hvac.unregisterHvacTemperatureCurrent(area, l) }
    }

    private fun subscribeOdometer() {
        val l = object : DistanceDrivenListener {
            override fun onDistanceDrivenUpdated(km: Float?) { if (km != null) _snapshot.update { it.copy(odometerKm = km.toDouble()) } }
            override fun onFailed(e: Exception) = failed("Odometer")(e)
        }
        val odo = vehicle.getOdometer()
        sub("Odometer", { odo.registerDistanceDriven(l) }) { odo.unregisterDistanceDriven(l) }
    }

    /** Big steering angle = turning: on above 90 degrees, off again below 45 (no flicker). */
    private fun subscribeSteering() {
        val l = object : ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener {
            override fun onSteeringWheelAngleUpdated(angle: Float?) {
                val a = kotlin.math.abs(angle ?: 0f)
                val on = SafetySignal.SHARP_TURN in _signals.value
                if (!on && a >= SHARP_TURN_ON_DEG) setSignal(SafetySignal.SHARP_TURN, true)
                else if (on && a <= SHARP_TURN_OFF_DEG) setSignal(SafetySignal.SHARP_TURN, false)
            }
            override fun onFailed(e: Exception) = failed("Steering")(e)
        }
        val wheel = vehicle.getSteeringWheel()
        sub("Steering", { wheel.registerSteeringWheelAngle(l) }) { wheel.unregisterSteeringWheelAngle(l) }
    }

    private fun subscribeTurnSignal() {
        val l = object : TurnSignalStateListener {
            override fun onTurnSignalStateUpdated(value: TurnSignalValue?) {
                setSignal(SafetySignal.TURN_SIGNAL, value != null && value != TurnSignalValue.OFF)
            }
            override fun onFailed(e: Exception) = failed("TurnSignal")(e)
        }
        val ts = vehicle.getTurnSignal()
        sub("TurnSignal", { ts.registerTurnSignalState(l) }) { ts.unregisterTurnSignalState(l) }
    }

    /**
     * The SDK reports only whether the pedal is pressed, not deceleration. Holding the brake at a
     * light must not block playback forever, so a press counts as a short pulse.
     */
    private fun subscribeBrake() {
        var pressed = false
        val l = object : BrakePedalStatusListener {
            override fun onBrakePedalStatusUpdated(status: Boolean?) {
                val on = status == true
                if (on && !pressed && _snapshot.value.gearInPark != true) {
                    brakePulse?.cancel()
                    brakePulse = scope.launch {
                        setSignal(SafetySignal.HARD_BRAKE, true)
                        delay(BRAKE_PULSE_MS)
                        setSignal(SafetySignal.HARD_BRAKE, false)
                    }
                }
                pressed = on
            }
            override fun onFailed(e: Exception) = failed("Brake")(e)
        }
        val brake = vehicle.getBrake()
        sub("Brake", { brake.registerBrakePedalStatus(l) }) { brake.unregisterBrakePedalStatus(l) }
    }

    private fun subscribeTelltales() {
        val l = object : TelltaleInfoListener {
            override fun onTelltaleInfoUpdated(info: Telltales?) {
                val warning = info?.telltales.orEmpty().any { it.type in WARNING_TELLTALES && it.state != TelltaleState.OFF }
                setSignal(SafetySignal.SAFETY_WARNING, warning)
            }
            override fun onFailed(e: Exception) = failed("Safety")(e)
        }
        val safety = vehicle.getSafety()
        sub("Safety", { safety.registerTelltales(l) }) { safety.unregisterTelltales(l) }
    }

    private fun setSignal(signal: SafetySignal, on: Boolean) =
        _signals.update { if (on) it + signal else it - signal }

    companion object {
        private const val TAG = "PleosVehicle"
        private const val BRAKE_PULSE_MS = 1_500L
        private const val SHARP_TURN_ON_DEG = 90f
        private const val SHARP_TURN_OFF_DEG = 45f
        private val WARNING_TELLTALES = setOf(
            TelltaleType.MASTER_WARNING, TelltaleType.ABS, TelltaleType.ESC, TelltaleType.AIRBAG,
            TelltaleType.ENGINE_OIL_PRESSURE, TelltaleType.ENGINE_TEMPERATURE, TelltaleType.POWER_DOWN,
        )
    }
}
