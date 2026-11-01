package app.timecapsule

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import app.timecapsule.ui.AlbumScreen
import app.timecapsule.ui.AlertCard
import app.timecapsule.ui.DevPanel
import app.timecapsule.ui.HomeScreen
import app.timecapsule.ui.LetterScreen
import app.timecapsule.ui.PlayingScreen
import app.timecapsule.ui.RecordingScreen
import app.timecapsule.ui.Screen
import app.timecapsule.ui.SettingsScreen
import app.timecapsule.ui.Tc
import app.timecapsule.ui.TimeCapsuleTheme

class MainActivity : ComponentActivity() {
    private val app get() = application as TimeCapsuleApp

    private val askMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(this, "마이크 권한이 없으면 캡슐을 묻을 수 없어요", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            askMic.launch(Manifest.permission.RECORD_AUDIO)
        }
        setContent {
            TimeCapsuleTheme {
                Surface(Modifier.fillMaxSize(), color = Tc.Bg) { Root(app) }
            }
        }
    }

    /** F-01: the steering-wheel voice button buries a capsule, a second press ends it. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode in VOICE_KEYS && event.repeatCount == 0) {
            if (app.drive.state.value == DriveState.Recording) app.drive.stopRecording() else app.drive.bury()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private companion object {
        val VOICE_KEYS = setOf(KeyEvent.KEYCODE_VOICE_ASSIST, KeyEvent.KEYCODE_SEARCH, KeyEvent.KEYCODE_HEADSETHOOK)
    }
}

@Composable
private fun Root(app: TimeCapsuleApp) {
    val drive = app.drive
    val state by drive.state.collectAsState()
    val car by drive.car.collectAsState()
    val alert by drive.alert.collectAsState()
    val playing by drive.playing.collectAsState()
    val signals by drive.signals.collectAsState()
    val userPaused by drive.userPaused.collectAsState()
    val recordingSince by drive.recordingSince.collectAsState()
    var screen by remember { mutableStateOf(Screen.HOME) }
    var dev by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // Unknown gear counts as parked so the app stays usable on a bench.
    val parked = car.gearInPark != false

    // Spec 07: only screens 04-06 (and home) while driving.
    LaunchedEffect(parked) { if (!parked) screen = Screen.HOME }
    LaunchedEffect(Unit) {
        drive.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    BackHandler(screen != Screen.HOME) { screen = Screen.HOME }

    Box(Modifier.fillMaxSize()) {
        val s = state
        when {
            s == DriveState.Recording ->
                RecordingScreen(recordingSince, MoodTagEngine.tagsFor(car)) { drive.stopRecording() }
            s is DriveState.Playing || (s is DriveState.Paused && s.resumeTo is DriveState.Playing) ->
                PlayingScreen(
                    playing,
                    safetyPaused = if (s is DriveState.Paused) signals else null,
                    userPaused = userPaused,
                    onRestart = drive::restart,
                    onPause = drive::togglePause,
                    onStop = drive::stopPlayback,
                )
            else -> when (screen) {
                Screen.HOME -> HomeScreen(drive, parked, app.onPleos, { screen = it }, { dev = !dev })
                Screen.LETTER -> LetterScreen(drive) { screen = Screen.HOME }
                Screen.ALBUM -> AlbumScreen(drive) { screen = Screen.HOME }
                Screen.SETTINGS -> SettingsScreen(app) { screen = Screen.HOME }
            }
        }
        alert?.let { AlertCard(it, drive::listen, drive::later) }
        if (dev && s != DriveState.Recording) DevPanel(app) { dev = false }
    }
}
