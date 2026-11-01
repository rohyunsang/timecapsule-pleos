package app.timecapsule.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Stop
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
import androidx.compose.ui.graphics.Color
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

/** Screen 04: recording. One action, a countdown you can read at a glance. */
@Composable
fun RecordingScreen(since: Long?, tags: List<MoodTag>, onStop: () -> Unit) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(since) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(200)
        }
    }
    val elapsed = since?.let { ((now - it) / 1000).toInt() } ?: 0
    val left = (Rules.MAX_RECORD_SEC - elapsed).coerceAtLeast(0)

    Column(
        Modifier.fillMaxSize().padding(Tc.Gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Tc.Gap, Alignment.CenterVertically),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(Tc.Rec, 14.dp)
            Text("  녹음 중", style = Type.Title, color = Tc.Rec)
        }
        Text("$left", style = Type.Display, color = Tc.Text)
        LinearProgressIndicator(
            progress = { elapsed / Rules.MAX_RECORD_SEC.toFloat() },
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = Tc.Accent,
            trackColor = Tc.Surface2,
            drawStopIndicator = {},
        )
        Body(if (elapsed < Rules.MIN_RECORD_SEC) "3초 이상 말해 주세요" else "다 말했으면 버튼을 누르세요. 30초가 되면 자동으로 묻혀요")
        TagRow(tags)
        Btn("끝내고 묻기", Modifier.widthIn(min = 400.dp), icon = Icons.Rounded.Stop, style = BtnStyle.Primary, onClick = onStop)
    }
}

/** Screen 05: approach alert. */
@Composable
fun AlertCard(alert: AlertUi, onListen: () -> Unit, onLater: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xB30F1012)), contentAlignment = Alignment.Center) {
        Card(Modifier.widthIn(max = 720.dp).padding(Tc.Gutter), accent = true) {
            Eyebrow("${formatDistance(alert.distanceM)} 앞", Icons.Rounded.Place, Tc.Accent)
            Headline(alert.line)
            TagRow(MoodTagEngine.fromStored(alert.capsule.tags))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Btn("들려줘", Modifier.weight(1f), icon = Icons.Rounded.PlayArrow, style = BtnStyle.Primary, onClick = onListen)
                Btn("다음에", Modifier.weight(1f), onClick = onLater)
            }
            Meta("10초 동안 답이 없으면 다음에 차를 세웠을 때 다시 알려 드려요")
        }
    }
}

/** Screen 06: playback. */
@Composable
fun PlayingScreen(
    playing: PlayingUi?,
    safetyPaused: Set<SafetySignal>?,
    userPaused: Boolean,
    onRestart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 72.dp, vertical = Tc.Gutter),
        verticalArrangement = Arrangement.spacedBy(Tc.Gap, Alignment.CenterVertically),
    ) {
        if (playing == null) {
            Body("불러오는 중…")
            return@Column
        }
        val c = playing.capsule
        val date = Instant.ofEpochMilli(c.createdAt).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy년 M월 d일 a h:mm"))
        val tags = MoodTagEngine.fromStored(c.tags)
        val seats = tags.firstOrNull { it.label.endsWith("이서") }?.label ?: "혼자"
        Eyebrow("$date  ·  ${c.placeName ?: "장소 기록 없음"}  ·  $seats")
        Text(playing.narration, style = Type.Headline.copy(fontSize = Type.Headline.fontSize * 1.2f, lineHeight = Type.Headline.lineHeight * 1.2f), color = Tc.Text)
        TagRow(tags)
        if (safetyPaused != null) {
            Card(accent = true) {
                Eyebrow("잠시 멈춤", Icons.Rounded.PauseCircle, Tc.Accent)
                Body("${safetyPaused.joinToString(", ") { it.korean() }} 때문에 멈췄어요. 끝나고 2초 뒤에 이어서 들려 드려요", Tc.Text)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Btn("처음부터", Modifier.weight(1f), icon = Icons.Rounded.Replay, onClick = onRestart)
            Btn(
                if (userPaused) "이어 듣기" else "일시정지", Modifier.weight(1f),
                icon = if (userPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                style = BtnStyle.Primary, onClick = onPause,
            )
            Btn("그만", Modifier.weight(1f), icon = Icons.Rounded.Stop, onClick = onStop)
        }
    }
}

fun SafetySignal.korean() = when (this) {
    SafetySignal.SAFETY_WARNING -> "안전 경고"
    SafetySignal.HARD_BRAKE -> "제동"
    SafetySignal.TURN_SIGNAL -> "방향지시등"
    SafetySignal.NAVI_VOICE -> "길 안내 음성"
}
