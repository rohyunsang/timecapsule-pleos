package io.timecapsule.vehicle

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.os.Handler
import android.util.Log

class DrivingRestrictions(private val context: Context, private val handler: Handler, private val changed: () -> Unit) {
    var known=false; private set
    var restricted=true; private set
    var flags=CarUxRestrictions.UX_RESTRICTIONS_FULLY_RESTRICTED; private set
    private var car: Car?=null
    private var manager: CarUxRestrictionsManager?=null
    private var connecting=false
    fun connect() {
        if(connecting) return
        connecting=true
        try {
            car=Car.createCar(context,handler,Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connected,ready ->
                if(!connecting) return@createCar
                if(!ready) { known=false; restricted=true; changed(); return@createCar }
                try {
                    manager=connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
                    manager!!.registerListener { value -> accept(value) }
                    accept(manager!!.currentCarUxRestrictions)
                } catch(_:Exception) { known=false; restricted=true; changed() }
            }
        } catch(_:Exception) { known=false; restricted=true; changed() }
    }
    private fun accept(value: CarUxRestrictions?) {
        known=value!=null
        restricted=value?.isRequiresDistractionOptimization ?: true
        flags=value?.activeRestrictions ?: CarUxRestrictions.UX_RESTRICTIONS_FULLY_RESTRICTED
        Log.i("TimeCapsule","CarUxRestrictions known=$known restricted=$restricted flags=$flags")
        changed()
    }
    fun disconnect() {
        connecting=false
        try { manager?.unregisterListener(); car?.disconnect() } catch(_:Exception){}
        manager=null; car=null; known=false; restricted=true
    }
}
