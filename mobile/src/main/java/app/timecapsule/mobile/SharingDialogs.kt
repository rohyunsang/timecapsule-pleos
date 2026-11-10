package app.timecapsule.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.timecapsule.social.RemoteCapsule

@Composable
fun CapsuleSharingDialog(model: MobileModel, capsule: RemoteCapsule, forwarding: Boolean, dismiss: () -> Unit, friendsScreen: () -> Unit) {
    val friends by model.friends.collectAsState()
    val busy by model.busy.collectAsState()
    var selected by remember(capsule.id) { mutableStateOf(if (forwarding) emptySet() else capsule.visibleTo.toSet()) }
    AlertDialog(onDismissRequest = { if (!busy) dismiss() },
        title = { Text(if (forwarding) "친구에게 사본 보내기" else "캡슐 공개 범위") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (forwarding) "받은 친구는 어디서든 들을 수 있어요. 원본 공유를 취소하거나 삭제해도 보낸 사본은 남습니다." else "선택한 친구가 이 장소에 방문해 수집할 수 있어요. 아무도 선택하지 않으면 나에게만 보입니다.")
            if (friends.isEmpty()) TextButton(onClick = { dismiss(); friendsScreen() }) { Text("친구 추가하러 가기") }
            LazyColumn(Modifier.heightIn(max = 260.dp)) { items(friends, key = { it.uid }) { friend ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { selected = if (forwarding) setOf(friend.uid) else if (friend.uid in selected) selected - friend.uid else selected + friend.uid }, enabled = !busy) { Text(friend.name) }
                    Checkbox(friend.uid in selected, onCheckedChange = { selected = if (forwarding) setOf(friend.uid) else if (it) selected + friend.uid else selected - friend.uid }, enabled = !busy)
                }
            } }
        } },
        confirmButton = { TextButton(onClick = {
            if (forwarding) selected.firstOrNull()?.let { model.forward(capsule, it, dismiss) }
            else model.share(capsule, selected.toList(), dismiss)
        }, enabled = !busy && (!forwarding || selected.size == 1)) { Text(if (busy) "처리 중…" else if (forwarding) "보내기" else "공개 범위 저장") } },
        dismissButton = { TextButton(onClick = dismiss, enabled = !busy) { Text("취소") } })
}
