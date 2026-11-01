package app.timecapsule.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.timecapsule.capsule.Rules
import app.timecapsule.drive.AlertUi
import app.timecapsule.drive.PlayingUi
import app.timecapsule.tag.MoodTag
import app.timecapsule.tag.MoodTagEngine
import app.timecapsule.vehicle.SafetySignal
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
private fun rememberNow(key: Any?): Long {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(key) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(200)
        }
    }
    return now
}

/**
 * Screen 04: recording, laid out like a voice recorder / call screen: status on top, a big
 * timer in the middle, the two ways out in a fixed bar at the bottom. No animation.
 */
@Composable
fun RecordingScreen(since: Long?, place: String?, tags: List<MoodTag>, onStop: () -> Unit, onCancel: () -> Unit) {
    val now = rememberNow(since)
    val elapsed = since?.let { ((now - it) / 1000).toInt() } ?: 0
    val shown = elapsed.coerceAtMost(Rules.MAX_RECORD_SEC)
    val tooShort = elapsed < Rules.MIN_RECORD_SEC

    Column(Modifier.fillMaxSize().background(Tc.Bg).padding(Tc.Gutter)) {
        Row(Modifier.fillMaxWidth().padding(start = Tc.Inset, end = Tc.Inset, top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Dot(Tc.Rec, 16.dp)
            Text("  녹음 중", style = Type.TitleS, color = Tc.Text)
            Spacer(Modifier.weight(1f))
            Text("최대 ${Rules.MAX_RECORD_SEC}초", style = Type.LabelM, color = Tc.TextMute)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        ) {
            Text("0:%02d".format(shown), style = Type.Display.copy(fontFeatureSettings = "tnum"), color = Tc.Text)
            LinearProgressIndicator(
                progress = { shown / Rules.MAX_RECORD_SEC.toFloat() },
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = Tc.Rec, trackColor = Tc.Basic, drawStopIndicator = {},
            )
            Text(
                (listOf(place ?: "위치 확인 중") + tags.map { it.label }).joinToString(" · "),
                style = Type.BodyS, color = Tc.TextMute, textAlign = TextAlign.Center, maxLines = 2,
            )
        }
        Text(
            if (tooShort) "3초 이상 말해 주세요" else "다 말했으면 묻기를 누르세요",
            style = Type.BodyM, color = Tc.TextSub, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Btn("지우기", Modifier.weight(1f), icon = Icons.Rounded.Close, height = Tc.TouchLarge, onClick = onCancel)
            Btn("묻기", Modifier.weight(1f), icon = Icons.Rounded.Check, style = BtnStyle.Filled, enabled = !tooShort, height = Tc.TouchLarge, onClick = onStop)
        }
    }
}

/**
 * Screen 05: approach alert. Same shape and place as a Pleos system notification (top, white,
 * shadow, no dimming of the screen behind). When the app is not on screen the same alert is
 * posted as a real heads-up notification instead (AlertNotifier), so it shows over navigation.
 */
@Composable
fun AlertCard(alert: AlertUi, onListen: () -> Unit, onLater: () -> Unit) {
    val now = rememberNow(alert.shownAt)
    val leftMs = (Rules.ALERT_TIMEOUT_MS - (now - alert.shownAt)).coerceAtLeast(0)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        SystemNotice(
            title = alert.line,
            description = alertDescription(alert),
            modifier = Modifier.padding(16.dp).widthIn(max = 760.dp),
            cta = "들려줘" to onListen, ctaIcon = Icons.Rounded.PlayArrow,
            sub = "다음에" to onLater,
            footer = {
                LinearProgressIndicator(
                    progress = { leftMs / Rules.ALERT_TIMEOUT_MS.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = Tc.TextMute, trackColor = Tc.Basic, drawStopIndicator = {},
                )
            },
        )
    }
}

fun alertDescription(alert: AlertUi): String {
    val tags = MoodTagEngine.fromStored(alert.capsule.tags).filter { it.label != alert.capsule.placeName }.map { it.label }
    val where = if (alert.distanceM < 100) "바로 이 근처" else "${formatDistance(alert.distanceM)} 앞"
    val c = alert.capsule
    // Own capsule: the title already says when and where. A friend's: say it here.
    val about = if (c.ownerName != null) listOfNotNull(
        app.timecapsule.tag.Narration.elapsed(c.date(), java.time.LocalDate.now()), c.placeName,
    ) else tags
    return (listOf(where) + about).joinToString(" · ")
}

/**
 * Screen 06: playback, like a Now Playing template: what (place, date), the narration as the
 * content, and a fixed three-button control bar at the bottom.
 */
@Composable
fun PlayingScreen(
    playing: PlayingUi?,
    safetyPaused: Set<SafetySignal>?,
    userPaused: Boolean,
    onRestart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    progress: () -> Pair<Int, Int>? = { null },
    /** Weather motion only when parked; while driving the backdrop stands still. */
    animate: Boolean = true,
) {
    var pos by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Pair<Int, Int>?>(null) }
    androidx.compose.runtime.LaunchedEffect(playing?.capsule?.id) {
        while (true) { pos = progress(); kotlinx.coroutines.delay(200) }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Tc.Bg)) {
        val portrait = widthClass(maxWidth) == WidthClass.Portrait
        playing?.capsule?.let { c ->
            WeatherBackdrop(
                app.timecapsule.tag.TheDay.sky(c.rawVehicleState), app.timecapsule.tag.TheDay.isNight(c.createdAt),
                animate, Modifier.matchParentSize(),
            )
        }
        Column(Modifier.fillMaxSize().padding(Tc.Gutter)) {
            if (playing == null) {
                Text("불러오는 중…", style = Type.BodyM, color = Tc.TextMute)
                return@Column
            }
            val c = playing.capsule
            val date = Instant.ofEpochMilli(c.createdAt).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy년 M월 d일 a h:mm"))
            Column(Modifier.fillMaxWidth().padding(start = Tc.Inset, top = 16.dp)) {
                Text(c.placeName ?: "장소 기록 없음", style = Type.TitleS, color = Tc.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(c.ownerName?.let { "${it}님이 남김 · $date" } ?: date, style = Type.LabelM, color = Tc.TextMute)
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().widthIn(max = 1200.dp).padding(horizontal = Tc.Inset),
                verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            ) {
                Text(
                    playing.narration,
                    style = if (portrait) Type.TitleL else Type.HeadlineS,
                    color = Tc.Text, maxLines = 5, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "그날  " + app.timecapsule.tag.TheDay.facts(c.createdAt, c.rawVehicleState).joinToString(" · "),
                    style = Type.BodyM, color = Tc.TextSub,
                )
                VoiceProgress(pos, c.ownerName)
            }
            if (safetyPaused != null) {
                Panel(Modifier.padding(bottom = 16.dp)) {
                    ListRow(
                        "${safetyPaused.joinToString(", ") { it.korean() }} 때문에 잠시 멈췄어요",
                        "끝나면 2초 뒤 이어서 들려 드려요",
                        leading = Icons.Rounded.PauseCircle,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Btn("처음부터", Modifier.weight(1f), icon = Icons.Rounded.Replay, height = Tc.TouchLarge, onClick = onRestart)
                Btn(
                    if (userPaused) "이어 듣기" else "일시정지", Modifier.weight(1f),
                    icon = if (userPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    style = BtnStyle.Filled, height = Tc.TouchLarge, onClick = onPause,
                )
                Btn("그만", Modifier.weight(1f), icon = Icons.Rounded.Stop, height = Tc.TouchLarge, onClick = onStop)
            }
        }
    }
}

/** A thin bar with time, so it is clear whether the voice is still playing. */
@Composable
private fun VoiceProgress(pos: Pair<Int, Int>?, owner: String?) {
    fun t(ms: Int) = "%d:%02d".format(ms / 60000, ms / 1000 % 60)
    val frac = pos?.let { (it.first.toFloat() / it.second).coerceIn(0f, 1f) } ?: 0f
    Column(Modifier.fillMaxWidth().widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.LinearProgressIndicator(
            progress = { frac },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = Tc.Text, trackColor = Tc.TextMute.copy(alpha = 0.25f), drawStopIndicator = {},
        )
        Text(
            if (pos == null) "안내 뒤 ${owner?.let { "${it}님의 " } ?: ""}목소리가 나와요"
            else "${owner?.let { "${it}님의 " } ?: ""}목소리 · ${t(pos.first)} / ${t(pos.second)}",
            style = Type.LabelM, color = Tc.TextMute,
        )
    }
}

fun SafetySignal.korean() = when (this) {
    SafetySignal.SAFETY_WARNING -> "안전 경고"
    SafetySignal.HARD_BRAKE -> "제동"
    SafetySignal.TURN_SIGNAL -> "방향지시등"
    SafetySignal.NAVI_VOICE -> "길 안내 음성"
}
