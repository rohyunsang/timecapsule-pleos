package app.timecapsule.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.timecapsule.TimeCapsuleApp
import app.timecapsule.capsule.isLocked
import app.timecapsule.data.Capsule
import app.timecapsule.data.Trip
import app.timecapsule.drive.DriveController
import app.timecapsule.geo.Geo
import app.timecapsule.geo.GeoPoint
import app.timecapsule.navi.FakeNaviSource
import app.timecapsule.navi.NamedPoint
import app.timecapsule.navi.NaviPosition
import app.timecapsule.tag.MoodTagEngine
import app.timecapsule.tag.Narration
import app.timecapsule.vehicle.FakeVehicleSource
import app.timecapsule.vehicle.SafetySignal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class Screen { HOME, LETTER, ALBUM, SETTINGS }

private fun Capsule.date(): LocalDate = Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
private val dayFmt = DateTimeFormatter.ofPattern("yyyy년 M월 d일")

/** Screen 01: home. Left = act now, right = what is around you. */
@Composable
fun HomeScreen(
    drive: DriveController,
    parked: Boolean,
    onPleos: Boolean,
    go: (Screen) -> Unit,
    onDev: () -> Unit,
) {
    val car by drive.car.collectAsState()
    val capsules by drive.capsules.collectAsState()
    val forecast by drive.forecast.collectAsState()
    val reprompt by drive.reprompt.collectAsState()
    val arrival by drive.arrivalPrompt.collectAsState()
    val position by drive.position.collectAsState()
    val today = LocalDate.now()

    Row(Modifier.fillMaxSize().padding(Tc.Gutter), horizontalArrangement = Arrangement.spacedBy(Tc.Gutter)) {
        Column(Modifier.weight(1.1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Tc.Gap)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("타임캡슐", style = Type.Headline, color = Tc.Text)
                Spacer(Modifier.width(16.dp))
                Dot(if (onPleos) Tc.Accent else Tc.TextMute, 8.dp)
                Spacer(Modifier.width(8.dp))
                Meta(if (onPleos) "차량 연결됨" else "가짜 차량")
                Spacer(Modifier.weight(1f))
                Btn("개발", icon = Icons.Rounded.Tune, style = BtnStyle.Quiet, height = 56.dp, onClick = onDev)
            }

            Card(Modifier.weight(1f), fill = true) {
                Spacer(Modifier.weight(1f))
                Eyebrow("지금 이 순간 · ${car.placeName ?: "위치 확인 중"}", color = Tc.Accent)
                Headline("이 길의 지금을 목소리로 남겨요")
                val now = MoodTagEngine.tagsFor(car)
                if (now.isEmpty()) Body("지금 차 상태로 붙을 태그가 없어요") else TagRow(now)
                Spacer(Modifier.height(16.dp))
                Btn(
                    "목소리 묻기", Modifier.fillMaxWidth(), icon = Icons.Rounded.Mic,
                    style = BtnStyle.Primary, height = 112.dp,
                ) { drive.bury() }
                Meta("버튼을 누를 때만 녹음해요 · 최대 30초")
                Spacer(Modifier.weight(1f))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Btn("편지 캡슐", Modifier.weight(1f), icon = Icons.Rounded.MailOutline, enabled = parked) { go(Screen.LETTER) }
                Btn("여행 앨범", Modifier.weight(1f), icon = Icons.Rounded.PhotoLibrary, enabled = parked) { go(Screen.ALBUM) }
                Btn("설정", Modifier.weight(1f), icon = Icons.Rounded.Settings, enabled = parked) { go(Screen.SETTINGS) }
            }
            if (!parked) Eyebrow("편지·앨범·설정은 차를 세우면 열려요", Icons.Rounded.Lock)
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (arrival) {
                Card(accent = true) {
                    Eyebrow("도착", Icons.Rounded.Directions, Tc.Accent)
                    Title("도착 한마디 남길까요?")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Btn("남기기", Modifier.weight(1f), icon = Icons.Rounded.Mic, style = BtnStyle.Primary) { drive.answerArrival(true) }
                        Btn("괜찮아요", Modifier.weight(1f)) { drive.answerArrival(false) }
                    }
                }
            }
            val again = reprompt
            if (again != null && parked) {
                Card(accent = true) {
                    Eyebrow("아까 지나친 캡슐", Icons.Rounded.History, Tc.Accent)
                    Title("${Narration.elapsed(again.date(), today)}, ${again.placeName ?: "이 길"}")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Btn("들려줘", Modifier.weight(1f), icon = Icons.Rounded.PlayArrow, style = BtnStyle.Primary) { drive.playCapsule(again.id) }
                        Btn("닫기", Modifier.weight(1f)) { drive.dismissReprompt() }
                    }
                }
            }
            Card {
                Eyebrow("오늘 길", Icons.Rounded.Route)
                val f = forecast
                if (f != null && f.total > 0) {
                    Title(f.message() ?: "")
                    f.open.firstOrNull()?.let { oldest ->
                        val dist = position?.point?.let { Geo.distanceM(it, GeoPoint(oldest.lat, oldest.lng)) }
                        Body("가장 오래된 건 ${Narration.elapsed(oldest.date(), today)} ${oldest.placeName ?: ""}" +
                            (dist?.let { " · ${formatDistance(it)} 남음" } ?: ""))
                    }
                } else {
                    Body("내비 안내를 시작하면 이 길에 묻힌 캡슐을 알려 드려요")
                }
            }
            Card {
                Eyebrow("내 캡슐")
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${capsules.size}", style = Type.Headline, color = Tc.Text)
                    Spacer(Modifier.width(6.dp))
                    Body("개 묻혀 있어요")
                }
                capsules.minByOrNull { it.createdAt }?.let { first ->
                    Divider()
                    Meta("가장 오래된 캡슐")
                    Body("${first.date().format(dayFmt)} · ${first.placeName ?: "장소 기록 없음"}", Tc.Text)
                    TagRow(MoodTagEngine.fromStored(first.tags))
                }
            }
        }
    }
}

/** Selectable option card (letter date). */
@Composable
private fun Choice(label: String, sub: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 96.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) Tc.AccentSoft else Tc.Surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Tc.Accent else Tc.Line),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = Type.Label, color = if (selected) Tc.Accent else Tc.Text)
            Meta(sub)
        }
    }
}

/** Screen 02: letter capsule (parked only). */
@Composable
fun LetterScreen(drive: DriveController, back: () -> Unit) {
    val today = LocalDate.now()
    var openOn by remember { mutableStateOf(today.plusYears(1)) }
    val choices = listOf(
        "1년 뒤" to today.plusYears(1),
        "10년 뒤" to today.plusYears(10),
        "100일 기념일" to today.plusDays(99),
    )
    Column(Modifier.fillMaxSize().padding(Tc.Gutter), verticalArrangement = Arrangement.spacedBy(Tc.Gap)) {
        Header("편지 캡슐", back)
        Body("정한 날이 오기 전에는 이 길을 지나도 열리지 않아요. 그날이 지나면 보통 캡슐처럼 들을 수 있어요.")
        Eyebrow("열리는 날")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            choices.forEach { (label, date) ->
                Choice(label, date.format(dayFmt), openOn == date, Modifier.weight(1f)) { openOn = date }
            }
        }
        Card {
            Eyebrow("직접 고르기")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Btn("1달", icon = Icons.Rounded.Remove) { if (openOn.minusMonths(1).isAfter(today)) openOn = openOn.minusMonths(1) }
                Text(openOn.format(dayFmt), style = Type.Title, color = Tc.Text, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                Btn("1달", icon = Icons.Rounded.Add) { openOn = openOn.plusMonths(1) }
                Btn("1년", icon = Icons.Rounded.Add) { openOn = openOn.plusYears(1) }
            }
        }
        Spacer(Modifier.weight(1f))
        Meta("받는 사람 지정은 운전자 구분 API가 열리면 추가할 예정이에요")
        Btn("녹음하고 묻기", Modifier.fillMaxWidth(), icon = Icons.Rounded.Mic, style = BtnStyle.Primary, height = 96.dp) {
            drive.bury(openOn)
            back()
        }
    }
}

/** Screen 03: trip album (parked only). */
@Composable
fun AlbumScreen(drive: DriveController, back: () -> Unit) {
    val capsules by drive.capsules.collectAsState()
    val locks by drive.locks.collectAsState()
    val trips by drive.trips.collectAsState()
    val today = LocalDate.now()
    val tripById = trips.associateBy { it.id }
    // F-08: group by trip; capsules outside a trip are grouped by day.
    val groups = capsules.groupBy { c -> c.tripId?.let { "trip:$it" } ?: "day:${c.date()}" }
        .entries.sortedByDescending { e -> e.value.maxOf { it.createdAt } }

    Column(Modifier.fillMaxSize().padding(Tc.Gutter), verticalArrangement = Arrangement.spacedBy(Tc.Gap)) {
        Header("여행 앨범", back)
        if (capsules.isEmpty()) Body("아직 묻은 캡슐이 없어요. 홈에서 목소리를 묻어 보세요.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(groups, key = { it.key }) { (key, list) ->
                val trip = if (key.startsWith("trip:")) tripById[key.removePrefix("trip:").toLong()] else null
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Title(groupTitle(trip, list))
                            Meta("캡슐 ${list.size}개" + (trip?.let { tripSummary(it) } ?: ""))
                        }
                        Btn("이 길 다시 달리기", icon = Icons.Rounded.Route) { drive.driveMemoryRoute(list) }
                    }
                    list.sortedBy { it.createdAt }.forEach { c ->
                        Divider()
                        CapsuleRow(c, locks[c.id]?.openAfter?.takeIf { locks[c.id].isLocked(today) }, drive)
                    }
                }
            }
        }
    }
}

@Composable
private fun CapsuleRow(c: Capsule, lockedUntil: Long?, drive: DriveController) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(
            if (lockedUntil != null) Icons.Rounded.Lock else Icons.Rounded.GraphicEq,
            contentDescription = if (lockedUntil != null) "잠긴 편지" else null,
            tint = if (lockedUntil != null) Tc.Accent else Tc.TextMute,
            modifier = Modifier.size(28.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "${c.placeName ?: "장소 기록 없음"}  ·  ${c.durationSec}초" + (if (c.locationUncertain) "  ·  위치 불확실" else ""),
                style = Type.Label, color = Tc.Text,
            )
            if (lockedUntil != null) {
                Meta("${LocalDate.ofEpochDay(lockedUntil).format(dayFmt)}에 열리는 편지")
            } else {
                Meta(c.date().format(dayFmt))
                TagRow(MoodTagEngine.fromStored(c.tags))
            }
        }
        Btn("듣기", icon = Icons.Rounded.PlayArrow, style = BtnStyle.Primary, enabled = lockedUntil == null) { drive.playCapsule(c.id) }
        Btn("삭제", icon = Icons.Rounded.DeleteOutline) { drive.delete(c) }
    }
}

private fun groupTitle(trip: Trip?, list: List<Capsule>): String {
    val first = list.minOf { it.createdAt }
    val day = Instant.ofEpochMilli(trip?.startAt ?: first).atZone(ZoneId.systemDefault()).toLocalDate().format(dayFmt)
    return if (trip != null) "$day 여행" else day
}

private fun tripSummary(t: Trip): String {
    val km = if (t.startOdometer != null && t.endOdometer != null) " · ${"%.0f".format(t.endOdometer - t.startOdometer)}km" else ""
    val who = t.occupants?.takeIf { it > 1 }?.let { " · ${it}명" } ?: ""
    return km + who
}

@Composable
private fun SettingRow(title: String, sub: String, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = Tc.Touch), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = Type.Label, color = Tc.Text)
            Meta(sub)
        }
        Spacer(Modifier.width(24.dp))
        trailing()
    }
}

/** F-13 settings (parked only). */
@Composable
fun SettingsScreen(app: TimeCapsuleApp, back: () -> Unit) {
    val arrival by app.settings.arrivalPrompt.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().padding(Tc.Gutter).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Tc.Gap),
    ) {
        Header("설정", back)
        Column(
            Modifier.widthIn(max = 1040.dp).fillMaxWidth().align(Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Eyebrow("개인정보")
            Card {
                SettingRow("녹음은 직접 부를 때만", "버튼을 눌렀을 때만 마이크가 켜져요. 상시 청취는 하지 않아요.")
                Divider()
                SettingRow("녹음 알림음", "시작·끝 알림음은 함께 탄 사람을 위해 끌 수 없어요.") { Meta("항상 켜짐") }
                Divider()
                SettingRow("저장 위치", "캡슐은 이 차 안에만 저장돼요. 계정 동기화는 준비 중이에요.") { Meta("차량만") }
            }
            Eyebrow("알림")
            Card {
                SettingRow("도착 제안", "처음 온 곳에 도착하면 한마디 남길지 물어봐요. 하루 2번까지.") {
                    Switch(
                        arrival, app.settings::setArrivalPrompt,
                        colors = SwitchDefaults.colors(checkedTrackColor = Tc.Accent, checkedThumbColor = Tc.OnAccent),
                    )
                }
            }
            Eyebrow("차량을 넘길 때")
            Card {
                SettingRow("차량에서 모두 지우기", "모든 캡슐과 녹음 파일을 지워요. 되돌릴 수 없어요.") {
                    Btn("모두 지우기", icon = Icons.Rounded.DeleteOutline) { confirm = true }
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = Tc.Surface,
            title = { Title("모든 캡슐을 지울까요?") },
            text = { Body("녹음 파일까지 모두 지워지고 되돌릴 수 없어요.") },
            confirmButton = {
                TextButton({ confirm = false; app.drive.deleteEverything(app) }) {
                    Text("지우기", style = Type.Label, color = Tc.Danger)
                }
            },
            dismissButton = { TextButton({ confirm = false }) { Text("취소", style = Type.Label, color = Tc.Text) } },
        )
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (on) Tc.AccentSoft else Tc.Surface2,
        border = BorderStroke(1.dp, if (on) Tc.Accent else Tc.Line),
    ) {
        Text(label, style = Type.Tag, color = if (on) Tc.Accent else Tc.TextSub, modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp))
    }
}

/** Dev panel for demos: fake car state, safety signals, approach and route. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DevPanel(app: TimeCapsuleApp, close: () -> Unit) {
    val capsules by app.drive.capsules.collectAsState()
    val signals by app.drive.signals.collectAsState()
    val fakeCar = app.vehicle as? FakeVehicleSource
    val fakeNavi = app.navi as? FakeNaviSource
    Card(Modifier.padding(Tc.Gap).widthIn(max = 1100.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Title("개발 패널")
            Spacer(Modifier.weight(1f))
            Btn("닫기", style = BtnStyle.Quiet, height = 56.dp, onClick = close)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("가짜 차량·내비 모드", style = Type.Label, color = Tc.Text)
                Meta("Pleos 기기에서도 가짜 값으로 시연해요. 앱을 다시 켜면 적용돼요.")
            }
            var fake by remember { mutableStateOf(app.forceFake) }
            Switch(
                fake, { fake = it; app.setForceFake(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = Tc.Accent, checkedThumbColor = Tc.OnAccent),
            )
        }
        if (fakeCar != null) {
            val s by fakeCar.snapshot.collectAsState()
            Eyebrow("차량 상태")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Toggle("와이퍼", s.wiperOn == true) { fakeCar.set { it.copy(wiperOn = it.wiperOn != true) } }
                Toggle("전조등", s.headlightOn == true) { fakeCar.set { it.copy(headlightOn = it.headlightOn != true) } }
                Toggle("창문", s.anyWindowOpen == true) { fakeCar.set { it.copy(anyWindowOpen = it.anyWindowOpen != true) } }
                Toggle("고속도로", s.onHighway == true) { fakeCar.set { it.copy(onHighway = it.onHighway != true) } }
                Toggle("P단", s.gearInPark == true) { fakeCar.set { it.copy(gearInPark = it.gearInPark != true) } }
                Toggle("문 열림", s.anyDoorOpen == true) { fakeCar.set { it.copy(anyDoorOpen = it.anyDoorOpen != true) } }
                Toggle("탑승 ${s.occupiedSeats}명", false) { fakeCar.set { it.copy(occupiedSeats = ((it.occupiedSeats ?: 1) % 4) + 1) } }
                Toggle("${s.outsideTempC?.toInt()}℃", false) { fakeCar.set { it.copy(outsideTempC = if ((it.outsideTempC ?: 18f) > 5f) 2f else 18f) } }
            }
            Eyebrow("안전 신호")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SafetySignal.entries.filter { it != SafetySignal.NAVI_VOICE }.forEach { sig ->
                    Toggle(sig.korean(), sig in signals) { fakeCar.setSignal(sig, sig !in signals) }
                }
                // Arms a turn signal for the next playback: on 2 s after it starts, off 5 s later.
                Toggle("다음 재생 때 방향지시등", false) {
                    app.scope.launch {
                        app.drive.state.first { it is app.timecapsule.drive.DriveState.Playing }
                        kotlinx.coroutines.delay(2_000)
                        fakeCar.setSignal(SafetySignal.TURN_SIGNAL, true)
                        kotlinx.coroutines.delay(5_000)
                        fakeCar.setSignal(SafetySignal.TURN_SIGNAL, false)
                    }
                }
            }
        } else {
            Meta("차량 값은 Virtual Console(127.0.0.1:8088)에서 바꾸세요")
        }
        Eyebrow("시연")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val newest = capsules.maxByOrNull { it.createdAt }
            Btn("최근 캡슐을 18년 전으로", enabled = newest != null, height = 64.dp) { newest?.let { app.drive.backdate(it.id, 18) } }
            Btn("1.2km 접근", enabled = newest != null, height = 64.dp) { newest?.let { app.drive.simulateApproach(it.id) } }
            if (fakeNavi != null) {
                Btn("캡슐 지나는 경로 시작", enabled = capsules.isNotEmpty(), height = 64.dp) {
                    val c = capsules.first()
                    fakeNavi.moveTo(NaviPosition(GeoPoint(c.lat - 0.02, c.lng), "강원특별자치도 강릉시 7번 국도"))
                    fakeNavi.drive(NamedPoint("경포해변", GeoPoint(c.lat + 0.02, c.lng)), emptyList())
                }
                Btn("도착", height = 64.dp) {
                    fakeNavi.arrive()
                    fakeNavi.moveTo(NaviPosition(GeoPoint(37.80, 128.95), "강원특별자치도 강릉시 경포로"))
                }
            }
        }
    }
}
