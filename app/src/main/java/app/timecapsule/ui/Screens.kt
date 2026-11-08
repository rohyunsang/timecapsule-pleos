package app.timecapsule.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Map
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
import app.timecapsule.capsule.AlbumData
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.saveable.rememberSaveable
import app.timecapsule.social.Forwarding
import app.timecapsule.TimeCapsuleApp
import app.timecapsule.capsule.RouteForecast
import app.timecapsule.capsule.Rules
import app.timecapsule.tag.TheDay
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

enum class Screen { HOME, LETTER, ALBUM, FRIENDS, SETTINGS, MAP }

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
    friendCount: Int = 0,
) {
    val capsules by drive.capsules.collectAsState()
    val locks by drive.locks.collectAsState()
    val today = LocalDate.now()
    val position by drive.position.collectAsState()
    var routeTarget by remember { mutableStateOf<Capsule?>(null) }
    val openRecent: (Long) -> Unit = { id ->
        capsules.firstOrNull { it.id == id }?.let { c ->
            if (AlbumData.needsCollection(c) && drive.openingReason(c, position) != null && AlbumData.canNavigate(c)) routeTarget = c
            else drive.playCapsule(id)
        }
    }
    routeTarget?.let { c ->
        ConfirmPopup("이 장소로 안내할까요?", "${c.placeName ?: "캡슐 지점"}을 내비게이션 목적지로 설정해요. 기존 경로가 있다면 바뀝니다.",
            "길 안내 시작", onConfirm = { drive.driveToCapsule(c.id); routeTarget = null }, onDismiss = { routeTarget = null })
    }
    val playable = capsules.filterNot { it.draft || locks[it.id].isLocked(today) }.sortedByDescending { it.createdAt }

    BoxWithConstraints(Modifier.fillMaxSize().background(Tc.Bg)) {
        val portrait = widthClass(maxWidth) == WidthClass.Portrait
        if (portrait) {
            Column(Modifier.fillMaxSize().padding(Tc.Gutter), verticalArrangement = Arrangement.spacedBy(Tc.Gap)) {
                AppBar(onPleos, restricted, onDev)
                VoiceBar(drive, friendCount)
                NowNotice(drive, restricted, today)
                // The list takes what is left (only whole rows); the menu stays pinned to the bottom.
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val fits = ((maxHeight - 72.dp) / (Tc.Row + 1.dp)).toInt().coerceIn(1, 4)
                    CapsuleList(
                        playable.take(fits), capsules.size, restricted, today, Modifier,
                        onMore = { if (restricted) onLocked() else go(Screen.ALBUM) },
                    ) { openRecent(it) }
                }
                MenuBar(restricted, go, onLocked)
            }
        } else {
            Column(Modifier.fillMaxSize().padding(Tc.Gutter), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                AppBar(onPleos, restricted, onDev)
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        Surface(Modifier.weight(1f).fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Tc.Basic) {
                            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center) {
                                Text("지금, 이곳의 목소리", style = Type.LabelStrong, color = Tc.TextSub)
                                Spacer(Modifier.height(24.dp))
                                VoiceBar(drive, friendCount)
                            }
                        }
                        NowNotice(drive, restricted, today)
                        MenuBar(restricted, go, onLocked)
                    }
                    Surface(Modifier.weight(1f).fillMaxHeight(), shape = RoundedCornerShape(24.dp), color = Tc.Surface) {
                        BoxWithConstraints(Modifier.padding(24.dp)) {
                            val fits = ((maxHeight - 100.dp) / (Tc.Row + 1.dp)).toInt().coerceIn(1, 6)
                            CapsuleList(playable.take(fits), capsules.size, restricted, today, Modifier,
                                onMore = { if (restricted) onLocked() else go(Screen.ALBUM) },
                            ) { openRecent(it) }
                        }
                    }
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
    Header("타임캡슐") { IconBtn(Icons.Rounded.MoreHoriz, "시연 패널", onClick = onDev) }
}

/** Centered recording control. Starting still goes through the existing preparation policy. */
@Composable
private fun VoiceBar(drive: DriveController, friendCount: Int) {
    val car by drive.car.collectAsState()
    val audience by drive.defaultAudience.collectAsState()
    val tags = MoodTagEngine.tagsFor(car).filter { it.label != car.placeName }.map { it.label }
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Column(
        Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            onClick = clickWithSound { drive.bury() },
            modifier = Modifier.size(176.dp).semantics { contentDescription = "목소리 녹음 시작" },
            shape = CircleShape,
            color = if (pressed) Tc.Basic else Color.Transparent,
            interactionSource = source,
        ) {
            RecordingCapsule(Modifier.fillMaxSize())
        }
        Text("목소리 남기기", style = Type.TitleM, color = Tc.Text, textAlign = TextAlign.Center)
        Text(
            (listOf(
                when {
                    audience == "SELF" -> "나만"
                    friendCount == 0 -> "친구 모두에게(아직 친구 없음)"
                    else -> "친구 ${friendCount}명에게"
                },
                car.placeName ?: "위치 확인 중",
            ) + tags).joinToString(Sep),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            style = Type.LabelM, color = Tc.TextSub, textAlign = TextAlign.Center,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The single most relevant "now" message, as a plain row (or a question with two buttons). */
@Composable
private fun NowNotice(drive: DriveController, restricted: Boolean, today: LocalDate) {
    val forecast by drive.forecast.collectAsState()
    val reprompt by drive.reprompt.collectAsState()
    val arrival by drive.arrivalPrompt.collectAsState()
    val position by drive.position.collectAsState()
    val prepared by drive.prepared.collectAsState()
    var preparing by remember { mutableStateOf(false) }
    if (preparing) ConfirmPopup("이번 주행을 준비할까요?",
        "함께 탄 사람에게 녹음을 알리고 동의를 확인해 주세요. 준비만으로 녹음하지 않으며, 캡슐 알림에는 잠시 음성으로 답할 수 있어요.",
        "동의 확인 · 준비", onConfirm = { preparing = false; drive.prepareDrive() }, onDismiss = { preparing = false })
    val again = reprompt
    val f = forecast
    when {
        !prepared -> InlinePrompt("이번 주행 준비", "주차 중 권한과 동승자 동의를 확인해 주세요",
            cta = "준비하기" to { if (!restricted) preparing = true }, ctaIcon = Icons.Rounded.Mic,
            sub = null)
        arrival -> InlinePrompt(
            "도착했어요. 한마디 남길까요?", "이곳엔 아직 캡슐이 없어요",
            cta = "남기기" to { drive.answerArrival(true) }, ctaIcon = Icons.Rounded.Mic,
            sub = "괜찮아요" to { drive.answerArrival(false) },
        )
        again != null && !restricted -> InlinePrompt(
            again.ownerName?.let { "아까 지나친 ${it}님 캡슐이 있어요" } ?: "아까 지나친 캡슐이 있어요",
            "${Narration.elapsed(again.date(), today)}$Sep${again.placeName ?: "이 길"}",
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
        "가장 오래된 건 ${Narration.elapsed(o.date(), today)} ${o.placeName ?: ""}" + (dist?.let { "$Sep${formatDistance(it)} 남음" } ?: "")
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
        Section("최근 캡슐") {
            if (total > 0 && !driving) {
                Btn("전체 $total", icon = null, style = BtnStyle.Quiet, height = 88.dp, onClick = onMore)
            }
        }
        Panel {
            when {
                total == 0 -> ListRow("아직 묻은 캡슐이 없어요", "녹음 버튼을 누르고 첫 목소리를 남겨 보세요", leading = Icons.Rounded.Mic, leadingTint = Tc.TextMute)
                driving -> ListRow("목록은 차를 세우면 보여요", "운전 중에는 캡슐이 알림으로 찾아와요", leading = Icons.Rounded.Lock, leadingTint = Tc.TextMute)
                recent.isEmpty() -> ListRow("지금 표시할 최근 캡슐이 없어요", "전체 목록에서 개봉 대기 캡슐과 임시보관 녹음을 확인하세요", leading = Icons.Rounded.Inventory2, onClick = onMore)
                else -> recent.forEachIndexed { i, c ->
                    if (i > 0) RowDivider()
                    ListRow(
                        (c.ownerName?.let { "${it}님   " } ?: "") + (c.placeName ?: "장소 기록 없음"),
                        "${Narration.elapsed(c.date(), today)} · ${c.durationSec}초 · ${if (c.collectedAt != null || c.sourceRemoteId != null) "바로 듣기" else "수집 전"}",
                        leading = if (c.collectedAt != null || c.sourceRemoteId != null) Icons.Rounded.PlayArrow else Icons.Rounded.Lock,
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
    var recipientUid by rememberSaveable { mutableStateOf<String?>(null) }
    val to = friends.firstOrNull { it.uid == recipientUid }
    val prepared by drive.prepared.collectAsState()
    var preparing by remember { mutableStateOf(false) }
    if (preparing) ConfirmPopup("이번 주행을 준비할까요?",
        "함께 탄 사람에게 녹음을 알리고 동의를 확인해 주세요. 준비만으로 녹음하지 않아요.",
        "동의 확인 · 준비", onConfirm = { preparing = false; drive.prepareDrive() }, onDismiss = { preparing = false })
    val today = LocalDate.now()
    var pick by rememberSaveable { mutableStateOf(LetterPick.YEAR1) }
    var customDay by rememberSaveable { mutableStateOf(today.plusMonths(6).toEpochDay()) }
    val custom = LocalDate.ofEpochDay(customDay)
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
                radio(recipientUid == null, "나", "이 차에서 내가 열어요") { recipientUid = null }
                friends.forEach { f ->
                    RowDivider()
                    radio(to?.uid == f.uid, "${f.name}님", "${f.name}님 차가 이 길을 지나면 열려요") { recipientUid = f.uid }
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
                        IconBtn(Icons.Rounded.Remove, "한 달 앞으로", style = BtnStyle.Basic) { if (custom.minusMonths(1).isAfter(today)) customDay = custom.minusMonths(1).toEpochDay() }
                        Text(custom.format(dayFmt), style = Type.BodyStrong, color = Tc.Text, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                        IconBtn(Icons.Rounded.Add, "한 달 뒤로", style = BtnStyle.Basic) { customDay = custom.plusMonths(1).toEpochDay() }
                    }
                }
            }
        }
        Column(Modifier.widthIn(max = 960.dp).fillMaxWidth().align(Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
                Icon(Icons.Rounded.Lock, null, tint = Tc.TextMute, modifier = Modifier.size(28.dp))
                Text("  ${to?.let { "${it.name}님에게   " } ?: ""}${openOn.format(dayFmt)}에 열려요", style = Type.BodyS, color = Tc.TextSub)
            }
            if (recipientUid != null && to == null) Text("선택한 친구를 확인할 수 없어요. 받는 사람을 다시 선택해 주세요.", style = Type.BodyS, color = Tc.TextSub)
            Btn(if (prepared) "녹음하고 묻기" else "녹음 준비하기", Modifier.fillMaxWidth(), icon = Icons.Rounded.Mic, style = BtnStyle.Filled,
                enabled = (recipientUid == null || to != null) && !openOn.isBefore(today), height = Tc.TouchLarge) {
                if (!prepared) { preparing = true; return@Btn }
                val f = to
                if (f == null) drive.bury(openOn, audience = "SELF") else drive.bury(openOn, audience = "SOME", recipients = listOf(f.uid))
            }
        }
    }
}

// ---- Album (screen 03, parked only) -----------------------------------------------------

@Composable
fun AlbumScreen(app: TimeCapsuleApp, onMap: () -> Unit, back: () -> Unit) {
    val drive = app.drive
    val capsules by drive.capsules.collectAsState()
    val locks by drive.locks.collectAsState()
    val trips by drive.trips.collectAsState()
    val today = LocalDate.now()
    val previewId by drive.previewDraftId.collectAsState()
    val previewProgress by androidx.compose.runtime.produceState<Pair<Int, Int>?>(null, previewId) {
        value = null
        while (previewId != null) { value = drive.progress(); kotlinx.coroutines.delay(250) }
    }
    androidx.compose.runtime.DisposableEffect(drive) { onDispose { drive.stopDraftPreview() } }
    val tripById = trips.associateBy { it.id }
    var deleting by remember { mutableStateOf<Capsule?>(null) }
    var sharing by remember { mutableStateOf<Capsule?>(null) }
    var forwarding by remember { mutableStateOf<Capsule?>(null) }
    var routeTarget by remember { mutableStateOf<Capsule?>(null) }
    var ownTab by rememberSaveable { mutableStateOf(true) }
    var draftsExpanded by rememberSaveable { mutableStateOf(false) }
    val ownListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val collectedListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val position by drive.position.collectAsState()
    val mine = capsules.filter(AlbumData::mine)
    val collected = capsules.filter(AlbumData::collected)
    val visible = if (ownTab) mine else collected
    // F-08: group by trip; capsules outside a trip are grouped by day.
    val groups = visible.filterNot { it.draft }.groupBy { c -> c.tripId?.let { "trip:$it" } ?: "day:${c.date()}" }
        .entries.sortedByDescending { e -> e.value.maxOf { it.createdAt } }

    Column(Modifier.fillMaxSize().background(Tc.Bg).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 1320.dp).fillMaxWidth()) {
            Header("여행 앨범", back) { IconBtn(Icons.Rounded.Map, "수집 지도", onClick = onMap) }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                AlbumTab("내가 남긴 캡슐", mine.size, ownTab, Modifier.weight(1f)) { ownTab = true }
                AlbumTab("수집한 캡슐", collected.size, !ownTab, Modifier.weight(1f)) { drive.stopDraftPreview(); ownTab = false }
            }
        }
        if (visible.isEmpty()) {
            Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
                Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(64.dp), tint = Tc.TextMute)
                Text(if (ownTab) "아직 남긴 캡슐이 없어요" else "아직 수집한 캡슐이 없어요", style = Type.TitleS, color = Tc.Text)
                Text(if (ownTab) "길에 남긴 캡슐을 여기에서 확인하세요."
                    else "수집하거나 친구에게 받은 캡슐이 여기에 모여요.",
                    style = Type.BodyS, color = Tc.TextSub, textAlign = TextAlign.Center)
            }
        } else LazyColumn(
            Modifier.widthIn(max = 1320.dp).fillMaxWidth().weight(1f),
            state = if (ownTab) ownListState else collectedListState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val drafts = visible.filter { it.draft }
            if (drafts.isNotEmpty()) {
                item(key = "draft-summary") {
                    AlbumDraftHeader(drafts.size, draftsExpanded) { if (draftsExpanded) drive.stopDraftPreview(); draftsExpanded = !draftsExpanded }
                }
                if (draftsExpanded) drafts.forEach { c ->
                    item(key = "draft:${c.id}") {
                        AlbumDraftRow(c, onPreview = { drive.previewDraft(c) },
                            onSave = { drive.confirmDraft(c) }.takeUnless { c.locationUncertain },
                            onDelete = { deleting = c }, previewing = previewId == c.id, progress = previewProgress.takeIf { previewId == c.id })
                    }
                }
            }
            groups.forEach { (key, list) ->
                val trip = if (key.startsWith("trip:")) tripById[key.removePrefix("trip:").toLong()] else null
                item(key = key) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(groupTitle(trip, list), style = Type.LabelStrong, color = Tc.TextSub, modifier = Modifier.weight(1f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${list.size}개", style = Type.LabelM, color = Tc.TextMute)
                    }
                }
                list.sortedBy { it.createdAt }.forEach { c ->
                    item(key = "capsule:${c.id}") {
                        val openingReason = drive.openingReason(c, position)
                        CapsuleRow(c, locks[c.id]?.openAfter?.takeIf { locks[c.id].isLocked(today) },
                            onPlay = { drive.playCapsule(c.id) },
                            onShare = { sharing = c }.takeIf { c.isMine && !c.locationUncertain && c.audience in setOf("SELF", "FRIENDS") },
                            onForward = { forwarding = c }.takeIf { Forwarding.eligible(c) },
                            onNavigate = { routeTarget = c }.takeIf { AlbumData.canNavigate(c) },
                            locationBlocked = AlbumData.needsCollection(c) && openingReason != null,
                            onDelete = { deleting = c })
                    }
                }
            }
        }
    }
    routeTarget?.let { c ->
        ConfirmPopup("이 장소로 안내할까요?",
            "${c.placeName ?: "캡슐 지점"}을 내비게이션 목적지로 설정해요. 기존 경로가 있다면 바뀝니다.",
            "길 안내 시작", onConfirm = { drive.driveToCapsule(c.id); routeTarget = null },
            onDismiss = { routeTarget = null })
    }
    forwarding?.let { c -> ForwardCapsuleDialog(app, c) { forwarding = null } }
    sharing?.let { c ->
        val toFriends = c.audience == "SELF"
        ConfirmPopup(
            if (toFriends) "이 캡슐을 친구 모두에게 들려줄까요?" else "이 캡슐을 나만 들을까요?",
            if (toFriends) "지금 연결된 친구에게만 공유해요. 나중에 추가한 친구에게는 공개되지 않아요. 새 개봉에는 인터넷 연결이 필요해요."
            else "공유 철회를 요청해요. 오프라인 차량의 사본은 다음 연결 때 정리되며, 이미 들은 음성은 회수할 수 없어요.",
            if (toFriends) "친구에게 공개" else "나만 듣기",
            onConfirm = { drive.setShared(c, toFriends); sharing = null },
            onDismiss = { sharing = null },
        )
    }
    deleting?.let { c ->
        ConfirmPopup(
            "이 캡슐을 삭제할까요?",
            "${c.date().format(dayFmt)} ${c.placeName ?: ""}에서 남긴 목소리가 지워지고 되돌릴 수 없어요.",
            "삭제하기",
            onConfirm = { drive.delete(c); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

private fun groupTitle(trip: Trip?, list: List<Capsule>): String {
    val first = list.minOf { it.createdAt }
    val day = Instant.ofEpochMilli(trip?.startAt ?: first).atZone(ZoneId.systemDefault()).toLocalDate().format(dayFmt)
    return if (trip != null) "$day 여행" else day
}

private fun tripSummary(t: Trip): String {
    val km = if (t.startOdometer != null && t.endOdometer != null) "   ${"%.0f".format(t.endOdometer - t.startOdometer)}km" else ""
    val who = t.occupants?.takeIf { it > 1 }?.let { "   ${it}명" } ?: ""
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
            val prepared by app.drive.prepared.collectAsState()
            if (prepared) ListRow("이번 주행 종료", "기록 준비와 캡슐 알림을 종료해요", onClick = { app.drive.endDrive() })
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
                radio("SELF", "나만 (기본)", "이 차에만 남고 아무에게도 가지 않아요")
                RowDivider()
                radio("FRIENDS", "친구 모두에게", "녹음할 때 연결된 친구에게만 공유해요. 새 개봉에는 인터넷이 필요해요")
            }
            Spacer(Modifier.height(8.dp))
            Section("녹음")
            Panel {
                val maxSec by app.settings.maxRecordSec.collectAsState()
                val silence by app.settings.silenceSec.collectAsState()
                ListRow("최대 길이", "이 길이가 되면 저절로 묻혀요")
                ChoiceRow(Rules.RECORD_LENGTHS, maxSec, { lengthLabel(it).removePrefix("최대 ") }, app.settings::setMaxRecordSec)
                RowDivider()
                ListRow("말이 없으면 저절로 묻기", "정한 시간 동안 조용하면 그때까지의 녹음을 묻어요. 아무 말도 없었으면 저장하지 않아요")
                ChoiceRow(Rules.SILENCE_CHOICES, silence, { if (it == 0) "끄기" else "${it}초" }, app.settings::setSilenceSec)
            }
            Spacer(Modifier.height(8.dp))
            Section("개인정보")
            Panel {
                ListRow("녹음은 직접 누를 때만", "마이크는 버튼을 눌렀을 때와, 캡슐 알림에 말로 답할 때(몇 초)만 켜져요") { ValueText("항상") }
                RowDivider()
                ListRow("녹음 알림음", "함께 탄 사람도 알 수 있게 끌 수 없어요") { ValueText("켜짐") }
                RowDivider()
                ListRow("날씨", "묻을 때 그날 날씨를 함께 남겨요. 날씨 서버에는 대략 위치(약 10km)만 보내요") { ValueText("자동") }
                RowDivider()
                ListRow("저장 위치", "친구에게 공개한 캡슐만 서버에 올라가요. 앨범에서 하나씩 바꿀 수 있어요") { ValueText("이 차 + 서버") }
            }
            Spacer(Modifier.height(8.dp))
            Section("알림")
            Panel {
                ListRow("도착 제안", "처음 온 곳에 도착하면 물어봐요. 하루 2번까지") { TcSwitch(arrival, app.settings::setArrivalPrompt) }
                RowDivider()
                val ctx = androidx.compose.ui.platform.LocalContext.current
                var overlay by remember { mutableStateOf(android.provider.Settings.canDrawOverlays(ctx)) }
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    while (true) { overlay = android.provider.Settings.canDrawOverlays(ctx); kotlinx.coroutines.delay(1000) }
                }
                ListRow(
                    "지도 위에 알림 띄우기",
                    if (overlay) "내비를 보고 있어도 캡슐 알림이 지도 위에 떠요" else "켜지 않으면 내비를 보는 동안 알림이 알림 센터로만 가요",
                    onClick = {
                        ctx.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:" + ctx.packageName))
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                ) { ValueText(if (overlay) "켜짐" else "꺼짐") }
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

/** One row of choices; the picked one is filled. */
@Composable
private fun <T> ChoiceRow(choices: List<T>, picked: T, label: (T) -> String, onPick: (T) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = Tc.Inset, end = Tc.Inset, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        choices.forEach { c ->
            Btn(label(c), Modifier.weight(1f), style = if (c == picked) BtnStyle.Filled else BtnStyle.Basic, height = 64.dp) { onPick(c) }
        }
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
                if (fakeNavi != null) {
                    // One capsule per weather (and night): drive there and play it, to see its backdrop.
                    val samples = capsules.filter { !it.draft && !it.locationUncertain }
                        .groupBy { c ->
                            val sky = TheDay.sky(c.rawVehicleState)?.let(TheDay::label) ?: "날씨 없음"
                            sky + if (TheDay.isNight(c.createdAt)) " 밤" else ""
                        }.toSortedMap().mapValues { it.value.first() }
                    Section("날씨별 재생 (그 캡슐 자리로 옮겨서)")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        samples.forEach { (label, c) ->
                            Btn(label, height = 72.dp) {
                                fakeNavi.moveTo(NaviPosition(GeoPoint(c.lat, c.lng), c.placeName))
                                close()
                                app.drive.devPreview(c.id)
                            }
                        }
                    }
                }
                Section("시연")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val newest = capsules.maxByOrNull { it.createdAt }
                    Btn("최근 캡슐을 18년 전으로", enabled = newest != null, height = 72.dp) { newest?.let { app.drive.backdate(it.id, 18) } }
                    Btn("1.2km 접근", enabled = newest != null, height = 72.dp) { newest?.let { app.drive.simulateApproach(it.id); close() } }
                    Btn("10초 뒤 접근(앱 밖에서 보기)", enabled = newest != null, height = 72.dp) {
                        newest?.let { c -> close(); app.scope.launch { kotlinx.coroutines.delay(10_000); app.drive.simulateApproach(c.id) } }
                    }
                    Btn("도착 제안 띄우기", height = 72.dp) { close(); app.drive.simulateArrivalPrompt() }
                    Btn("다음 녹음 5초 뒤 내비 안내로 끊기", height = 72.dp) { app.drive.simulateRecordingInterruption(5_000) }
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
    Section("친구   ${app.social.label}")
    if (app.hasFirebaseKeys) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(start = Tc.Inset)) {
                Text("가짜 서버 쓰기", style = Type.BodyStrong, color = Tc.Text)
                Text("인터넷이 없는 곳에서 시연할 때   앱을 다시 켜면 적용돼요", style = Type.LabelM, color = Tc.TextMute)
            }
            var on by remember { mutableStateOf(app.fakeServer) }
            TcSwitch(on) { on = it; app.fakeServer = it }
        }
    }
    Text(
        me?.let { "지금 이 차: ${it.name} (${spaced(it.code)})   친구 ${friends.size}명" } ?: "아직 이름을 정하지 않았어요",
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
