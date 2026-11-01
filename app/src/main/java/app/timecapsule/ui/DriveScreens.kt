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
import androidx.compose.material.icons.rounded.Inventory2
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
 * Screen 04: recording. One glance: big seconds left. Two clear ways out: keep it (main) or
 * throw it away. No animation (Pleos: no moving elements while driving).
 */
@Composable
fun RecordingScreen(since: Long?, tags: List<MoodTag>, onStop: () -> Unit, onCancel: () -> Unit) {
    val now = rememberNow(since)
    val elapsed = since?.let { ((now - it) / 1000).toInt() } ?: 0
    val left = (Rules.MAX_RECORD_SEC - elapsed).coerceAtLeast(0)
    val tooShort = elapsed < Rules.MIN_RECORD_SEC

    Column(
        Modifier.fillMaxSize().background(Tc.Bg).padding(Tc.Gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(Tc.Rec, 18.dp)
            Text("  녹음 중", style = Type.TitleM, color = Tc.Rec)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$left", style = Type.Display.copy(fontFeatureSettings = "tnum"), color = Tc.Text)
            Text("초", style = Type.TitleL, color = Tc.TextSub, modifier = Modifier.padding(bottom = 24.dp, start = 8.dp))
        }
        LinearProgressIndicator(
            progress = { elapsed / Rules.MAX_RECORD_SEC.toFloat() },
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
            color = Tc.Rec,
            trackColor = Tc.Surface2,
            drawStopIndicator = {},
        )
        Body(if (tooShort) "3초 이상 말해 주세요" else "다 말했으면 묻기를 누르세요", align = TextAlign.Center)
        TagRow(tags, center = true)
        Row(Modifier.widthIn(max = 760.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Btn("지우기", Modifier.weight(1f), icon = Icons.Rounded.Close, height = Tc.TouchLarge, onClick = onCancel)
            Btn(
                "묻기", Modifier.weight(2f), icon = Icons.Rounded.Check, style = BtnStyle.Primary,
                enabled = !tooShort, height = Tc.TouchLarge, onClick = onStop,
            )
        }
        Meta("30초가 되면 자동으로 묻혀요")
    }
}

/**
 * Screen 05: approach alert, in the Pleos System Notification shape and anchored to the top
 * like system notifications, so the road-relevant part of the screen behind stays visible.
 */
@Composable
fun AlertCard(alert: AlertUi, onListen: () -> Unit, onLater: () -> Unit) {
    val now = rememberNow(alert.shownAt)
    val leftMs = (Rules.ALERT_TIMEOUT_MS - (now - alert.shownAt)).coerceAtLeast(0)
    Box(Modifier.fillMaxSize().background(Tc.Scrim), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.padding(Tc.Gutter).widthIn(max = 880.dp)) {
            val tags = MoodTagEngine.fromStored(alert.capsule.tags).filter { it.label != alert.capsule.placeName }
            Notice(
                icon = Icons.Rounded.Inventory2,
                title = alert.line,
                description = (listOf(if (alert.distanceM < 100) "바로 이 근처" else "${formatDistance(alert.distanceM)} 앞") + tags.map { it.label }).joinToString(" · "),
                accent = true,
                elevated = true,
                actions = { m ->
                    Btn("다음에", m, height = Tc.TouchLarge, onClick = onLater)
                    Btn("들려줘", m, icon = Icons.Rounded.PlayArrow, style = BtnStyle.Primary, height = Tc.TouchLarge, onClick = onListen)
                },
                footer = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(
                            progress = { leftMs / Rules.ALERT_TIMEOUT_MS.toFloat() },
                            modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = Tc.Accent, trackColor = Tc.Surface2, drawStopIndicator = {},
                        )
                        Text("  ${(leftMs + 999) / 1000}초 뒤 닫혀요", style = Type.LabelM, color = Tc.TextMute)
                    }
                },
            )
        }
    }
}

/** Screen 06: playback. The narration is the content; controls are three big, fixed slots. */
@Composable
fun PlayingScreen(
    playing: PlayingUi?,
    safetyPaused: Set<SafetySignal>?,
    userPaused: Boolean,
    onRestart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Tc.Bg)) {
        val compact = widthClass(maxWidth) == WidthClass.Compact
        Column(
            Modifier.fillMaxSize().padding(horizontal = if (compact) Tc.Gutter else 80.dp, vertical = Tc.Gutter),
            verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
        ) {
            if (playing == null) {
                Body("불러오는 중…")
                return@Column
            }
            val c = playing.capsule
            val date = Instant.ofEpochMilli(c.createdAt).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy년 M월 d일 a h:mm"))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.Inventory2, size = 56.dp)
                Column(Modifier.padding(start = 20.dp)) {
                    Text(c.placeName ?: "장소 기록 없음", style = Type.TitleS, color = Tc.Text)
                    Text(date, style = Type.LabelM, color = Tc.TextMute)
                }
            }
            Text(
                playing.narration,
                style = if (compact) Type.TitleL else Type.HeadlineS,
                color = Tc.Text, maxLines = 4, overflow = TextOverflow.Ellipsis,
            )
            TagRow(MoodTagEngine.fromStored(c.tags).filter { it.label != c.placeName })
            if (safetyPaused != null) {
                Row(
                    Modifier.fillMaxWidth().background(Tc.AccentSoft, RoundedCornerShape(20.dp)).padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.PauseCircle, null, tint = Tc.AccentInk, modifier = Modifier.size(40.dp))
                    Column(Modifier.padding(start = 16.dp)) {
                        Text("${safetyPaused.joinToString(", ") { it.korean() }} 때문에 잠시 멈췄어요", style = Type.BodyStrong, color = Tc.Text)
                        Text("끝나면 2초 뒤 이어서 들려 드려요", style = Type.LabelM, color = Tc.TextSub)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Btn("처음부터", Modifier.weight(1f), icon = Icons.Rounded.Replay, height = Tc.TouchLarge, onClick = onRestart)
                Btn(
                    if (userPaused) "이어 듣기" else "일시정지", Modifier.weight(1.4f),
                    icon = if (userPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    style = BtnStyle.Primary, height = Tc.TouchLarge, onClick = onPause,
                )
                Btn("그만", Modifier.weight(1f), icon = Icons.Rounded.Stop, height = Tc.TouchLarge, onClick = onStop)
            }
        }
    }
}

fun SafetySignal.korean() = when (this) {
    SafetySignal.SAFETY_WARNING -> "안전 경고"
    SafetySignal.HARD_BRAKE -> "제동"
    SafetySignal.TURN_SIGNAL -> "방향지시등"
    SafetySignal.NAVI_VOICE -> "길 안내 음성"
}
