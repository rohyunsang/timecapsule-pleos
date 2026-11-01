package app.timecapsule.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.sp
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class Screen { HOME, LETTER, ALBUM, SETTINGS }

private fun Capsule.date(): LocalDate = Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
private val dayFmt = DateTimeFormatter.ofPattern("yyyy년 M월 d일")

/** Screen 01: home. */
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

    Row(Modifier.fillMaxSize().padding(40.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Title("타임캡슐")
                Spacer(Modifier.weight(1f))
                TextButton(onDev) { Caption(if (onPleos) "Pleos 연결됨 · 개발" else "가짜 차량 · 개발") }
            }
            Caption("묻힌 캡슐 ${capsules.size}개 · ${car.placeName ?: "위치 확인 중"}")
            BigButton("지금 이 순간 묻기", Modifier.fillMaxWidth().height(140.dp)) { drive.bury() }
            Caption("녹음은 버튼이나 핸들 음성 버튼을 누를 때만 시작해요")
            TagRow(MoodTagEngine.tagsFor(car))
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BigButton("편지 캡슐", Modifier.weight(1f), enabled = parked, primary = false) { go(Screen.LETTER) }
                BigButton("여행 앨범", Modifier.weight(1f), enabled = parked, primary = false) { go(Screen.ALBUM) }
                BigButton("설정", Modifier.weight(1f), enabled = parked, primary = false) { go(Screen.SETTINGS) }
            }
            if (!parked) Caption("편지·앨범·설정은 정차 중에만 열 수 있어요")
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (arrival) {
                Panel(highlight = true) {
                    Text("도착 한마디 남길까요?", fontSize = 28.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        BigButton("남기기", Modifier.weight(1f)) { drive.answerArrival(true) }
                        BigButton("괜찮아요", Modifier.weight(1f), primary = false) { drive.answerArrival(false) }
                    }
                }
            }
            val again = reprompt
            if (again != null && parked) {
                Panel(highlight = true) {
                    Caption("아까 지나친 캡슐")
                    Text("${Narration.elapsed(again.date(), today)}, ${again.placeName ?: "이 길"}", fontSize = 28.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        BigButton("들려줘", Modifier.weight(1f)) { drive.playCapsule(again.id) }
                        BigButton("닫기", Modifier.weight(1f), primary = false) { drive.dismissReprompt() }
                    }
                }
            }
            val f = forecast
            if (f != null && f.total > 0) {
                Panel {
                    Caption("오늘 길")
                    Text(f.message() ?: "", fontSize = 26.sp)
                    f.open.firstOrNull()?.let { oldest ->
                        val dist = position?.point?.let { Geo.distanceM(it, GeoPoint(oldest.lat, oldest.lng)) }
                        Body("가장 오래된 캡슐: ${Narration.elapsed(oldest.date(), today)} · ${oldest.placeName ?: ""}" +
                            (dist?.let { " · ${formatDistance(it)} 남음" } ?: ""))
                    }
                }
            } else {
                Panel {
                    Caption("오늘 길")
                    Body("내비 안내를 시작하면 이 길에 묻힌 캡슐을 알려 드려요")
                }
            }
            capsules.minByOrNull { it.createdAt }?.let { first ->
                Panel {
                    Caption("가장 오래된 캡슐")
                    Body("${first.date().format(dayFmt)} · ${first.placeName ?: "장소 기록 없음"}")
                    TagRow(MoodTagEngine.fromStored(first.tags))
                }
            }
        }
    }
}

/** Screen 02: letter capsule (parked only). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LetterScreen(drive: DriveController, back: () -> Unit) {
    val today = LocalDate.now()
    var openOn by remember { mutableStateOf(today.plusYears(1)) }
    val choices = listOf(
        "1년 뒤" to today.plusYears(1),
        "10년 뒤" to today.plusYears(10),
        "100일 기념일" to today.plusDays(99),
    )
    Column(Modifier.fillMaxSize().padding(40.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Title("편지 캡슐")
            Spacer(Modifier.weight(1f))
            BigButton("뒤로", primary = false, onClick = back)
        }
        Body("열리는 날 전에는 이 길을 지나도 내용이 열리지 않아요. 날짜가 지나면 일반 캡슐처럼 들을 수 있어요.")
        Caption("열리는 날")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            choices.forEach { (label, date) ->
                FilterChip(openOn == date, { openOn = date }, { Text(label, fontSize = 22.sp, modifier = Modifier.padding(12.dp)) })
            }
        }
        Caption("직접 고르기")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            BigButton("− 1달", primary = false) { if (openOn.minusMonths(1).isAfter(today)) openOn = openOn.minusMonths(1) }
            Text(openOn.format(dayFmt), fontSize = 32.sp)
            BigButton("+ 1달", primary = false) { openOn = openOn.plusMonths(1) }
            BigButton("+ 1년", primary = false) { openOn = openOn.plusYears(1) }
        }
        Caption("받는 사람 지정은 운전자 구분 API가 열리면 추가돼요 (P1)")
        Spacer(Modifier.weight(1f))
        BigButton("녹음하고 묻기", Modifier.fillMaxWidth()) {
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

    Column(Modifier.fillMaxSize().padding(40.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Title("여행 앨범")
            Spacer(Modifier.weight(1f))
            BigButton("뒤로", primary = false, onClick = back)
        }
        if (capsules.isEmpty()) Body("아직 묻은 캡슐이 없어요")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            items(groups, key = { it.key }) { (key, list) ->
                val trip = key.removePrefix("trip:").toLongOrNull()?.let { if (key.startsWith("trip:")) tripById[it] else null }
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(groupTitle(trip, list), fontSize = 26.sp)
                            Caption("캡슐 ${list.size}개" + (trip?.let { tripSummary(it) } ?: ""))
                        }
                        BigButton("이 길 다시 달리기", primary = false) { drive.driveMemoryRoute(list) }
                    }
                    list.sortedBy { it.createdAt }.forEach { c ->
                        val locked = locks[c.id].isLocked(today)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Body(
                                    (if (locked) "🔒 " else "") + "${c.date().format(dayFmt)} · ${c.placeName ?: "장소 기록 없음"}" +
                                        (if (c.locationUncertain) " (위치 불확실)" else "") + " · ${c.durationSec}초",
                                )
                                if (locked) {
                                    Caption("${LocalDate.ofEpochDay(locks[c.id]!!.openAfter).format(dayFmt)}에 열려요")
                                } else {
                                    TagRow(MoodTagEngine.fromStored(c.tags))
                                }
                            }
                            BigButton("듣기", enabled = !locked) { drive.playCapsule(c.id) }
                            BigButton("삭제", primary = false) { drive.delete(c) }
                        }
                    }
                }
            }
        }
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

/** F-13 settings (parked only). */
@Composable
fun SettingsScreen(app: TimeCapsuleApp, back: () -> Unit) {
    val arrival by app.settings.arrivalPrompt.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().padding(40.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Title("설정 · 개인정보")
            Spacer(Modifier.weight(1f))
            BigButton("뒤로", primary = false, onClick = back)
        }
        Panel {
            Body("녹음은 직접 부를 때만")
            Caption("묻기 버튼이나 핸들 음성 버튼을 눌렀을 때만 마이크가 켜져요. 상시 청취는 하지 않아요. 녹음 시작·끝 알림음은 끌 수 없어요.")
        }
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Body("도착 제안")
                    Caption("처음 온 곳에 도착하면 한마디 남길지 물어봐요 (하루 2번까지)")
                }
                Switch(arrival, app.settings::setArrivalPrompt)
            }
        }
        Panel {
            Body("저장 위치: 차량만")
            Caption("캡슐은 이 차 안에만 저장돼요. 계정 동기화는 준비 중이에요.")
        }
        Panel {
            Body("차량에서 모두 지우기")
            Caption("차를 팔거나 넘길 때 쓰세요. 모든 캡슐과 녹음 파일이 지워지고 되돌릴 수 없어요.")
            BigButton("모두 지우기", primary = false) { confirm = true }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("모든 캡슐을 지울까요?", fontSize = 26.sp) },
            text = { Body("녹음 파일까지 모두 지워지고 되돌릴 수 없어요.") },
            confirmButton = {
                TextButton({ confirm = false; app.drive.deleteEverything(app) }) {
                    Text("지우기", fontSize = 22.sp, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton({ confirm = false }) { Text("취소", fontSize = 22.sp) } },
        )
    }
}

/** Dev panel for demos without a real car: fake car state, safety signals, approach and route. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DevPanel(app: TimeCapsuleApp, close: () -> Unit) {
    val capsules by app.drive.capsules.collectAsState()
    val signals by app.drive.signals.collectAsState()
    val fakeCar = app.vehicle as? FakeVehicleSource
    val fakeNavi = app.navi as? FakeNaviSource
    Panel(Modifier.padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Title("개발 패널")
            Spacer(Modifier.weight(1f))
            BigButton("닫기", primary = false, onClick = close)
        }
        if (fakeCar != null) {
            val s by fakeCar.snapshot.collectAsState()
            Caption("가짜 차량 상태")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(s.wiperOn == true, { fakeCar.set { it.copy(wiperOn = it.wiperOn != true) } }, { Text("와이퍼") })
                FilterChip(s.headlightOn == true, { fakeCar.set { it.copy(headlightOn = it.headlightOn != true) } }, { Text("전조등") })
                FilterChip(s.anyWindowOpen == true, { fakeCar.set { it.copy(anyWindowOpen = it.anyWindowOpen != true) } }, { Text("창문") })
                FilterChip(s.onHighway == true, { fakeCar.set { it.copy(onHighway = it.onHighway != true) } }, { Text("고속도로") })
                FilterChip(s.gearInPark == true, { fakeCar.set { it.copy(gearInPark = it.gearInPark != true) } }, { Text("P단") })
                FilterChip(s.anyDoorOpen == true, { fakeCar.set { it.copy(anyDoorOpen = it.anyDoorOpen != true) } }, { Text("문 열림") })
                FilterChip(false, { fakeCar.set { it.copy(occupiedSeats = ((it.occupiedSeats ?: 1) % 4) + 1) } }, { Text("탑승 ${s.occupiedSeats}명") })
                FilterChip(false, { fakeCar.set { it.copy(outsideTempC = if ((it.outsideTempC ?: 18f) > 5f) 2f else 18f) } }, { Text("${s.outsideTempC}℃") })
            }
            Caption("안전 신호")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SafetySignal.entries.filter { it != SafetySignal.NAVI_VOICE }.forEach { sig ->
                    FilterChip(sig in signals, { fakeCar.setSignal(sig, sig !in signals) }, { Text(sig.korean()) })
                }
            }
        } else {
            Caption("Pleos 차량 연결됨: 차량 값은 Virtual Console(127.0.0.1:8088)에서 바꾸세요")
        }
        Caption("시연")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val newest = capsules.maxByOrNull { it.createdAt }
            BigButton("최근 캡슐을 18년 전으로", enabled = newest != null, primary = false) { newest?.let { app.drive.backdate(it.id, 18) } }
            BigButton("1.2km 접근", enabled = newest != null, primary = false) { newest?.let { app.drive.simulateApproach(it.id) } }
            if (fakeNavi != null) {
                BigButton("캡슐 지나는 경로 시작", enabled = capsules.isNotEmpty(), primary = false) {
                    val c = capsules.first()
                    fakeNavi.moveTo(NaviPosition(GeoPoint(c.lat - 0.02, c.lng), "강원특별자치도 강릉시 7번 국도"))
                    fakeNavi.drive(NamedPoint("경포해변", GeoPoint(c.lat + 0.02, c.lng)), emptyList())
                }
                BigButton("도착", primary = false) {
                    fakeNavi.arrive()
                    fakeNavi.moveTo(NaviPosition(GeoPoint(37.80, 128.95), "강원특별자치도 강릉시 경포로"))
                }
            }
        }
    }
}
