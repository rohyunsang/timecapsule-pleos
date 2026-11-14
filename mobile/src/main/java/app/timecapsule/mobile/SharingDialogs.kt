package app.timecapsule.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import app.timecapsule.social.Friend
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
    val friendIds = friends.map { it.uid }.toSet()
    val activeSelection = selected.intersect(friendIds)
    CapsuleSharingContent(friends, activeSelection, forwarding, busy,
        select = { uid -> selected = if (forwarding) setOf(uid) else if (uid in activeSelection) activeSelection - uid else activeSelection + uid },
        confirm = {
            if (forwarding) activeSelection.singleOrNull()?.let { model.forward(capsule, it, dismiss) }
            else model.share(capsule, activeSelection.toList(), dismiss)
        }, dismiss = dismiss, friendsScreen = { dismiss(); friendsScreen() })
}

@Composable
internal fun CapsuleSharingContent(
    friends: List<Friend>, selected: Set<String>, forwarding: Boolean, busy: Boolean,
    select: (String) -> Unit, confirm: () -> Unit, dismiss: () -> Unit, friendsScreen: () -> Unit,
) {
    AlertDialog(onDismissRequest = { if (!busy) dismiss() },
        title = { Text(if (forwarding) "친구에게 사본 보내기" else "캡슐 공개 범위") },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text(if (forwarding) "받을 친구 한 명을 선택해 주세요. 받은 친구는 어디서든 들을 수 있어요. 원본 공유를 취소하거나 삭제해도 보낸 사본은 남습니다." else "선택한 친구가 이 장소에 방문해 수집할 수 있어요. 아무도 선택하지 않으면 나에게만 보입니다.")
                }
                if (friends.isEmpty()) item {
                    Text("아직 연결된 친구가 없어요.")
                    TextButton(onClick = friendsScreen, enabled = !busy) { Text("친구 추가하러 가기") }
                }
                items(friends, key = { it.uid }) { friend ->
                    val checked = friend.uid in selected
                    val selection = if (forwarding) Modifier.selectable(checked, enabled = !busy, role = Role.RadioButton) { select(friend.uid) }
                        else Modifier.toggleable(checked, enabled = !busy, role = Role.Checkbox) { select(friend.uid) }
                    Row(Modifier.fillMaxWidth().then(selection).heightIn(min = 56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(friend.name, Modifier.weight(1f).padding(end = 12.dp))
                        if (forwarding) RadioButton(checked, onClick = null, enabled = !busy)
                        else Checkbox(checked, onCheckedChange = null, enabled = !busy)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = confirm, enabled = !busy && (!forwarding || selected.size == 1)) { Text(if (busy) "처리 중…" else if (forwarding) "보내기" else "공개 범위 저장") } },
        dismissButton = { TextButton(onClick = dismiss, enabled = !busy) { Text("취소") } })
}
