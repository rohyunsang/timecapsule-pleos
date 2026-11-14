package app.timecapsule.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.timecapsule.capsule.AlbumData
import app.timecapsule.data.Capsule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Fixed-height tabs with a persistent selection marker, separate from action buttons. */
@Composable
internal fun AlbumTab(label: String, count: Int, active: Boolean, modifier: Modifier, select: () -> Unit) {
    Column(modifier.semantics { selected = active; role = Role.Tab }
        .clickable(onClick = clickWithSound(select))) {
        Row(Modifier.fillMaxWidth().height(88.dp).padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = Type.Button, color = if (active) Tc.Text else Tc.TextMute,
                modifier = Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(count.toString(), style = Type.LabelStrong, color = if (active) Tc.P.onFilled else Tc.TextMute,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (active) Tc.P.filled else Tc.Basic)
                    .padding(horizontal = 12.dp, vertical = 4.dp), maxLines = 1)
        }
        Box(Modifier.fillMaxWidth().height(4.dp).background(if (active) Tc.P.filled else Tc.Line))
    }
}

@Composable
internal fun AlbumDraftHeader(count: Int, expanded: Boolean, toggle: () -> Unit) {
    Surface(onClick = clickWithSound(toggle), shape = RoundedCornerShape(16.dp), color = Tc.Basic) {
        Row(Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Icon(Icons.Rounded.Inventory2, null, Modifier.size(32.dp), tint = Tc.TextSub)
            Text("임시보관 $count", style = Type.LabelStrong, color = Tc.Text, modifier = Modifier.weight(1f))
            Text(if (expanded) "접기" else "확인", style = Type.LabelM, color = Tc.TextSub)
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                if (expanded) "임시보관 접기" else "임시보관 펼치기", Modifier.size(32.dp), tint = Tc.TextSub)
        }
    }
}

@Composable
internal fun AlbumDraftRow(c: Capsule, onPreview: () -> Unit, onSave: (() -> Unit)?, onDelete: () -> Unit, previewing: Boolean = false, progress: Pair<Int, Int>? = null) {
    AlbumTile(c, title = "임시보관 녹음", status = if (previewing) "미리 듣는 중" else if (c.locationUncertain) "위치 미확정" else "저장 대기",
        metadata = if (previewing) progress?.let { "${it.first / 1000}초 / ${it.second / 1000}초" } ?: "음성 재생 준비 중" else "${c.durationSec}초 · ${c.date().format(dayFmt)}",
        detail = if (c.locationUncertain) "위치 없이 보관한 비공개 음성" else "중단된 녹음 · 저장 후 현장에서 수집",
        onNavigate = null, secondaryLabel = "저장", onSecondary = onSave,
        mainIcon = if (previewing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, mainDescription = if (previewing) "미리 듣기 정지" else "임시보관 미리 듣기", mainEnabled = true,
        onMain = onPreview, onDelete = onDelete)
}

@Composable
internal fun CapsuleRow(c: Capsule, lockedUntil: Long?, onPlay: () -> Unit, onShare: (() -> Unit)?,
    onForward: (() -> Unit)?, onNavigate: (() -> Unit)?, locationBlocked: Boolean, onDelete: () -> Unit) {
    val replyWaiting = app.timecapsule.companion.ReplyPolicy.waiting(c.replyMode, c.replyAvailableAt, System.currentTimeMillis())
    val locked = lockedUntil != null || locationBlocked || replyWaiting
    val state = when {
        replyWaiting -> "답장 개봉 대기"
        c.replyMode == app.timecapsule.companion.ReplyPolicy.AFTER_THREE_DAYS -> "어디서든 듣기"
        lockedUntil != null -> "개봉 대기"
        locationBlocked -> "수집 전"
        c.isForwarded -> "전달받음"
        c.collectedAt != null -> "수집 완료"
        else -> "수집 가능"
    }
    val owner = when {
        c.isForwarded -> "${c.originalOwnerName ?: "친구"}님이 남김"
        c.ownerName != null -> "${c.ownerName}님이 남김"
        c.audience == "SELF" -> "나만"
        c.audience == "SOME" -> "지정한 친구"
        else -> "친구 모두"
    }
    val time = Instant.ofEpochMilli(c.createdAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("a h:mm"))
    val detail = when {
        replyWaiting -> c.replyAvailableAt?.let { "${Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M월 d일 HH:mm"))}부터 방문 없이 듣기" } ?: "전송 시각을 확인하고 있어요"
        c.replyMode == app.timecapsule.companion.ReplyPolicy.AFTER_THREE_DAYS -> "3일이 지난 답장 · 어디서든 듣기"
        lockedUntil != null -> "${LocalDate.ofEpochDay(lockedUntil).format(dayFmt)}부터 현장에서 열기"
        c.locationUncertain -> "장소를 확인할 수 없어요"
        locationBlocked -> "잠금 버튼을 누르면 이 장소로 길 안내"
        c.isForwarded -> "${c.ownerName ?: "친구"}님이 전달 · 어디서든 듣기"
        c.collectedAt != null -> "어디서든 다시 듣기"
        else -> "이 장소에 도착했어요 · 재생하면 수집돼요"
    }
    AlbumTile(c, title = c.placeName ?: "장소 기록 없음", status = state,
        metadata = "$time · ${c.durationSec}초 · $owner", detail = detail, onNavigate = onNavigate,
        secondaryLabel = if (onForward != null) "친구에게 보내기" else if (c.audience == "SELF") "친구 공개" else "나만 듣기",
        onSecondary = onForward ?: onShare,
        mainIcon = if (locked) Icons.Rounded.Lock else Icons.Rounded.PlayArrow,
        mainDescription = if (locked) "잠긴 캡슐 장소로 길 안내" else if (AlbumData.needsCollection(c)) "수집하고 듣기" else "캡슐 재생",
        mainEnabled = !replyWaiting && (!locked || onNavigate != null),
        onMain = if (locked) ({ onNavigate?.invoke(); Unit }) else onPlay, onDelete = onDelete)
}

/** Main action and delete remain adjacent; narrow windows move only the secondary action below. */
@Composable
private fun AlbumTile(c: Capsule, title: String, status: String, metadata: String, detail: String,
    onNavigate: (() -> Unit)?, secondaryLabel: String, onSecondary: (() -> Unit)?,
    mainIcon: androidx.compose.ui.graphics.vector.ImageVector, mainDescription: String, mainEnabled: Boolean,
    onMain: () -> Unit, onDelete: () -> Unit) {
    val density = LocalDensity.current.density
    val artwork = remember(density) { CapsuleArtwork.bitmap(density).asImageBitmap() }
    BoxWithConstraints(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Tc.Basic)) {
        val compact = maxWidth < 1120.dp
        val showArtwork = maxWidth >= 820.dp
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                if (showArtwork) {
                    Box(Modifier.size(80.dp).clip(RoundedCornerShape(16.dp)).background(Tc.Line), contentAlignment = Alignment.Center) {
                        Image(artwork, null, Modifier.size(72.dp))
                    }
                }
                Column(Modifier.weight(1f).clickable(enabled = onNavigate != null,
                    onClick = clickWithSound { onNavigate?.invoke() }), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(status, style = Type.LabelStrong, color = Tc.TextSub, maxLines = 1)
                        Text(metadata, style = Type.LabelM, color = Tc.TextMute, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, style = Type.TitleS, color = Tc.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false))
                        if (onNavigate != null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "장소 길 안내", Modifier.size(32.dp), tint = Tc.TextSub)
                    }
                    Text(detail, style = Type.LabelM, color = Tc.TextSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!compact && onSecondary != null) Btn(secondaryLabel, Modifier.width(if (secondaryLabel.length > 6) 256.dp else 176.dp), height = 88.dp,
                    style = BtnStyle.Quiet, onClick = onSecondary)
                Surface(onClick = clickWithSound(onMain), enabled = mainEnabled, modifier = Modifier.size(88.dp),
                    shape = RoundedCornerShape(20.dp), color = if (mainEnabled) Tc.P.filled else Tc.Line) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(mainIcon, mainDescription, Modifier.size(44.dp), tint = if (mainEnabled) Tc.P.onFilled else Tc.TextMute)
                    }
                }
                IconBtn(Icons.Rounded.DeleteOutline, "${c.placeName ?: "캡슐"} 삭제", size = 88.dp, onClick = onDelete)
            }
            if (compact && onSecondary != null) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Tc.Line))
                Btn(secondaryLabel, Modifier.fillMaxWidth(), style = BtnStyle.Quiet, height = 88.dp, onClick = onSecondary)
            }
        }
    }
}
