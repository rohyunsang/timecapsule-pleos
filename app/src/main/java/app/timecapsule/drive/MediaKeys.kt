package app.timecapsule.drive

import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.util.Log
import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Steering wheel media keys. The session is active only while an approach alert is open or a
 * capsule is playing, so the keys go back to the radio or music the rest of the time.
 *   alert:   play / play-pause = 들려줘 · next / stop = 다음에
 *   playing: play-pause = 일시정지 / 이어 듣기 · previous = 처음부터 · next / stop = 그만
 * The title also shows on the cluster's media area: "지영님의 캡슐 · 7번 국도".
 */
class MediaKeys(context: Context, private val scope: CoroutineScope, private val drive: DriveController) {
    private val app = context.applicationContext
    /**
     * Created for an alert or playback and released right after. Android sends media keys to
     * the last app that played sound even when its session is inactive, so a session left
     * behind would swallow the music app's steering keys until that app plays again.
     */
    private var session: MediaSession? = null

    private fun open(): MediaSession = session ?: MediaSession(app, "TimeCapsule").also { s ->
        session = s
        s.setCallback(object : MediaSession.Callback() {
            override fun onMediaButtonEvent(intent: Intent): Boolean {
                @Suppress("DEPRECATION")
                val key = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return false
                if (key.action != KeyEvent.ACTION_DOWN || key.repeatCount > 0) return true
                Log.i(TAG, "key ${KeyEvent.keyCodeToString(key.keyCode)} in ${drive.state.value}")
                return handle(key.keyCode)
            }
        })
    }

    fun start() {
        scope.launch {
            combine(drive.state, drive.alert, drive.playing, drive.userPaused) { s, a, p, paused -> Triple(s, a ?: p, paused) }
                .collect { (s, what, paused) -> update(s, what, paused) }
        }
    }

    /** Returns true when the key was ours. */
    fun handle(code: Int): Boolean {
        val s = drive.state.value
        when {
            s is DriveState.ApproachAlert -> when (code) {
                KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> drive.listen()
                KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_STOP -> drive.later()
                else -> return false
            }
            s is DriveState.Playing || s is DriveState.Paused -> when (code) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> drive.togglePause()
                KeyEvent.KEYCODE_MEDIA_PAUSE -> if (!drive.userPaused.value) drive.togglePause()
                KeyEvent.KEYCODE_MEDIA_PLAY -> if (drive.userPaused.value) drive.togglePause()
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> drive.restart()
                KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_STOP -> drive.stopPlayback()
                else -> return false
            }
            else -> return false
        }
        return true
    }

    private fun update(s: DriveState, what: Any?, paused: Boolean) {
        val alert = s is DriveState.ApproachAlert
        val playing = s is DriveState.Playing || s is DriveState.Paused
        if (!alert && !playing) {
            session?.let { it.isActive = false; it.release() }
            session = null
            return
        }
        val session = open()
        val c = (what as? AlertUi)?.capsule ?: (what as? PlayingUi)?.capsule
        val title = (c?.ownerName?.let { "${it}님의 캡슐" } ?: "내 캡슐") + " · " + (c?.placeName ?: "이 길")
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, if (alert) "핸들 재생 버튼: 들려줘" else "타임캡슐")
                .build(),
        )
        val actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_STOP or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS
        // "Playing" while an alert is open too, so the media keys are routed to us right away.
        val state = if (playing && paused) PlaybackState.STATE_PAUSED else PlaybackState.STATE_PLAYING
        session.setPlaybackState(PlaybackState.Builder().setActions(actions).setState(state, 0L, 1f).build())
        if (!session.isActive) session.isActive = true
    }

    private companion object { const val TAG = "MediaKeys" }
}
