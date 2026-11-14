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
import kotlinx.coroutines.launch

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
    /** Friends and shared capsules: Firebase when keys are set, otherwise an in-memory fake. */
    lateinit var social: app.timecapsule.social.SocialBackend
        private set
    lateinit var sync: app.timecapsule.social.SocialSync
        private set

    /** Dev panel override for day/night: null follows the car (system night mode). */
    val nightOverride = kotlinx.coroutines.flow.MutableStateFlow<Boolean?>(null)

    /** A screen to open from outside the UI (friend QR link). */
    val openScreen = kotlinx.coroutines.flow.MutableStateFlow<app.timecapsule.ui.Screen?>(null)

    /** Friend code from a QR link opened before the user had a name; added after sign-up. */
    var pendingFriendCode: String? = null

    /** True while any activity of the app is started (on screen, also in a split window). */
    val visible = kotlinx.coroutines.flow.MutableStateFlow(false)
    lateinit var voiceAnswer: app.timecapsule.drive.VoiceAnswer
        private set
    private var started = 0

    /** True when running on a Pleos head unit (real Vehicle SDK and NaviHelper). */
    var onPleos = false
        private set

    override fun onCreate() {
        super.onCreate()
        db = Room.databaseBuilder(this, AppDatabase::class.java, "timecapsule.db")
            .addMigrations(app.timecapsule.data.Migrations.FROM_1, app.timecapsule.data.Migrations.FROM_2, app.timecapsule.data.Migrations.FROM_3, app.timecapsule.data.Migrations.FROM_4, app.timecapsule.data.Migrations.FROM_5)
            .build()
        settings = Settings(this)
        repo = CapsuleRepository(db)

        // Dev: a "force_fake" file switches to fake car + navi even on a Pleos unit (for demos).
        val forceFake = BuildConfig.DEBUG && java.io.File(filesDir, FORCE_FAKE).exists()
        onPleos = !forceFake && isInstalled(PLEOS_CAR_SERVICE)
        vehicle = if (onPleos) {
            runCatching { PleosVehicleSource(this, scope).also { it.start() } }.getOrNull()
                ?: FakeVehicleSource(VehicleSnapshot()).also { onPleos = false }
        } else if (forceFake) fakeVehicle() else FakeVehicleSource(VehicleSnapshot())
        navi = if (!forceFake && isInstalled(PLEOS_NAVI_SERVICE)) {
            runCatching { PleosNaviSource(this, scope).also { it.start() } }
                .onFailure { android.util.Log.w("TimeCapsuleApp", "NaviHelper failed", it) }.getOrNull()
                ?: FakeNaviSource(null)
        } else FakeNaviSource(if (forceFake) NaviPosition(GeoPoint(37.7519, 128.8761), "강원특별자치도 강릉시 7번 국도") else null)

        ux = UxRestrictions(this).also { it.start() }

        val beeper = Beeper()
        val sky = app.timecapsule.weather.SkySource(scope, navi.position).also { it.start() }
        drive = DriveController(scope, vehicle, navi, repo, Recorder(this, beeper), Player(this), beeper, settings,
            restricted = { !forceFake && ux.restricted.value }, demo = { forceFake },
            microphoneAllowed = { checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED },
            requestPreparation = {
                visible.value && runCatching {
                    androidx.core.content.ContextCompat.startForegroundService(this,
                        android.content.Intent(this, app.timecapsule.drive.DriveService::class.java).setAction(app.timecapsule.drive.DriveService.PREPARE))
                }.isSuccess
            },
            releasePreparation = { stopService(android.content.Intent(this, app.timecapsule.drive.DriveService::class.java)) },
            sky = sky.sky, outsideTempC = sky.tempC)

        val useFirebase = BuildConfig.FIREBASE_API_KEY.isNotBlank() && !java.io.File(filesDir, FAKE_SERVER).exists()
        social = if (useFirebase) {
            runCatching {
                app.timecapsule.social.FirebaseBackend(
                    this, scope, BuildConfig.FIREBASE_API_KEY, BuildConfig.FIREBASE_APP_ID, BuildConfig.FIREBASE_PROJECT_ID,
                )
            }.onFailure { android.util.Log.w("TimeCapsuleApp", "Firebase off", it) }.getOrNull()
                ?: app.timecapsule.social.FakeBackend(this)
        } else app.timecapsule.social.FakeBackend(this)
        android.util.Log.i("TimeCapsuleApp", "server: ${social.label}")
        sync = app.timecapsule.social.SocialSync(this, scope, social, repo, settings) {
            navi.position.value?.let { it.point to drive.car.value.placeName }
        }.also { it.start() }
        drive.sharing = sync
        drive.currentRecipients = { social.friends.value.map { it.uid } }
        drive.authorizeShared = { c -> if (c.replyToId != null) c.remoteId?.let { social.canOpen(it) } == true else c.isMine || c.remoteId?.let { social.canOpen(it) } == true }
        scope.launch {
            var previous = social.me.value?.uid
            social.me.collect { profile ->
                if (profile?.uid != previous) { drive.endDrive(); previous = profile?.uid }
            }
        }

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(a: android.app.Activity) { started++; visible.value = true }
            override fun onActivityStopped(a: android.app.Activity) { started--; visible.value = started > 0; if (!visible.value && !drive.prepared.value) drive.stopPlayback() }
            override fun onActivityCreated(a: android.app.Activity, b: android.os.Bundle?) {}
            override fun onActivityResumed(a: android.app.Activity) {}
            override fun onActivityPaused(a: android.app.Activity) {}
            override fun onActivitySaveInstanceState(a: android.app.Activity, b: android.os.Bundle) {}
            override fun onActivityDestroyed(a: android.app.Activity) {}
        })
        // Alerts over navigation when the app is not on screen.
        app.timecapsule.drive.AlertNotifier(this, scope, drive, visible).start()
        // Steering wheel media keys and voice answers for the approach alert (Pleos SDK).
        app.timecapsule.drive.MediaKeys(this, scope, drive).start()
        voiceAnswer = app.timecapsule.drive.VoiceAnswer(this, scope, drive).also { it.start(this) }
    }

    /** Dev panel: switch between real Pleos and fake sources; takes effect on next start. */
    fun setForceFake(on: Boolean) {
        val f = java.io.File(filesDir, FORCE_FAKE)
        if (on) f.createNewFile() else f.delete()
    }

    val forceFake get() = BuildConfig.DEBUG && java.io.File(filesDir, FORCE_FAKE).exists()

    /** Dev panel: in-memory server instead of Firebase (no internet at a venue); next start. */
    var fakeServer: Boolean
        get() = java.io.File(filesDir, FAKE_SERVER).exists()
        set(on) { java.io.File(filesDir, FAKE_SERVER).let { if (on) it.createNewFile() else it.delete() } }
    val hasFirebaseKeys get() = BuildConfig.FIREBASE_API_KEY.isNotBlank()

    /**
     * "차를 넘길 때 모두 지우기": the car forgets everything, including who was signed in.
     * Capsules left for friends stay on the server (they belong to the friends now too).
     */
    fun wipeCar() {
        drive.endDrive()
        scope.launch { drive.deleteEverything(this@TimeCapsuleApp).join(); social.signOut() }
    }

    /** Dev panel: play another person on this car. Local capsules go; the server keeps theirs. */
    fun switchAccount(p: app.timecapsule.social.Profile?) {
        drive.endDrive()
        scope.launch { drive.deleteEverything(this@TimeCapsuleApp).join(); social.switchAccount(p) }
    }

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
        /** Dev: use the in-memory server even when Firebase keys are set. */
        const val FAKE_SERVER = "fake_server"
    }
}
