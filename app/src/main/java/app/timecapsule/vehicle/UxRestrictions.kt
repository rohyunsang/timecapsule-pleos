package app.timecapsule.vehicle

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pleos "Design Considering Driving Safety": apps must read CarUxRestrictions and reduce the UI
 * while driving. [restricted] is true when the platform asks for distraction optimization.
 * Unknown or unavailable restrictions keep editing locked. Explicit debug demos bypass in the app graph.
 */
class UxRestrictions(private val context: Context) {
    private val _restricted = MutableStateFlow(true)
    val restricted: StateFlow<Boolean> = _restricted.asStateFlow()

    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null

    fun start() {
        try {
            val c = Car.createCar(context) ?: return
            car = c
            val m = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager ?: return
            manager = m
            _restricted.value = m.currentCarUxRestrictions?.isRequiresDistractionOptimization != false
            m.registerListener { r -> _restricted.value = r.isRequiresDistractionOptimization }
        } catch (t: Throwable) {
            // NoClassDefFoundError off AAOS, or the car service is not ready.
            _restricted.value = true
            Log.w("UxRestrictions", "car ux restrictions unavailable", t)
        }
    }

    fun stop() {
        _restricted.value = true
        runCatching { manager?.unregisterListener() }
        runCatching { car?.disconnect() }
    }
}
