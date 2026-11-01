package app.timecapsule.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** Screen 04: recording. One big stop button, live countdown, the tags being captured. */
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
        Modifier.fillMaxSize().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
    ) {
        Text("● 녹음 중", fontSize = 30.sp, color = MaterialTheme.colorScheme.error)
        Text("$left", fontSize = 120.sp)
        LinearProgressIndicator(
            progress = { elapsed / Rules.MAX_RECORD_SEC.toFloat() },
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
        )
        Caption(if (elapsed < Rules.MIN_RECORD_SEC) "3초 이상 말해 주세요" else "\"그만\" 하거나 버튼을 누르면 묻어요")
        TagRow(tags)
        BigButton("끝내고 묻기", Modifier.widthIn(min = 360.dp), onClick = onStop)
    }
}

/** Screen 05: approach alert card. */
@Composable
fun AlertCard(alert: AlertUi, onListen: () -> Unit, onLater: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
        Panel(Modifier.widthIn(max = 760.dp).padding(32.dp), highlight = true) {
            Caption("${formatDistance(alert.distanceM)} 앞")
            Text(alert.line, fontSize = 32.sp)
            TagRow(MoodTagEngine.fromStored(alert.capsule.tags))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BigButton("들려줘", Modifier.weight(1f), onClick = onListen)
                BigButton("다음에", Modifier.weight(1f), primary = false, onClick = onLater)
            }
            Caption("10초 동안 답이 없으면 다음 정차 때 다시 알려 드려요")
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
        Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
    ) {
        if (playing == null) {
            Body("불러오는 중…")
            return@Column
        }
        val c = playing.capsule
        val date = Instant.ofEpochMilli(c.createdAt).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy년 M월 d일 a h:mm"))
        val seats = MoodTagEngine.fromStored(c.tags).firstOrNull { it.label.endsWith("이서") }?.label ?: "혼자"
        Caption("$date · ${c.placeName ?: "장소 기록 없음"} · $seats")
        Text("“${playing.narration}”", fontSize = 36.sp, lineHeight = 48.sp)
        TagRow(MoodTagEngine.fromStored(c.tags))
        if (safetyPaused != null) {
            Panel(highlight = true) {
                Body("잠시 멈췄어요 · ${safetyPaused.joinToString { it.korean() }}")
                Caption("신호가 끝나고 2초 뒤에 이어서 들려 드려요")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BigButton("처음부터", Modifier.weight(1f), primary = false, onClick = onRestart)
            BigButton(if (userPaused) "이어 듣기" else "일시정지", Modifier.weight(1f), onClick = onPause)
            BigButton("그만", Modifier.weight(1f), primary = false, onClick = onStop)
        }
    }
}

fun SafetySignal.korean() = when (this) {
    SafetySignal.SAFETY_WARNING -> "안전 경고"
    SafetySignal.HARD_BRAKE -> "제동"
    SafetySignal.TURN_SIGNAL -> "방향지시등"
    SafetySignal.NAVI_VOICE -> "길 안내 음성"
}
