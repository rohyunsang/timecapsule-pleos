package app.timecapsule.mobile

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.timecapsule.companion.*
import app.timecapsule.social.RemoteCapsule
import java.time.*
import java.time.format.DateTimeFormatter

internal data class CapsuleDetailActions(
    val listen: () -> Unit, val navigate: () -> Unit, val sendToCar: () -> Unit,
    val saveMemory: (CapsuleMemory) -> Unit, val share: () -> Unit, val forward: () -> Unit,
    val reply: () -> Unit, val pickPhoto: () -> Unit, val deletePhoto: () -> Unit,
)

@Composable
internal fun CapsuleDetailContent(c: RemoteCapsule, stored: CapsuleMemory, uid: String?, collected: Boolean,
    destination: CarDestination?, bitmap: ImageBitmap?, playing: Boolean, busy: Boolean, now: Long, actions: CapsuleDetailActions) {
    var title by rememberSaveable(c.id) { mutableStateOf(stored.title) }
    var note by rememberSaveable(c.id) { mutableStateOf(stored.note) }
    var edited by rememberSaveable(c.id) { mutableStateOf(false) }
    LaunchedEffect(stored.title, stored.note) {
        if (!edited) { title = stored.title; note = stored.note }
        if (title == stored.title && note == stored.note) edited = false
    }
    val ready = collected || c.sourceRemoteId != null || ReplyPolicy.remoteOpen(c.replyMode, c.replyAvailableAt, now)
    val lockedDate = CapsuleAvailability.state(c, collected, now) == CapsuleAvailability.State.WAITING
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(c.placeName ?: "캡슐을 남긴 장소", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item { Text("${c.originalOwnerName ?: c.ownerName}의 목소리 · ${c.durationSec}초") }
        item { Text(if (lockedDate) replyWaitingLabel(c, now) else if (ready) "어디서든 들을 수 있는 목소리예요." else "이 장소에서 수집해 주세요. 수집하면 다음부터 어디서든 들을 수 있어요.") }
        item { Button(onClick = actions.listen, enabled = !busy && !lockedDate, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Icon(if (playing) Icons.Rounded.Stop else if (lockedDate) Icons.Rounded.Lock else Icons.Rounded.PlayArrow, null)
            Spacer(Modifier.width(8.dp)); Text(if (playing) "재생 정지" else if (lockedDate) "개봉 대기" else if (ready) "목소리 듣기" else "여기서 수집하고 듣기")
        } }
        item {
            val navigateButton: @Composable (Modifier) -> Unit = { modifier ->
                OutlinedButton(onClick = actions.navigate, modifier) { Text("길 찾기") }
            }
            val carButton: @Composable (Modifier) -> Unit = { modifier ->
                OutlinedButton(onClick = actions.sendToCar, modifier, enabled = !busy && destination?.status != "pending") { Text(if (destination?.status == "pending") "차량 확인 대기" else "차량으로 보내기") }
            }
            if (androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.2f) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                navigateButton(Modifier.fillMaxWidth()); carButton(Modifier.fillMaxWidth())
            } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                navigateButton(Modifier.weight(1f)); carButton(Modifier.weight(1f))
            }
        }
        destination?.let { request -> item {
            Text(when (request.status) {
                "pending" -> "차량이 연결되면 주차 중에 안내 요청을 확인할 수 있어요."
                "accepted" -> "차량에서 길 안내 요청을 확인했어요. 실제 경로는 차량 내비게이션에서 확인해 주세요."
                "dismissed" -> "차량에서 안내 요청을 닫았어요. 필요하면 다시 보낼 수 있어요."
                else -> "차량에서 안내를 시작하지 못했어요. 다시 보내 주세요."
            }, style = MaterialTheme.typography.bodySmall)
        } }
        item { OutlinedButton(onClick = { actions.saveMemory(stored.copy(visitLater = !stored.visitLater)) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (stored.visitLater) "방문할 장소에서 빼기" else "방문할 장소에 저장") } }
        if (c.ownerUid == uid && c.sourceRemoteId == null && c.replyToId == null) item {
            OutlinedButton(onClick = actions.share, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("공개 범위 변경") }
        }
        if (uid?.let { RemoteDelivery.eligible(c, it, collected) } == true) item {
            OutlinedButton(onClick = actions.forward, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("친구에게 보내기") }
        }
        if (ready && !lockedDate) item {
            OutlinedButton(onClick = actions.reply, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("음성 답장 남기기") }
        }
        item { HorizontalDivider() }
        item { Text("나의 기록", style = MaterialTheme.typography.titleLarge) }
        item { OutlinedButton(onClick = { actions.saveMemory(stored.copy(favorite = !stored.favorite)) }, enabled = !busy) { Text(if (stored.favorite) "즐겨찾기 해제" else "즐겨찾기") } }
        bitmap?.let { image -> item { Image(image, "이 캡슐에 추가한 내 사진", Modifier.fillMaxWidth().height(220.dp)) } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = actions.pickPhoto, enabled = !busy) { Text(if (bitmap == null) "사진 추가" else "사진 바꾸기") }
            if (bitmap != null) TextButton(onClick = actions.deletePhoto, enabled = !busy) { Text("사진 삭제") }
        } }
        item { OutlinedTextField(title, { title = it.take(120); edited = true }, label = { Text("추억의 제목") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(note, { note = it.take(4000); edited = true }, label = { Text("직접 남기는 메모") }, modifier = Modifier.fillMaxWidth(), minLines = 3) }
        item { Button(onClick = { actions.saveMemory(stored.copy(title = title, note = note)) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("기록 저장") } }
    }
}

private fun replyWaitingLabel(c: RemoteCapsule, now: Long): String {
    if (ReplyPolicy.waiting(c.replyMode, c.replyAvailableAt, now)) {
        val at = c.replyAvailableAt ?: return "전송 시각을 확인하고 있어요"
        val date = java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault())
        return "${date.format(DateTimeFormatter.ofPattern("M월 d일 HH:mm"))}부터 방문 없이 듣기"
    }
    return c.openAfter?.let { "${LocalDate.ofEpochDay(it)} 개봉" } ?: "개봉 대기"
}
