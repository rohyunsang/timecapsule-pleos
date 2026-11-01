package app.timecapsule.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.timecapsule.TimeCapsuleApp
import app.timecapsule.capsule.RouteForecast
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

// ---- Home (screen 01) ---------------------------------------------------------------------

/**
 * Home. Glanceable in 1-2 s (Pleos): one big voice button, one "now" message, three ways out.
 * Wide (full window) puts "now" on the right; compact (1:1 next to navigation) stacks it.
 */
@Composable
fun HomeScreen(
    drive: DriveController,
    restricted: Boolean,
    onPleos: Boolean,
    go: (Screen) -> Unit,
    onLocked: () -> Unit,
    onDev: () -> Unit,
) {
    val car by drive.car.collectAsState()
    val capsules by drive.capsules.collectAsState()
    val forecast by drive.forecast.collectAsState()
    val reprompt by drive.reprompt.collectAsState()
    val arrival by drive.arrivalPrompt.collectAsState()
    val position by drive.position.collectAsState()
    val locks by drive.locks.collectAsState()
    val today = LocalDate.now()

    val notices = buildList<@Composable (Modifier) -> Unit> {
        if (arrival) add { m ->
            Notice(Icons.Rounded.Flag, "도착했어요. 한마디 남길까요?", "이곳엔 아직 캡슐이 없어요", m, accent = true, actions = { b ->
                Btn("괜찮아요", b) { drive.answerArrival(false) }
                Btn("남기기", b, icon = Icons.Rounded.Mic, style = BtnStyle.Primary) { drive.answerArrival(true) }
            })
        }
        val again = reprompt
        if (again != null && !restricted) add { m ->
            Notice(Icons.Rounded.History, "아까 지나친 캡슐", "${Narration.elapsed(again.date(), today)}, ${again.placeName ?: "이 길"}", m, accent = true, actions = { b ->
                Btn("닫기", b) { drive.dismissReprompt() }
                Btn("들려줘", b, icon = Icons.Rounded.PlayArrow, style = BtnStyle.Primary) { drive.playCapsule(again.id) }
            })
        }
        val f = forecast
        if (f != null && f.total > 0) add { m -> ForecastNotice(f, position?.point, today, m) }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Tc.Bg)) {
        val compact = widthClass(maxWidth) == WidthClass.Compact
        val bar: @Composable () -> Unit = { TopBar(onPleos, restricted, onDev) }
        val hero: @Composable (Modifier) -> Unit = { m -> VoiceHero(drive, compact, m) }
        val nav: @Composable () -> Unit = {
            NavRow(restricted, go, onLocked)
        }
        if (compact) {
            Column(Modifier.fillMaxSize().padding(Tc.Gutter), verticalArrangement = Arrangement.spacedBy(Tc.Gap)) {
                bar()
                // Half window: only the most important "now" message.
                notices.firstOrNull()?.invoke(Modifier)
                hero(Modifier.weight(1f))
                nav()
            }
        } else {
            Row(Modifier.fillMaxSize().padding(Tc.Gutter), horizontalArrangement = Arrangement.spacedBy(Tc.Gutter)) {
                Column(Modifier.weight(1.2f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Tc.Gap)) {
                    bar()
                    hero(Modifier.weight(1f))
                    nav()
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Tc.Gap)) {
                    Spacer(Modifier.height(Tc.Touch - Tc.Gap))
                    notices.take(2).forEach { it(Modifier) }
                    if (notices.isEmpty()) {
                        Notice(Icons.Rounded.Route, "오늘 길", "길 안내를 시작하면 이 길에 묻힌 캡슐을 알려 드려요")
                    }
                    MyCapsules(capsules, { locks[it.id].isLocked(today) }, today, restricted, Modifier.weight(1f, fill = false)) { drive.playCapsule(it) }
                }
            }
        }
    }
}

@Composable
private fun TopBar(onPleos: Boolean, restricted: Boolean, onDev: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = Tc.Touch), verticalAlignment = Alignment.CenterVertically) {
        Text("타임캡슐", style = Type.TitleL, color = Tc.Text)
        Spacer(Modifier.width(20.dp))
        val (dot, label) = when {
            restricted -> Tc.Info to "주행 중"
            onPleos -> Tc.Accent to "차량 연결됨"
            else -> Tc.TextMute to "가짜 차량"
        }
        Row(
            Modifier.background(Tc.Surface2, RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Dot(dot, 12.dp)
            Text("  $label", style = Type.LabelM, color = Tc.TextSub)
        }
        Spacer(Modifier.weight(1f))
        IconBtn(Icons.Rounded.Tune, "개발 패널", style = BtnStyle.Quiet, onClick = onDev)
    }
}

/** The one main action: a big round voice button, like a voice-assistant key. */
@Composable
private fun VoiceHero(drive: DriveController, compact: Boolean, modifier: Modifier) {
    val car by drive.car.collectAsState()
    val tags = MoodTagEngine.tagsFor(car).filter { it.label != car.placeName }
    Card(modifier, fill = true) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            val size: Dp = if (compact) 200.dp else 240.dp
            // The circle and its label are one button, so tapping the words works too.
            Column(
                Modifier.clip(RoundedCornerShape(32.dp))
                    .clickable(onClickLabel = "목소리 묻기", onClick = clickWithSound { drive.bury() })
                    .semantics(mergeDescendants = true) {}
                    .padding(horizontal = 40.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Surface(
                    modifier = Modifier.size(size),
                    shape = CircleShape,
                    color = Tc.Accent,
                    contentColor = Tc.OnAccent,
                    shadowElevation = if (Tc.P.isDark) 0.dp else 8.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Mic, null, Modifier.size(size * 0.42f)) }
                }
                Text("목소리 묻기", style = Type.TitleL, color = Tc.Text)
            }
            Text(
                "지금 · ${car.placeName ?: "위치 확인 중"}",
                style = Type.BodyM, color = Tc.TextSub, textAlign = TextAlign.Center,
            )
            TagRow(tags, center = true)
            Meta("누르면 바로 녹음돼요 · 최대 30초", align = TextAlign.Center)
        }
    }
}

@Composable
private fun NavRow(restricted: Boolean, go: (Screen) -> Unit, onLocked: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        val tile: @Composable (ImageVector, String, Screen) -> Unit = { icon, label, screen ->
            NavTile(icon, label, restricted, Modifier.weight(1f)) { if (restricted) onLocked() else go(screen) }
        }
        tile(Icons.Rounded.MailOutline, "편지 캡슐", Screen.LETTER)
        tile(Icons.Rounded.PhotoLibrary, "여행 앨범", Screen.ALBUM)
        tile(Icons.Rounded.Settings, "설정", Screen.SETTINGS)
    }
}

/** Big navigation tile. When driving it stays tappable but shows a lock and says why on tap. */
@Composable
private fun NavTile(icon: ImageVector, label: String, locked: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = clickWithSound(onClick),
        modifier = modifier.heightIn(min = 128.dp),
        shape = RoundedCornerShape(Tc.Radius),
        color = Tc.Surface,
        border = if (!Tc.P.isDark) BorderStroke(1.dp, Tc.Line) else null,
    ) {
        val fg = if (locked) Tc.TextMute else Tc.Text
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        ) {
            Box {
                Icon(icon, null, tint = fg, modifier = Modifier.size(40.dp))
                if (locked) {
                    Icon(
                        Icons.Rounded.Lock, "잠김", tint = Tc.Text,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(start = 28.dp, top = 28.dp).size(22.dp)
                            .background(Tc.Surface, CircleShape).padding(2.dp),
                    )
                }
            }
            Text(label, style = Type.BodyStrong, color = fg, maxLines = 1)
        }
    }
}

@Composable
private fun ForecastNotice(f: RouteForecast, here: GeoPoint?, today: LocalDate, modifier: Modifier) {
    val oldest = f.open.firstOrNull()
    val desc = oldest?.let { o ->
        val dist = here?.let { Geo.distanceM(it, GeoPoint(o.lat, o.lng)) }
        "가장 오래된 건 ${Narration.elapsed(o.date(), today)} ${o.placeName ?: ""}" + (dist?.let { " · ${formatDistance(it)} 남음" } ?: "")
    }
    Notice(Icons.Rounded.Route, f.message() ?: "오늘 길에 캡슐이 있어요", desc, modifier)
}

@Composable
private fun MyCapsules(capsules: List<Capsule>, locked: (Capsule) -> Boolean, today: LocalDate, driving: Boolean, modifier: Modifier, onPlay: (Long) -> Unit) {
    Card(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("내 캡슐 ", style = Type.TitleS, color = Tc.Text, modifier = Modifier.padding(bottom = 6.dp))
            Text("${capsules.size}", style = Type.TitleL, color = Tc.AccentInk)
            Text("개", style = Type.TitleS, color = Tc.Text, modifier = Modifier.padding(bottom = 6.dp))
        }
        if (capsules.isEmpty()) {
            Body("목소리를 묻으면 여기에 쌓여요")
            return@Card
        }
        if (driving) {
            // No browsing while driving (spec 07); capsules come to you through approach alerts.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, null, tint = Tc.TextMute, modifier = Modifier.size(28.dp))
                Text("  목록은 차를 세우면 보여요", style = Type.BodyM, color = Tc.TextSub)
            }
            return@Card
        }
        Meta("최근에 묻은 캡슐")
        capsules.filterNot(locked).sortedByDescending { it.createdAt }.take(3).forEach { c ->
            Surface(
                onClick = clickWithSound { onPlay(c.id) },
                shape = RoundedCornerShape(20.dp),
                color = Tc.Bg,
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(56.dp).background(Tc.Surface2, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.PlayArrow, "듣기", tint = Tc.Text, modifier = Modifier.size(32.dp))
                    }
                    Column(Modifier.padding(start = 16.dp).weight(1f)) {
                        Text(c.placeName ?: "장소 기록 없음", style = Type.BodyStrong, color = Tc.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${c.date().format(dayFmt)} · ${Narration.elapsed(c.date(), today)}", style = Type.LabelM, color = Tc.TextMute, maxLines = 1)
                    }
                }
            }
        }
    }
}

// ---- Letter capsule (screen 02, parked only) --------------------------------------------

private enum class LetterPick { YEAR1, YEAR10, DAY100, CUSTOM }

@Composable
fun LetterScreen(drive: DriveController, back: () -> Unit) {
    val today = LocalDate.now()
    var pick by remember { mutableStateOf(LetterPick.YEAR1) }
    var custom by remember { mutableStateOf(today.plusMonths(6)) }
    val openOn = when (pick) {
        LetterPick.YEAR1 -> today.plusYears(1)
        LetterPick.YEAR10 -> today.plusYears(10)
        LetterPick.DAY100 -> today.plusDays(99)
        LetterPick.CUSTOM -> custom
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Tc.Bg)) {
        val compact = widthClass(maxWidth) == WidthClass.Compact
        Column(Modifier.fillMaxSize().padding(Tc.Gutter), verticalArrangement = Arrangement.spacedBy(Tc.Gap)) {
            Header("편지 캡슐", back)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Title("언제 열어 볼까요?")
                Body("정한 날 전에는 이 길을 지나도 열리지 않아요")
            }
            val choices = listOf(
                Triple(LetterPick.YEAR1, "1년 뒤", today.plusYears(1).format(dayFmt)),
                Triple(LetterPick.YEAR10, "10년 뒤", today.plusYears(10).format(dayFmt)),
                Triple(LetterPick.DAY100, "100일 되는 날", today.plusDays(99).format(dayFmt)),
                Triple(LetterPick.CUSTOM, "직접 고르기", if (pick == LetterPick.CUSTOM) custom.format(dayFmt) else "달력에서"),
            )
            val rows = if (compact) choices.chunked(2) else listOf(choices)
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { (p, label, sub) ->
                        Choice(label, sub, pick == p, if (p == LetterPick.CUSTOM) Icons.Rounded.CalendarMonth else null, Modifier.weight(1f)) { pick = p }
                    }
                }
            }
            if (pick == LetterPick.CUSTOM) {
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconBtn(Icons.Rounded.Remove, "한 달 앞으로") { if (custom.minusMonths(1).isAfter(today)) custom = custom.minusMonths(1) }
                        Text(custom.format(dayFmt), style = Type.TitleM, color = Tc.Text, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                        IconBtn(Icons.Rounded.Add, "한 달 뒤로") { custom = custom.plusMonths(1) }
                        if (!compact) Btn("1년", icon = Icons.Rounded.Add) { custom = custom.plusYears(1) }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, null, tint = Tc.AccentInk, modifier = Modifier.size(28.dp))
                Text("  ${openOn.format(dayFmt)}에 열려요", style = Type.BodyStrong, color = Tc.Text)
            }
            Btn("녹음하고 묻기", Modifier.fillMaxWidth(), icon = Icons.Rounded.Mic, style = BtnStyle.Primary, height = Tc.TouchLarge) {
                drive.bury(openOn)
                back()
            }
        }
    }
}

/** Selectable option card. Selection is shown by border + check color, not color alone. */
@Composable
private fun Choice(label: String, sub: String, selected: Boolean, icon: ImageVector?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = clickWithSound(onClick),
        modifier = modifier.heightIn(min = 120.dp),
        shape = RoundedCornerShape(Tc.Radius),
        color = if (selected) Tc.AccentSoft else Tc.Surface,
        border = BorderStroke(if (selected) 3.dp else 1.dp, if (selected) Tc.Accent else Tc.Line),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) Icon(icon, null, tint = Tc.Text, modifier = Modifier.size(30.dp).padding(end = 6.dp))
                Text(label, style = Type.TitleS, color = Tc.Text, maxLines = 1)
            }
            Text(sub, style = Type.LabelM, color = if (selected) Tc.AccentInk else Tc.TextMute, maxLines = 1)
        }
    }
}

// ---- Album (screen 03, parked only) -----------------------------------------------------

@Composable
fun AlbumScreen(drive: DriveController, back: () -> Unit) {
    val capsules by drive.capsules.collectAsState()
    val locks by drive.locks.collectAsState()
    val trips by drive.trips.collectAsState()
    val today = LocalDate.now()
    val tripById = trips.associateBy { it.id }
    var deleting by remember { mutableStateOf<Capsule?>(null) }
    // F-08: group by trip; capsules outside a trip are grouped by day.
    val groups = capsules.groupBy { c -> c.tripId?.let { "trip:$it" } ?: "day:${c.date()}" }
        .entries.sortedByDescending { e -> e.value.maxOf { it.createdAt } }

    BoxWithConstraints(Modifier.fillMaxSize().background(Tc.Bg)) {
        val compact = widthClass(maxWidth) == WidthClass.Compact
        Column(Modifier.fillMaxSize().padding(Tc.Gutter), verticalArrangement = Arrangement.spacedBy(Tc.Gap)) {
            Header("여행 앨범", back) { if (capsules.isNotEmpty()) Meta("캡슐 ${capsules.size}개") }
            if (capsules.isEmpty()) {
                EmptyState(back)
                return@Column
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Tc.Gap)) {
                items(groups, key = { it.key }) { (key, list) ->
                    val trip = if (key.startsWith("trip:")) tripById[key.removePrefix("trip:").toLong()] else null
                    Card(pad = 28.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(groupTitle(trip, list), style = Type.TitleS, color = Tc.Text)
                                Meta("${Narration.elapsed(list.first().date(), today)} · 캡슐 ${list.size}개" + (trip?.let { tripSummary(it) } ?: ""))
                            }
                            Btn(if (compact) "다시 달리기" else "이 길 다시 달리기", icon = Icons.Rounded.Route) { drive.driveMemoryRoute(list) }
                        }
                        list.sortedBy { it.createdAt }.forEach { c ->
                            CapsuleRow(c, locks[c.id]?.openAfter?.takeIf { locks[c.id].isLocked(today) }, compact, { drive.playCapsule(c.id) }) { deleting = c }
                        }
                    }
                }
            }
        }
    }
    deleting?.let { c ->
        ConfirmPopup(
            "이 캡슐을 지울까요?",
            "${c.date().format(dayFmt)} ${c.placeName ?: ""}에서 남긴 목소리가 지워지고 되돌릴 수 없어요.",
            "지우기",
            onConfirm = { drive.delete(c); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun ColumnScope.EmptyState(back: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        IconBadge(Icons.Rounded.Inventory2, tint = Tc.TextSub, bg = Tc.Surface2, size = 112.dp)
        Title("아직 묻은 캡슐이 없어요", align = TextAlign.Center)
        Body("홈에서 목소리 묻기를 누르면 첫 캡슐이 생겨요", align = TextAlign.Center)
        Btn("홈으로", icon = Icons.Rounded.Mic, style = BtnStyle.Primary, onClick = back)
    }
}

/** One capsule: the whole row plays it (big target); delete is a separate, confirmed action. */
@Composable
private fun CapsuleRow(c: Capsule, lockedUntil: Long?, compact: Boolean, onPlay: () -> Unit, onDelete: () -> Unit) {
    val locked = lockedUntil != null
    Surface(
        onClick = clickWithSound(onPlay),
        enabled = !locked,
        shape = RoundedCornerShape(20.dp),
        color = Tc.Bg,
        modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(
                Modifier.size(72.dp).background(if (locked) Tc.Surface2 else Tc.Accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (locked) Icons.Rounded.Lock else Icons.Rounded.PlayArrow,
                    contentDescription = if (locked) "잠긴 편지" else "듣기",
                    tint = if (locked) Tc.TextSub else Tc.OnAccent,
                    modifier = Modifier.size(40.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "${c.placeName ?: "장소 기록 없음"} · ${c.durationSec}초" + (if (c.locationUncertain) " · 위치 불확실" else ""),
                    style = Type.BodyStrong, color = Tc.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (locked) {
                    Text("${LocalDate.ofEpochDay(lockedUntil!!).format(dayFmt)}에 열리는 편지", style = Type.LabelM, color = Tc.AccentInk)
                } else {
                    val tags = MoodTagEngine.fromStored(c.tags).filter { it.label != c.placeName }
                    Text(
                        Instant.ofEpochMilli(c.createdAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("a h:mm")) +
                            (if (tags.isNotEmpty()) " · " + tags.joinToString(" · ") { it.label } else ""),
                        style = Type.LabelM, color = Tc.TextMute, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconBtn(Icons.Rounded.DeleteOutline, "삭제", style = BtnStyle.Quiet, onClick = onDelete)
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

// ---- Settings (F-13, parked only) -------------------------------------------------------

@Composable
private fun SettingRow(title: String, sub: String, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = Tc.Touch), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = Type.BodyStrong, color = Tc.Text)
            Text(sub, style = Type.LabelM, color = Tc.TextMute)
        }
        Spacer(Modifier.width(24.dp))
        trailing()
    }
}

@Composable
private fun ValueText(text: String) = Text(text, style = Type.BodyM, color = Tc.TextSub)

@Composable
fun SettingsScreen(app: TimeCapsuleApp, back: () -> Unit) {
    val arrival by app.settings.arrivalPrompt.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Tc.Bg).padding(Tc.Gutter), verticalArrangement = Arrangement.spacedBy(Tc.Gap)) {
        Header("설정", back)
        Column(
            Modifier.widthIn(max = 1100.dp).fillMaxWidth().align(Alignment.CenterHorizontally).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Eyebrow("개인정보")
            Card {
                SettingRow("녹음은 직접 누를 때만", "마이크는 버튼을 눌렀을 때만 켜져요") { ValueText("항상") }
                Divider()
                SettingRow("녹음 알림음", "함께 탄 사람도 알 수 있게 끌 수 없어요") { ValueText("켜짐") }
                Divider()
                SettingRow("저장 위치", "캡슐은 이 차 안에만 저장돼요") { ValueText("이 차") }
            }
            Spacer(Modifier.height(8.dp))
            Eyebrow("알림")
            Card {
                SettingRow("도착 제안", "처음 온 곳에 도착하면 물어봐요 · 하루 2번까지") { TcSwitch(arrival, app.settings::setArrivalPrompt) }
            }
            Spacer(Modifier.height(8.dp))
            Eyebrow("차를 넘길 때")
            Card {
                SettingRow("모두 지우기", "모든 캡슐과 녹음을 지워요") {
                    Btn("지우기", icon = Icons.Rounded.DeleteOutline) { confirm = true }
                }
            }
        }
    }
    if (confirm) {
        ConfirmPopup(
            "모든 캡슐을 지울까요?",
            "녹음까지 모두 지워지고 되돌릴 수 없어요.",
            "모두 지우기",
            onConfirm = { confirm = false; app.drive.deleteEverything(app) },
            onDismiss = { confirm = false },
        )
    }
}

// ---- Dev panel (demo only) -------------------------------------------------------------

@Composable
private fun Toggle(label: String, on: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = clickWithSound(onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (on) Tc.AccentSoft else Tc.Surface2,
        border = BorderStroke(2.dp, if (on) Tc.Accent else Tc.Surface2),
        modifier = Modifier.heightIn(min = 64.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text(label, style = Type.LabelM, color = Tc.Text)
        }
    }
}

/** Dev panel for demos: fake car state, safety signals, day/night, approach and route. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DevPanel(app: TimeCapsuleApp, close: () -> Unit) {
    val capsules by app.drive.capsules.collectAsState()
    val signals by app.drive.signals.collectAsState()
    val night by app.nightOverride.collectAsState()
    val fakeCar = app.vehicle as? FakeVehicleSource
    val fakeNavi = app.navi as? FakeNaviSource
    Box(Modifier.fillMaxSize().background(Tc.Scrim), contentAlignment = Alignment.CenterEnd) {
        Surface(Modifier.fillMaxHeight().widthIn(max = 840.dp).fillMaxWidth(), color = Tc.Surface) {
            Column(
                Modifier.padding(Tc.Gutter).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Header("개발 패널") { Btn("닫기", style = BtnStyle.Quiet, onClick = close) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("가짜 차량·내비 모드", style = Type.BodyStrong, color = Tc.Text)
                        Meta("앱을 다시 켜면 적용돼요")
                    }
                    var fake by remember { mutableStateOf(app.forceFake) }
                    TcSwitch(fake) { fake = it; app.setForceFake(it) }
                }
                Eyebrow("화면")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Toggle("차량 따라", night == null) { app.nightOverride.value = null }
                    Toggle("낮", night == false) { app.nightOverride.value = false }
                    Toggle("밤", night == true) { app.nightOverride.value = true }
                    val half by app.halfPreview.collectAsState()
                    Toggle("반쪽 화면(내비 옆)", half) { app.halfPreview.value = !half }
                }
                if (fakeCar != null) {
                    val s by fakeCar.snapshot.collectAsState()
                    Eyebrow("차량 상태")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Toggle("P단", s.gearInPark == true) { fakeCar.set { it.copy(gearInPark = it.gearInPark != true) } }
                        Toggle("문 열림", s.anyDoorOpen == true) { fakeCar.set { it.copy(anyDoorOpen = it.anyDoorOpen != true) } }
                        Toggle("전조등", s.headlightOn == true) { fakeCar.set { it.copy(headlightOn = it.headlightOn != true) } }
                        Toggle("와이퍼", s.wiperOn == true) { fakeCar.set { it.copy(wiperOn = it.wiperOn != true) } }
                        Toggle("창문", s.anyWindowOpen == true) { fakeCar.set { it.copy(anyWindowOpen = it.anyWindowOpen != true) } }
                        Toggle("고속도로", s.onHighway == true) { fakeCar.set { it.copy(onHighway = it.onHighway != true) } }
                        Toggle("탑승 ${s.occupiedSeats}명", false) { fakeCar.set { it.copy(occupiedSeats = ((it.occupiedSeats ?: 1) % 4) + 1) } }
                        Toggle("${s.outsideTempC?.toInt()}℃", false) { fakeCar.set { it.copy(outsideTempC = if ((it.outsideTempC ?: 18f) > 5f) 2f else 18f) } }
                    }
                    Eyebrow("안전 신호")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val newest = capsules.maxByOrNull { it.createdAt }
                    Btn("최근 캡슐을 18년 전으로", enabled = newest != null, height = 72.dp) { newest?.let { app.drive.backdate(it.id, 18) } }
                    Btn("1.2km 접근", enabled = newest != null, height = 72.dp) { newest?.let { app.drive.simulateApproach(it.id); close() } }
                    if (fakeNavi != null) {
                        Btn("캡슐 지나는 경로 시작", enabled = capsules.isNotEmpty(), height = 72.dp) {
                            val c = capsules.first()
                            fakeNavi.moveTo(NaviPosition(GeoPoint(c.lat - 0.02, c.lng), "강원특별자치도 강릉시 7번 국도"))
                            fakeNavi.drive(NamedPoint("경포해변", GeoPoint(c.lat + 0.02, c.lng)), emptyList())
                        }
                        Btn("도착", height = 72.dp) {
                            fakeNavi.arrive()
                            fakeNavi.moveTo(NaviPosition(GeoPoint(37.80, 128.95), "강원특별자치도 강릉시 경포로"))
                        }
                    }
                }
            }
        }
    }
}
