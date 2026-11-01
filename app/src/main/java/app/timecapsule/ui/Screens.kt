package app.timecapsule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.MoreHoriz
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.timecapsule.TimeCapsuleApp
import app.timecapsule.capsule.RouteForecast
import app.timecapsule.capsule.isLocked
import app.timecapsule.data.Capsule
import app.timecapsule.data.Trip
import app.timecapsule.social.FakeBackend
import app.timecapsule.social.Friend
import app.timecapsule.social.FriendCode
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

enum class Screen { HOME, LETTER, ALBUM, FRIENDS, SETTINGS }

internal fun Capsule.date(): LocalDate = Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
internal val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일")
private val shortFmt = DateTimeFormatter.ofPattern("yyyy. M. d.")

// ---- Home (screen 01) ---------------------------------------------------------------------

/**
 * Home. Portrait (one window next to navigation) is the main layout:
 *   header · main action bar · "now" notice · recent capsules · menu bar.
 * Landscape keeps that column on the left and adds the full capsule list on the right.
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
    val capsules by drive.capsules.collectAsState()
    val locks by drive.locks.collectAsState()
    val today = LocalDate.now()
    val playable = capsules.filterNot { locks[it.id].isLocked(today) }.sortedByDescending { it.createdAt }

    BoxWithConstraints(Modifier.fillMaxSize().background(Tc.Bg)) {
        val portrait = widthClass(maxWidth) == WidthClass.Portrait
        if (portrait) {
            Column(Modifier.fillMaxSize().padding(Tc.Gutter), verticalArrangement = Arrangement.spacedBy(Tc.Gap)) {
                AppBar(onPleos, restricted, onDev)
                VoiceBar(drive)
                NowNotice(drive, restricted, today)
                // The list takes what is left (only whole rows); the menu stays pinned to the bottom.
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val fits = ((maxHeight - 72.dp) / (Tc.Row + 1.dp)).toInt().coerceIn(1, 4)
                    CapsuleList(
                        playable.take(fits), capsules.size, restricted, today, Modifier,
                        onMore = { if (restricted) onLocked() else go(Screen.ALBUM) },
                    ) { drive.playCapsule(it) }
                }
                MenuBar(restricted, go, onLocked)
            }
        } else {
            Row(Modifier.fillMaxSize().padding(Tc.Gutter), horizontalArrangement = Arrangement.spacedBy(Tc.Gutter)) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Tc.Gap)) {
                    AppBar(onPleos, restricted, onDev)
                    VoiceBar(drive)
                    NowNotice(drive, restricted, today)
                    Spacer(Modifier.weight(1f))
                    MenuBar(restricted, go, onLocked)
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Tc.Gap)) {
                    Spacer(Modifier.height(Tc.Touch - Tc.Gap))
                    CapsuleList(
                        playable.take(6), capsules.size, restricted, today, Modifier,
                        onMore = { if (restricted) onLocked() else go(Screen.ALBUM) },
                    ) { drive.playCapsule(it) }
                }
            }
        }
    }
}

/**
 * Pleos Page Navigation (main): the title and one "more" action. No logo, no status label:
 * the system status bar already shows whether the car is driving.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
private fun AppBar(onPleos: Boolean, restricted: Boolean, onDev: () -> Unit) {
    Header("타임캡슐") { IconBtn(Icons.Rounded.MoreHoriz, "개발 패널", onClick = onDev) }
}

/**
 * The one main action, shaped like navigation's "어디로 갈까요?" bar: a solid dark bar with a
 * mic, the verb, and what will be saved with it (place and mood) on the second line.
 * Plain icon, no colored badge.
 */
@Composable
private fun VoiceBar(drive: DriveController) {
    val car by drive.car.collectAsState()
    val audience by drive.defaultAudience.collectAsState()
    val tags = MoodTagEngine.tagsFor(car).filter { it.label != car.placeName }.map { it.label }
    val p = Tc.P
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Surface(
        onClick = clickWithSound { drive.bury() },
        modifier = Modifier.fillMaxWidth().heightIn(min = 128.dp),
        shape = RoundedCornerShape(Tc.PanelRadius),
        color = if (pressed) p.filled else p.strong,
        contentColor = p.onStrong,
        interactionSource = source,
    ) {
        Row(Modifier.padding(horizontal = 32.dp, vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Mic, null, tint = p.onStrong, modifier = Modifier.size(44.dp))
            Column(Modifier.padding(start = 24.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("목소리 남기기", style = Type.TitleM)
                // Who hears it comes first: that is the point of leaving one.
                Text(
                    (listOf(if (audience == "SELF") "나만" else "친구 모두에게", car.placeName ?: "위치 확인 중") + tags).joinToString(" · "),
                    style = Type.LabelM, color = p.onStrongSub, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The single most relevant "now" message, as a plain row (or a question with two buttons). */
@Composable
private fun NowNotice(drive: DriveController, restricted: Boolean, today: LocalDate) {
    val forecast by drive.forecast.collectAsState()
    val reprompt by drive.reprompt.collectAsState()
    val arrival by drive.arrivalPrompt.collectAsState()
    val position by drive.position.collectAsState()
    val again = reprompt
    val f = forecast
    when {
        arrival -> InlinePrompt(
            "도착했어요. 한마디 남길까요?", "이곳엔 아직 캡슐이 없어요",
            cta = "남기기" to { drive.answerArrival(true) }, ctaIcon = Icons.Rounded.Mic,
            sub = "괜찮아요" to { drive.answerArrival(false) },
        )
        again != null && !restricted -> InlinePrompt(
            again.ownerName?.let { "아까 지나친 ${it}님 캡슐이 있어요" } ?: "아까 지나친 캡슐이 있어요",
            "${Narration.elapsed(again.date(), today)} · ${again.placeName ?: "이 길"}",
            cta = "들려줘" to { drive.playCapsule(again.id) }, ctaIcon = Icons.Rounded.PlayArrow,
            sub = "닫기" to { drive.dismissReprompt() },
        )
        f != null && f.total > 0 -> ForecastRow(f, position?.point, today)
        else -> ListRow("오늘 길", "길 안내를 시작하면 이 길의 캡슐을 알려 드려요", leading = Icons.Rounded.Route, leadingTint = Tc.TextMute)
    }
    Divider()
}

@Composable
private fun ForecastRow(f: RouteForecast, here: GeoPoint?, today: LocalDate) {
    val oldest = f.open.firstOrNull()
    val desc = oldest?.let { o ->
        val dist = here?.let { Geo.distanceM(it, GeoPoint(o.lat, o.lng)) }
        "가장 오래된 건 ${Narration.elapsed(o.date(), today)} ${o.placeName ?: ""}" + (dist?.let { " · ${formatDistance(it)} 남음" } ?: "")
    }
    ListRow(f.message() ?: "오늘 길에 캡슐이 있어요", desc, leading = Icons.Rounded.Route)
}

/** Recent capsules as a list panel. Hidden while driving: capsules come to you as alerts. */
@Composable
private fun CapsuleList(
    recent: List<Capsule>,
    total: Int,
    driving: Boolean,
    today: LocalDate,
    modifier: Modifier,
    onMore: () -> Unit,
    onPlay: (Long) -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Section("캡슐 $total") {
            if (total > 0 && !driving) {
                Btn("전체", icon = null, style = BtnStyle.Quiet, height = 56.dp, onClick = onMore)
            }
        }
        Panel {
            when {
                total == 0 -> ListRow("아직 묻은 캡슐이 없어요", "위의 버튼을 누르고 말하면 첫 캡슐이 생겨요", leading = Icons.Rounded.Mic, leadingTint = Tc.TextMute)
                driving -> ListRow("목록은 차를 세우면 보여요", "운전 중에는 캡슐이 알림으로 찾아와요", leading = Icons.Rounded.Lock, leadingTint = Tc.TextMute)
                else -> recent.forEachIndexed { i, c ->
                    if (i > 0) RowDivider()
                    ListRow(
                        (c.ownerName?.let { "${it}님 · " } ?: "") + (c.placeName ?: "장소 기록 없음"),
                        "${c.date().format(shortFmt)} · ${Narration.elapsed(c.date(), today)}",
                        leading = Icons.Rounded.PlayArrow,
                        onClick = { onPlay(c.id) },
                    )
                }
            }
        }
    }
}

/**
 * Bottom menu, like navigation's icon row: a line above, four items split by dividers.
 * While driving the items stay tappable, show a lock and explain why.
 */
@Composable
private fun MenuBar(restricted: Boolean, go: (Screen) -> Unit, onLocked: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Divider()
        Row(Modifier.fillMaxWidth().height(120.dp), verticalAlignment = Alignment.CenterVertically) {
            val item: @Composable (ImageVector, String, Screen) -> Unit = { icon, label, screen ->
                MenuItem(icon, label, restricted, Modifier.weight(1f)) { if (restricted) onLocked() else go(screen) }
            }
            item(Icons.Rounded.MailOutline, "편지", Screen.LETTER)
            Box(Modifier.width(1.dp).height(56.dp).background(Tc.Line))
            item(Icons.Rounded.PhotoLibrary, "앨범", Screen.ALBUM)
            Box(Modifier.width(1.dp).height(56.dp).background(Tc.Line))
            item(Icons.Rounded.Group, "친구", Screen.FRIENDS)
            Box(Modifier.width(1.dp).height(56.dp).background(Tc.Line))
            item(Icons.Rounded.Settings, "설정", Screen.SETTINGS)
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, locked: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Surface(
        onClick = clickWithSound(onClick),
        modifier = modifier.fillMaxHeight(),
        color = if (pressed) Tc.Basic else Color.Transparent,
        interactionSource = source,
    ) {
        val fg = if (locked) Tc.TextMute else Tc.Text
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
            Icon(if (locked) Icons.Rounded.Lock else icon, contentDescription = if (locked) "잠김" else null, tint = fg, modifier = Modifier.size(40.dp))
            Text(label, style = Type.LabelStrong, color = fg, maxLines = 1)
        }
    }
}

// ---- Letter capsule (screen 02, parked only) --------------------------------------------

private enum class LetterPick { YEAR1, YEAR10, DAY100, CUSTOM }

/**
 * 기획서 F-06: a letter for someone, opening on a set day. Two radio lists (Pleos radio
 * component): who it is for, then when. One confirming action at the bottom.
 */
@Composable
fun LetterScreen(app: TimeCapsuleApp, back: () -> Unit) {
    val drive = app.drive
    val friends by app.social.friends.collectAsState()
    val me by app.social.me.collectAsState()
    var to by remember { mutableStateOf<Friend?>(null) }
    val today = LocalDate.now()
    var pick by remember { mutableStateOf(LetterPick.YEAR1) }
    var custom by remember { mutableStateOf(today.plusMonths(6)) }
    val openOn = when (pick) {
        LetterPick.YEAR1 -> today.plusYears(1)
        LetterPick.YEAR10 -> today.plusYears(10)
        LetterPick.DAY100 -> today.plusDays(99)
        LetterPick.CUSTOM -> custom
    }
    Column(Modifier.fillMaxSize().background(Tc.Bg).padding(Tc.Gutter), verticalArrangement = Arrangement.spacedBy(Tc.Gap)) {
        Header("편지 캡슐", back)
        Column(
            Modifier.weight(1f).widthIn(max = 960.dp).fillMaxWidth().align(Alignment.CenterHorizontally).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("누구에게 남길까요?", style = Type.TitleS, color = Tc.Text, modifier = Modifier.padding(start = Tc.Inset))
            Text("받는 사람이 이 길을 지날 때만 열려요. 다른 사람에게는 알리지 않아요", style = Type.BodyS, color = Tc.TextMute, modifier = Modifier.padding(start = Tc.Inset, bottom = 8.dp))
            Panel {
                val radio: @Composable (Boolean, String, String, () -> Unit) -> Unit = { on, label, sub, click ->
                    ListRow(
                        label, sub,
                        leading = if (on) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                        leadingTint = if (on) Tc.Info else Tc.TextMute,
                        onClick = click,
                    )
                }
                radio(to == null, "나", "이 차에서 내가 열어요") { to = null }
                friends.forEach { f ->
                    RowDivider()
                    radio(to?.uid == f.uid, "${f.name}님", "${f.name}님 차가 이 길을 지나면 열려요") { to = f }
                }
                if (friends.isEmpty()) {
                    RowDivider()
                    ListRow(
                        if (me == null) "친구에게 보내려면 친구를 추가하세요" else "아직 친구가 없어요",
                        "친구 화면에서 코드나 QR로 추가해요",
                        leading = Icons.Rounded.Group, leadingTint = Tc.TextMute,
                        onClick = { app.openScreen.value = Screen.FRIENDS },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("언제 열어 볼까요?", style = Type.TitleS, color = Tc.Text, modifier = Modifier.padding(start = Tc.Inset))
            Text("정한 날 전에는 이 길을 지나도 열리지 않아요", style = Type.BodyS, color = Tc.TextMute, modifier = Modifier.padding(start = Tc.Inset, bottom = 8.dp))
            Panel {
                val opt: @Composable (LetterPick, String, String) -> Unit = { p, label, sub ->
                    ListRow(
                        label, sub,
                        leading = if (pick == p) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                        leadingTint = if (pick == p) Tc.Info else Tc.TextMute,
                        onClick = { pick = p },
                    )
                }
                opt(LetterPick.YEAR1, "1년 뒤", today.plusYears(1).format(dayFmt))
                RowDivider()
                opt(LetterPick.YEAR10, "10년 뒤", today.plusYears(10).format(dayFmt))
                RowDivider()
                opt(LetterPick.DAY100, "100일 되는 날", today.plusDays(99).format(dayFmt))
                RowDivider()
                opt(LetterPick.CUSTOM, "직접 고르기", if (pick == LetterPick.CUSTOM) custom.format(dayFmt) else "한 달 단위로 정해요")
                if (pick == LetterPick.CUSTOM) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 92.dp, end = 28.dp, bottom = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        IconBtn(Icons.Rounded.Remove, "한 달 앞으로", style = BtnStyle.Basic) { if (custom.minusMonths(1).isAfter(today)) custom = custom.minusMonths(1) }
                        Text(custom.format(dayFmt), style = Type.BodyStrong, color = Tc.Text, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                        IconBtn(Icons.Rounded.Add, "한 달 뒤로", style = BtnStyle.Basic) { custom = custom.plusMonths(1) }
                    }
                }
            }
        }
        Column(Modifier.widthIn(max = 960.dp).fillMaxWidth().align(Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
                Icon(Icons.Rounded.Lock, null, tint = Tc.TextMute, modifier = Modifier.size(28.dp))
                Text("  ${to?.let { "${it.name}님에게 · " } ?: ""}${openOn.format(dayFmt)}에 열려요", style = Type.BodyS, color = Tc.TextSub)
            }
            Btn("녹음하고 묻기", Modifier.fillMaxWidth(), icon = Icons.Rounded.Mic, style = BtnStyle.Filled, height = Tc.TouchLarge) {
                val f = to
                if (f == null) drive.bury(openOn, audience = "SELF") else drive.bury(openOn, audience = "SOME", recipients = listOf(f.uid))
                back()
            }
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

    Column(Modifier.fillMaxSize().background(Tc.Bg).padding(Tc.Gutter), verticalArrangement = Arrangement.spacedBy(Tc.Gap)) {
        Header("여행 앨범", back) { if (capsules.isNotEmpty()) Text("캡슐 ${capsules.size}개", style = Type.LabelM, color = Tc.TextMute) }
        if (capsules.isEmpty()) {
            EmptyState(back)
            return@Column
        }
        LazyColumn(
            Modifier.widthIn(max = 1100.dp).fillMaxWidth().align(Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            groups.forEach { (key, list) ->
                val trip = if (key.startsWith("trip:")) tripById[key.removePrefix("trip:").toLong()] else null
                item(key = key) {
                    Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(groupTitle(trip, list), style = Type.BodyStrong, color = Tc.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${Narration.elapsed(list.first().date(), today)} · ${list.size}개" + (trip?.let { tripSummary(it) } ?: ""),
                                style = Type.LabelM, color = Tc.TextMute, maxLines = 1,
                            )
                        }
                        Btn("다시 달리기", icon = Icons.Rounded.Route, height = 72.dp) { drive.driveMemoryRoute(list) }
                    }
                }
                item(key = "$key:rows") {
                    Panel {
                        list.sortedBy { it.createdAt }.forEachIndexed { i, c ->
                            if (i > 0) RowDivider()
                            CapsuleRow(c, locks[c.id]?.openAfter?.takeIf { locks[c.id].isLocked(today) }, { drive.playCapsule(c.id) }) { deleting = c }
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
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(Icons.Rounded.PhotoLibrary, null, tint = Tc.TextMute, modifier = Modifier.size(72.dp))
        Text("아직 묻은 캡슐이 없어요", style = Type.TitleS, color = Tc.Text, textAlign = TextAlign.Center)
        Text("홈에서 목소리 남기기를 누르면 첫 캡슐이 생겨요", style = Type.BodyS, color = Tc.TextMute, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Btn("홈으로", style = BtnStyle.Filled, onClick = back)
    }
}

/** One capsule: the whole row plays it; delete is a separate, confirmed action. */
@Composable
private fun CapsuleRow(c: Capsule, lockedUntil: Long?, onPlay: () -> Unit, onDelete: () -> Unit) {
    val locked = lockedUntil != null
    val time = Instant.ofEpochMilli(c.createdAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("a h:mm"))
    val tags = MoodTagEngine.fromStored(c.tags).filter { it.label != c.placeName }.joinToString(" · ") { it.label }
    ListRow(
        title = "${c.placeName ?: "장소 기록 없음"} · ${c.durationSec}초",
        sub = if (locked) "${LocalDate.ofEpochDay(lockedUntil!!).format(dayFmt)}에 열리는 편지"
        else listOf(whoOf(c), time, tags, if (c.locationUncertain) "위치 불확실" else "").filter { it.isNotEmpty() }.joinToString(" · "),
        leading = if (locked) Icons.Rounded.Lock else Icons.Rounded.PlayArrow,
        leadingTint = if (locked) Tc.TextMute else Tc.Text,
        enabled = !locked,
        onClick = onPlay,
    ) {
        IconBtn(Icons.Rounded.DeleteOutline, "삭제", onClick = onDelete)
    }
}

/** Who left it (a friend), or who hears it (mine). */
private fun whoOf(c: Capsule): String = when {
    c.ownerName != null -> "${c.ownerName}님이 남김"
    c.audience == "SELF" -> "나만"
    c.audience == "SOME" -> "받는 사람 지정"
    else -> "친구 모두"
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
private fun ValueText(text: String) = Text(text, style = Type.BodyS, color = Tc.TextMute)

@Composable
fun SettingsScreen(app: TimeCapsuleApp, back: () -> Unit) {
    val arrival by app.settings.arrivalPrompt.collectAsState()
    val audience by app.settings.defaultAudience.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Tc.Bg).padding(Tc.Gutter), verticalArrangement = Arrangement.spacedBy(Tc.Gap)) {
        Header("설정", back)
        Column(
            Modifier.widthIn(max = 1100.dp).fillMaxWidth().align(Alignment.CenterHorizontally).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section("목소리 남기기를 누르면")
            Panel {
                val radio: @Composable (String, String, String) -> Unit = { value, label, sub ->
                    ListRow(
                        label, sub,
                        leading = if (audience == value) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                        leadingTint = if (audience == value) Tc.Info else Tc.TextMute,
                        onClick = { app.settings.setDefaultAudience(value) },
                    )
                }
                radio("FRIENDS", "친구 모두에게", "친구가 그 길을 지나면 내 목소리가 나와요")
                RowDivider()
                radio("SELF", "나만", "이 차에만 남고 아무에게도 가지 않아요")
            }
            Spacer(Modifier.height(8.dp))
            Section("개인정보")
            Panel {
                ListRow("녹음은 직접 누를 때만", "마이크는 버튼을 눌렀을 때만 켜져요") { ValueText("항상") }
                RowDivider()
                ListRow("녹음 알림음", "함께 탄 사람도 알 수 있게 끌 수 없어요") { ValueText("켜짐") }
                RowDivider()
                ListRow("저장 위치", "친구에게 남긴 캡슐만 서버에 올라가요") { ValueText("이 차 + 서버") }
            }
            Spacer(Modifier.height(8.dp))
            Section("알림")
            Panel {
                ListRow("도착 제안", "처음 온 곳에 도착하면 물어봐요 · 하루 2번까지") { TcSwitch(arrival, app.settings::setArrivalPrompt) }
            }
            Spacer(Modifier.height(8.dp))
            Section("차를 넘길 때")
            Panel {
                ListRow("모두 지우기", "이 차의 캡슐, 녹음, 내 이름을 지워요", onClick = { confirm = true }) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Tc.TextMute, modifier = Modifier.size(36.dp))
                }
            }
        }
    }
    if (confirm) {
        ConfirmPopup(
            "모든 캡슐을 지울까요?",
            "이 차에서 녹음까지 모두 지워지고 되돌릴 수 없어요. 친구에게 이미 간 캡슐은 친구 차에 남아요.",
            "모두 지우기",
            onConfirm = { confirm = false; app.wipeCar() },
            onDismiss = { confirm = false },
        )
    }
}

// ---- Dev panel (demo only) -------------------------------------------------------------

@Composable
private fun Toggle(label: String, on: Boolean, onClick: () -> Unit) =
    Btn(label, style = if (on) BtnStyle.Filled else BtnStyle.Basic, height = 64.dp, onClick = onClick)

/** Dev panel for demos: fake car state, safety signals, day/night, approach and route. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DevPanel(app: TimeCapsuleApp, close: () -> Unit) {
    val capsules by app.drive.capsules.collectAsState()
    val signals by app.drive.signals.collectAsState()
    val night by app.nightOverride.collectAsState()
    val fakeCar = app.vehicle as? FakeVehicleSource
    val fakeNavi = app.navi as? FakeNaviSource
    Box(Modifier.fillMaxSize().background(Color(0x66131417)), contentAlignment = Alignment.CenterEnd) {
        Surface(Modifier.fillMaxHeight().widthIn(max = 840.dp).fillMaxWidth(), color = Tc.Surface) {
            Column(
                Modifier.padding(Tc.Gutter).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Header("개발 패널") { Btn("닫기", style = BtnStyle.Quiet, onClick = close) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("가짜 차량·내비 모드", style = Type.BodyStrong, color = Tc.Text)
                        Text("앱을 다시 켜면 적용돼요", style = Type.LabelM, color = Tc.TextMute)
                    }
                    var fake by remember { mutableStateOf(app.forceFake) }
                    TcSwitch(fake) { fake = it; app.setForceFake(it) }
                }
                Section("화면")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Toggle("차량 따라", night == null) { app.nightOverride.value = null }
                    Toggle("낮", night == false) { app.nightOverride.value = false }
                    Toggle("밤", night == true) { app.nightOverride.value = true }
                }
                if (fakeCar != null) {
                    val s by fakeCar.snapshot.collectAsState()
                    Section("차량 상태")
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
                    Section("안전 신호")
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
                    Text("차량 값은 Virtual Console(127.0.0.1:8088)에서 바꾸세요", style = Type.LabelM, color = Tc.TextMute)
                }
                FriendDev(app, close)
                Section("시연")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val newest = capsules.maxByOrNull { it.createdAt }
                    Btn("최근 캡슐을 18년 전으로", enabled = newest != null, height = 72.dp) { newest?.let { app.drive.backdate(it.id, 18) } }
                    Btn("1.2km 접근", enabled = newest != null, height = 72.dp) { newest?.let { app.drive.simulateApproach(it.id); close() } }
                    Btn("10초 뒤 접근(앱 밖에서 보기)", enabled = newest != null, height = 72.dp) {
                        newest?.let { c -> close(); app.scope.launch { kotlinx.coroutines.delay(10_000); app.drive.simulateApproach(c.id) } }
                    }
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

/**
 * Dev panel, friends: which server, and the one-emulator demo of "a friend left me a capsule
 * on this road". With the fake server, 민수 (MNSU27) and 지영 (JYNG38) exist already.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FriendDev(app: TimeCapsuleApp, close: () -> Unit) {
    val me by app.social.me.collectAsState()
    val friends by app.social.friends.collectAsState()
    val known by app.social.knownAccounts.collectAsState()
    val fake = app.social as? FakeBackend
    val context = androidx.compose.ui.platform.LocalContext.current
    Section("친구 · ${app.social.label}")
    Text(
        me?.let { "지금 이 차: ${it.name} (${spaced(it.code)}) · 친구 ${friends.size}명" } ?: "아직 이름을 정하지 않았어요",
        style = Type.LabelM, color = Tc.TextMute, modifier = Modifier.padding(start = Tc.Inset),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (fake != null) {
            Btn("민수 친구 추가", enabled = me != null && friends.none { it.code == "MNSU27" }, height = 72.dp) {
                app.scope.launch { app.social.addFriend("MNSU27") }
            }
            Btn("QR로 지영 추가", height = 72.dp) {
                context.startActivity(
                    android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(FriendCode.link("JYNG38")))
                        .setPackage(context.packageName),
                )
                close()
            }
            val friend = friends.firstOrNull()
            Btn("${friend?.name ?: "친구"}가 이 길에 캡슐 남김", enabled = friend != null, height = 72.dp) {
                val here = app.navi.position.value
                val at = here?.point?.let { GeoPoint(it.lat + 0.005, it.lng) } ?: GeoPoint(37.7569, 128.8761)
                fake.friendLeavesCapsule(friend!!, at, app.drive.car.value.placeName ?: "7번 국도", daysAgo = 3)
                close()
            }
        }
        known.filter { it.uid != me?.uid }.forEach { p ->
            Btn("${p.name}(으)로 바꾸기", height = 72.dp) { app.switchAccount(p) }
        }
        Btn("다른 사람으로 시작", height = 72.dp) { app.switchAccount(null) }
    }
}
