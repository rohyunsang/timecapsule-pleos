package app.timecapsule

import app.timecapsule.weather.Sky
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import app.timecapsule.ui.ConfirmPopup
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import app.timecapsule.drive.DriveState
import app.timecapsule.tag.MoodTagEngine
import app.timecapsule.ui.CollectedMapScreen
import app.timecapsule.ui.AlbumScreen
import app.timecapsule.ui.AlertCard
import app.timecapsule.drive.Gleo
import app.timecapsule.ui.DevPanel
import app.timecapsule.ui.HomeScreen
import app.timecapsule.ui.LetterScreen
import app.timecapsule.ui.PlayingScreen
import app.timecapsule.ui.RecordingScreen
import app.timecapsule.ui.Screen
import app.timecapsule.ui.SettingsScreen
import app.timecapsule.ui.Tc
import app.timecapsule.ui.TimeCapsuleTheme
import app.timecapsule.ui.FriendsScreen
import kotlinx.coroutines.launch
import app.timecapsule.social.FriendCode

class MainActivity : ComponentActivity() {
    private val app get() = application as TimeCapsuleApp

    private val askPerms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == false) {
            Toast.makeText(this, "마이크 권한이 없으면 캡슐을 묻을 수 없어요", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Mic for capsules; notifications so approach alerts can show over navigation.
        val wanted = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (android.os.Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isNotEmpty()) askPerms.launch(wanted.toTypedArray())
        handleFriendLink(intent)
        handleGleo(intent)
        setContent {
            // Day/night follows the car (AAOS night mode); the dev panel can pin one for demos.
            val pinned by app.nightOverride.collectAsState()
            TimeCapsuleTheme(night = pinned ?: isSystemInDarkTheme()) {
                Surface(Modifier.fillMaxSize(), color = Tc.Bg) { Root(app) }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleFriendLink(intent)
        handleGleo(intent)
    }

    /** Gleo AI: "타임캡슐 녹음해 줘" opens this screen with START_CAPSULE_RECORDING. */
    private fun handleGleo(intent: android.content.Intent?) {
        if (intent?.action != Gleo.START) return
        intent.action = null
        // The action is exported for Gleo, so an ordinary installed app could also send it and
        // turn the microphone on. Only the car's own (system) apps and this app may start it.
        if (!trustedCaller()) {
            android.util.Log.w("Gleo", "START ignored from ${referrer?.host}")
            return
        }
        app.openScreen.value = Screen.HOME
        app.scope.launch {
            val ok = app.drive.recordByVoice()
            Gleo.reply(
                this@MainActivity, Gleo.START, ok,
                if (ok) "Recording a voice capsule." else "Recording could not start now.",
            )
        }
    }

    /** True when the launcher is unknown (system), this app, or a preinstalled system app. */
    private fun trustedCaller(): Boolean {
        val pkg = referrer?.takeIf { it.scheme == "android-app" }?.host ?: return true
        if (pkg == packageName) return true
        return runCatching {
            val info = packageManager.getApplicationInfo(pkg, 0)
            info.flags and (android.content.pm.ApplicationInfo.FLAG_SYSTEM or android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        }.getOrDefault(false)
    }

    /** A friend QR code opened on this car (timecapsule://friend?code=...). */
    private fun handleFriendLink(intent: android.content.Intent?) {
        val code = intent?.dataString?.let(FriendCode::fromLink) ?: return
        intent.data = null
        app.openScreen.value = Screen.FRIENDS
        if (app.social.me.value == null) {
            app.pendingFriendCode = code
            return
        }
        app.scope.launch {
            val r = app.social.addFriend(code)
            Toast.makeText(this@MainActivity, r.fold({ "${it.name}님과 친구가 됐어요" }, { it.message ?: "추가하지 못했어요" }), Toast.LENGTH_LONG).show()
        }
    }

    /** Explicit RECORD only. Ordinary media keys never start microphone recording. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode in VOICE_KEYS && event.repeatCount == 0) {
            if (app.drive.state.value == DriveState.Recording) app.drive.stopRecording() else app.drive.bury()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private companion object {
        val VOICE_KEYS = setOf(KeyEvent.KEYCODE_MEDIA_RECORD)
    }
}

@Composable
private fun Root(app: TimeCapsuleApp) {
    val drive = app.drive
    val state by drive.state.collectAsState()
    val car by drive.car.collectAsState()
    val alert by drive.alert.collectAsState()
    val playing by drive.playing.collectAsState()
    val friends by app.social.friends.collectAsState()
    val signals by drive.signals.collectAsState()
    val userPaused by drive.userPaused.collectAsState()
    val recordingSince by drive.recordingSince.collectAsState()
    var screen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(Screen.HOME) }
    val formState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    var permissionReturnScreen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<Screen?>(null) }
    var settingsReturned by remember { mutableStateOf(false) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && permissionReturnScreen != null) settingsReturned = true
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var dev by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // Driving = the platform asks for distraction optimization (CarUxRestrictions, Pleos guide)
    // Unknown or expired car state keeps management screens locked.
    val editable by drive.editable.collectAsState()
    val restricted = !editable

    // Spec 07: only screens 04-06 (and home) while driving.
    LaunchedEffect(restricted) {
        if (restricted) {
            screen = Screen.HOME
            // The dev panel has account switching and data tools: not while driving (except fake demos).
            if (!app.forceFake) dev = false
        }
    }
    LaunchedEffect(restricted, settingsReturned) {
        if (!restricted && settingsReturned) {
            permissionReturnScreen?.let { screen = it }
            permissionReturnScreen = null
            settingsReturned = false
        }
    }
    LaunchedEffect(Unit) {
        drive.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    LaunchedEffect(Unit) {
        app.sync.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    val open by app.openScreen.collectAsState()
    LaunchedEffect(open) { open?.let { if (!restricted) screen = it; app.openScreen.value = null } }
    // System back follows the header back: the map returns to the album it was opened from.
    BackHandler(screen != Screen.HOME) { screen = if (screen == Screen.MAP) Screen.ALBUM else Screen.HOME }

    Box(Modifier.fillMaxSize()) {
        val s = state
        when {
            s == DriveState.Recording ->
                RecordingScreen(
                    recordingSince,
                    maxSec = app.settings.maxRecordSec.collectAsState().value,
                    silenceSec = app.settings.silenceSec.collectAsState().value,
                    car.placeName,
                    MoodTagEngine.tagsFor(car).filter { it.label != car.placeName },
                    level = drive::micLevel,
                    sky = car.sky?.let { s -> Sky.entries.firstOrNull { it.name == s } },
                    night = java.time.LocalTime.now().hour.let { it >= 20 || it < 5 },
                    animate = !restricted,
                    onStop = drive::stopRecording,
                    onCancel = drive::cancelRecording,
                )
            s is DriveState.Playing || (s is DriveState.Paused && s.resumeTo is DriveState.Playing) ->
                PlayingScreen(
                    playing,
                    safetyPaused = if (s is DriveState.Paused) signals else null,
                    userPaused = userPaused,
                    onRestart = drive::restart,
                    onPause = drive::togglePause,
                    onStop = drive::stopPlayback,
                    progress = drive::progress,
                    animate = !restricted,
                )
            else -> when (screen) {
                Screen.HOME -> HomeScreen(
                    drive, restricted, app.onPleos, go = { screen = it },
                    onLocked = { Toast.makeText(context, "안전을 위해 차를 세우면 열려요", Toast.LENGTH_SHORT).show() },
                    onDev = { dev = !dev },
                    friendCount = friends.size,
                )
                Screen.LETTER -> formState.SaveableStateProvider("letter") { LetterScreen(app) { screen = Screen.HOME } }
                Screen.ALBUM -> AlbumScreen(app, onMap = { screen = Screen.MAP }) { screen = Screen.HOME }
                Screen.MAP -> CollectedMapScreen(drive) { screen = Screen.ALBUM }
                Screen.SETTINGS -> SettingsScreen(app) { screen = Screen.HOME }
                Screen.FRIENDS -> FriendsScreen(app) { screen = Screen.HOME }
            }
        }
        val hearing by app.voiceAnswer.listening.collectAsState()
        alert?.let { AlertCard(it, drive::listen, drive::later, hearing) }
        if (dev && s != DriveState.Recording && (!restricted || app.forceFake)) DevPanel(app) { dev = false }
        val microphonePermissionNeeded by drive.microphonePermissionNeeded.collectAsState()
        if (microphonePermissionNeeded && !restricted) ConfirmPopup(
            title = "마이크 권한이 필요해요",
            body = "Android 앱 설정에서 권한 → 마이크를 허용해 주세요. 돌아온 뒤 녹음 버튼이나 준비하기를 다시 눌러 주세요.",
            confirm = "설정으로 이동",
            onConfirm = {
                drive.dismissMicrophonePermission()
                if (drive.canEdit()) {
                    val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null))
                    permissionReturnScreen = screen
                    settingsReturned = false
                    runCatching { context.startActivity(settingsIntent) }.onFailure {
                        permissionReturnScreen = null
                        Toast.makeText(context, "설정을 열지 못했어요. 차량 설정 → 앱 → 타임캡슐 → 권한에서 마이크를 허용해 주세요", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onDismiss = drive::dismissMicrophonePermission,
        )
    }
}
