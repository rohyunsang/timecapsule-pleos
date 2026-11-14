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
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.unit.sp
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
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
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

/** Recording uses measured input, a large timer, and fixed completion controls. */
/** "최대 30초", "최대 1분", "최대 2분 30초". */
fun lengthLabel(sec: Int): String = "최대 " + when {
    sec < 60 -> "${sec}초"
    sec % 60 == 0 -> "${sec / 60}분"
    else -> "${sec / 60}분 ${sec % 60}초"
}

@Composable
fun RecordingScreen(
    since: Long?, maxSec: Int, silenceSec: Int, place: String?, tags: List<MoodTag>, level: () -> Float,
    sky: app.timecapsule.weather.Sky?, night: Boolean, animate: Boolean,
    onStop: () -> Unit, onCancel: () -> Unit,
) {
    val now = rememberNow(since)
    val elapsed = since?.let { ((now - it) / 1000).toInt() } ?: 0
    val shown = elapsed.coerceAtMost(maxSec)
    val tooShort = elapsed < Rules.MIN_RECORD_SEC
    val samples = remember(since) { mutableStateListOf<Float>().apply { repeat(48) { add(0f) } } }
    var input by remember(since) { mutableFloatStateOf(0f) }
    val breathing by animateFloatAsState(input, tween(180), label = "recording-capsule-input")
    LaunchedEffect(since, animate) {
        while (true) {
            val value = level().coerceIn(0f, 1f)
            input = if (animate) value else 0f
            if (animate) { samples.removeAt(0); samples.add(value) }
            delay(100)
        }
    }
    val red = Color(0xFFD92D3A)
    val waveform = if (Tc.P.isDark) Color(0xFFFF7B85) else red
    Column(Modifier.fillMaxSize().background(Tc.Bg).padding(Tc.Gutter), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Dot(red, 12.dp)
            Text("녹음 중", style = Type.TitleS, color = Tc.Text)
            Spacer(Modifier.weight(1f))
            Text(lengthLabel(maxSec), style = Type.LabelM, color = Tc.TextSub)
        }
        Surface(Modifier.weight(1f).fillMaxWidth(), color = Tc.Basic, shape = RoundedCornerShape(24.dp)) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(24.dp)) {
                val compact = maxHeight < 460.dp
                Column(Modifier.fillMaxSize().widthIn(max = 960.dp).align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 16.dp, Alignment.CenterVertically)) {
                    Text(place ?: "위치 확인 중", style = Type.TitleS, color = Tc.Text)
                    Text("%02d:%02d".format(shown / 60, shown % 60),
                        style = Type.Display.copy(fontSize = if (compact) 72.sp else 104.sp, lineHeight = if (compact) 84.sp else 116.sp, fontFeatureSettings = "tnum"), color = Tc.Text)
                    RecordingCapsule(Modifier.size(if (compact) 120.dp else 176.dp).graphicsLayer {
                        scaleX = 1f + breathing * 0.08f
                        scaleY = 1f + breathing * 0.08f
                    }, input = breathing)
                    Canvas(Modifier.fillMaxWidth(0.8f).height(if (compact) 40.dp else 64.dp)) {
                        val step = size.width / samples.size
                        samples.forEachIndexed { index, value ->
                            val half = (4.dp.toPx() + value * (size.height / 2 - 4.dp.toPx())).coerceAtLeast(2f)
                            val x = step * (index + 0.5f)
                            drawLine(waveform.copy(alpha = 0.35f + 0.65f * (index + 1) / samples.size),
                                Offset(x, center.y - half), Offset(x, center.y + half),
                                strokeWidth = minOf(6.dp.toPx(), step * 0.6f), cap = StrokeCap.Round)
                        }
                    }
                    if (tags.isNotEmpty()) Text(tags.map { it.label }.filter { it != place }.joinToString(Sep),
                        style = Type.LabelM, color = Tc.TextSub, textAlign = TextAlign.Center, maxLines = 2)
                    LinearProgressIndicator(progress = { shown / maxSec.coerceAtLeast(1).toFloat() },
                        modifier = Modifier.fillMaxWidth(0.7f).height(4.dp), color = waveform,
                        trackColor = Tc.Bg, drawStopIndicator = {})
                    Text("${(maxSec - shown).coerceAtLeast(0)}초 남음", style = Type.LabelM, color = Tc.TextSub)
                }
            }
        }
        Text(when {
            tooShort -> "3초 이상 말해 주세요"
            silenceSec > 0 -> "말을 마치면 저장하세요 · ${silenceSec}초 동안 말이 없으면 자동 저장돼요"
            else -> "말을 마치면 녹음 완료를 눌러 주세요"
        }, style = Type.LabelM, color = Tc.TextSub, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Btn("녹음 취소", Modifier.weight(1f), icon = Icons.Rounded.Close, height = Tc.TouchLarge, onClick = onCancel)
            Surface(onClick = clickWithSound(onStop), enabled = !tooShort,
                modifier = Modifier.weight(2f).height(Tc.TouchLarge), shape = RoundedCornerShape(20.dp),
                color = if (tooShort) Tc.Basic else red) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Stop, null, Modifier.size(36.dp), tint = if (tooShort) Tc.TextMute else Color.White)
                    Text("녹음 완료 · 저장", style = Type.Button, color = if (tooShort) Tc.TextMute else Color.White)
                }
            }
        }
    }
}

/**
 * Screen 05: approach alert. Same shape and place as a Pleos system notification (top, white,
 * shadow, no dimming of the screen behind). When the app is not on screen the same alert is
 * posted as a real heads-up notification instead (AlertNotifier), so it shows over navigation.
 */
@Composable
fun AlertCard(alert: AlertUi, onListen: () -> Unit, onLater: () -> Unit, hearing: Boolean = false) {
    val now = rememberNow(alert.shownAt)
    val leftMs = (Rules.ALERT_TIMEOUT_MS - (now - alert.shownAt)).coerceAtLeast(0)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        SystemNotice(
            title = alert.line,
            description = alertDescription(alert) + if (hearing) Sep + "'들려줘' 또는 '다음에'라고 말해도 돼요" else "",
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
    return (listOf(where) + about).joinToString(Sep)
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
                Text(c.ownerName?.let { "${it}님이 남김$Sep$date" } ?: date, style = Type.LabelM, color = Tc.TextMute)
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
                    app.timecapsule.tag.TheDay.facts(c.createdAt, c.rawVehicleState).joinToString(Sep),
                    style = Type.BodyM, color = Tc.TextSub,
                )
                VoiceProgress(pos, c.ownerName)
            }
            if (safetyPaused != null) {
                Panel(Modifier.padding(bottom = 16.dp)) {
                    ListRow(
                        if (safetyPaused.isEmpty()) "이어 듣기를 기다리고 있어요" else "${safetyPaused.joinToString(", ") { it.korean() }} 때문에 잠시 멈췄어요",
                        "안내가 끝나면 이어 듣기 버튼을 눌러 주세요",
                        leading = Icons.Rounded.PauseCircle,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Btn("처음부터", Modifier.weight(1f), icon = Icons.Rounded.Replay, height = Tc.TouchLarge, onClick = onRestart)
                Btn(
                    if (userPaused || safetyPaused != null) "이어 듣기" else "일시정지", Modifier.weight(1f),
                    icon = if (userPaused || safetyPaused != null) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
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
    val frac = pos?.takeIf { it.second > 0 }?.let { (it.first.toFloat() / it.second).coerceIn(0f, 1f) } ?: 0f
    Column(Modifier.fillMaxWidth().widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.LinearProgressIndicator(
            progress = { frac },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = Tc.Text, trackColor = Tc.TextMute.copy(alpha = 0.25f), drawStopIndicator = {},
        )
        Text(
            if (pos == null) "안내 뒤 ${owner?.let { "${it}님의 " } ?: ""}목소리가 나와요"
            else "${owner?.let { "${it}님의 " } ?: ""}목소리$Sep${t(pos.first)} / ${t(pos.second)}",
            style = Type.LabelM, color = Tc.TextMute,
        )
    }
}

fun SafetySignal.korean() = when (this) {
    SafetySignal.SAFETY_WARNING -> "안전 경고"
    SafetySignal.HARD_BRAKE -> "제동"
    SafetySignal.TURN_SIGNAL -> "방향지시등"
    SafetySignal.SHARP_TURN -> "회전"
    SafetySignal.NAVI_VOICE -> "길 안내 음성"
}
