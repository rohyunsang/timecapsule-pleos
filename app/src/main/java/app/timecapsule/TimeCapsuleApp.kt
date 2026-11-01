package app.timecapsule

import android.app.Application
import android.content.pm.PackageManager
import androidx.room.Room
import app.timecapsule.audio.Beeper
import app.timecapsule.audio.Player
import app.timecapsule.audio.Recorder
import app.timecapsule.capsule.CapsuleRepository
import app.timecapsule.data.AppDatabase
import app.timecapsule.drive.DriveController
import app.timecapsule.geo.GeoPoint
import app.timecapsule.navi.FakeNaviSource
import app.timecapsule.navi.NaviPosition
import app.timecapsule.navi.NaviSource
import app.timecapsule.navi.PleosNaviSource
import app.timecapsule.vehicle.FakeVehicleSource
import app.timecapsule.vehicle.PleosVehicleSource
import app.timecapsule.vehicle.UxRestrictions
import app.timecapsule.vehicle.VehicleSnapshot
import app.timecapsule.vehicle.VehicleSource
import kotlinx.coroutines.MainScope

/** Manual dependency graph. On a Pleos head unit the real SDKs are used, elsewhere fakes. */
class TimeCapsuleApp : Application() {
    val scope = MainScope()

    lateinit var db: AppDatabase
        private set
    lateinit var vehicle: VehicleSource
        private set
    lateinit var navi: NaviSource
        private set
    lateinit var settings: Settings
        private set
    lateinit var repo: CapsuleRepository
        private set
    lateinit var drive: DriveController
        private set
    lateinit var ux: UxRestrictions
        private set

    /** Dev panel override for day/night: null follows the car (system night mode). */
    val nightOverride = kotlinx.coroutines.flow.MutableStateFlow<Boolean?>(null)

    /** Dev panel: preview the 1:1 split window (half width next to navigation). */
    val halfPreview = kotlinx.coroutines.flow.MutableStateFlow(false)

    /** True when running on a Pleos head unit (real Vehicle SDK and NaviHelper). */
    var onPleos = false
        private set

    override fun onCreate() {
        super.onCreate()
        db = Room.databaseBuilder(this, AppDatabase::class.java, "timecapsule.db")
            // Prototype: no shipped data to migrate yet.
            .fallbackToDestructiveMigration()
            .build()
        settings = Settings(this)
        repo = CapsuleRepository(db)

        // Dev: a "force_fake" file switches to fake car + navi even on a Pleos unit (for demos).
        val forceFake = java.io.File(filesDir, FORCE_FAKE).exists()
        onPleos = !forceFake && isInstalled(PLEOS_CAR_SERVICE)
        vehicle = if (onPleos) {
            runCatching { PleosVehicleSource(this, scope).also { it.start() } }.getOrNull()
                ?: fakeVehicle().also { onPleos = false }
        } else fakeVehicle()
        navi = if (!forceFake && isInstalled(PLEOS_NAVI_SERVICE)) {
            PleosNaviSource(this, scope).also { it.start() }
        } else FakeNaviSource(NaviPosition(GeoPoint(37.7519, 128.8761), "강원특별자치도 강릉시 7번 국도"))

        ux = UxRestrictions(this).also { it.start() }

        val beeper = Beeper()
        drive = DriveController(scope, vehicle, navi, repo, Recorder(this, beeper), Player(this), beeper, settings)
    }

    /** Dev panel: switch between real Pleos and fake sources; takes effect on next start. */
    fun setForceFake(on: Boolean) {
        val f = java.io.File(filesDir, FORCE_FAKE)
        if (on) f.createNewFile() else f.delete()
    }

    val forceFake get() = java.io.File(filesDir, FORCE_FAKE).exists()

    private fun fakeVehicle() = FakeVehicleSource(VehicleSnapshot(occupiedSeats = 1, outsideTempC = 18f, gearInPark = true))

    private fun isInstalled(pkg: String) = try {
        packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private companion object {
        const val PLEOS_CAR_SERVICE = "ai.pleos.service.car"
        const val PLEOS_NAVI_SERVICE = "ai.pleos.playground.service.navi"
        const val FORCE_FAKE = "force_fake"
    }
}
