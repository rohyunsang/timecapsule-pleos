package app.timecapsule.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.timecapsule.TimeCapsuleApp
import app.timecapsule.data.Capsule
import kotlinx.coroutines.launch

@Composable
fun ForwardCapsuleDialog(app: TimeCapsuleApp, capsule: Capsule, dismiss: () -> Unit) {
    val friends by app.social.friends.collectAsState()
    val me by app.social.me.collectAsState()
    var selected by remember(capsule.id) { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val recipient = friends.firstOrNull { it.uid == selected }
    Dialog(onDismissRequest = { if (!sending) dismiss() }) {
        Surface(Modifier.widthIn(max = 720.dp), shape = RoundedCornerShape(32.dp), color = Tc.Surface) {
            Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(if (sent) "캡슐을 보냈어요" else "친구에게 보내기", style = Type.TitleS, color = Tc.Text)
                if (sent) {
                    Text("받은 친구는 앨범에서 어디서든 들을 수 있어요.", style = Type.BodyS, color = Tc.TextSub)
                    Btn("확인", Modifier.fillMaxWidth(), onClick = dismiss)
                } else {
                    Text("${capsule.ownerName ?: "친구"}님이 남긴 캡슐의 음성 사본을 보내요. 친구는 위치와 관계없이 바로 들을 수 있어요. 원본을 삭제해도 보낸 사본은 남아요.",
                        style = Type.BodyS, color = Tc.TextSub)
                    if (me == null || friends.isEmpty()) {
                        Text(if (me == null) "친구 탭에서 먼저 이름을 정해 주세요." else "친구 탭에서 친구를 추가해 주세요.", style = Type.BodyS, color = Tc.Text)
                    } else {
                        LazyColumn(Modifier.heightIn(max = 260.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(friends, key = { it.uid }) { friend ->
                                Btn(friend.name + if (selected == friend.uid) " · 선택됨" else "",
                                    Modifier.fillMaxWidth(), style = if (selected == friend.uid) BtnStyle.Filled else BtnStyle.Basic,
                                    enabled = !sending) { selected = friend.uid; error = null }
                            }
                        }
                    }
                    error?.let { Text(it, style = Type.BodyS, color = Tc.Text) }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Btn("취소", Modifier.weight(1f), enabled = !sending, onClick = dismiss)
                        Btn(if (sending) "보내는 중…" else recipient?.let { "${it.name}님에게 보내기" } ?: "친구를 선택해 주세요",
                            Modifier.weight(1f), style = BtnStyle.Filled, enabled = !sending && recipient != null && me != null) {
                            val uid = recipient?.uid ?: return@Btn
                            sending = true; error = null
                            scope.launch {
                                try {
                                    app.sync.forward(capsule.id, uid, app.drive::canEdit)
                                        .onSuccess { sent = true }
                                        .onFailure { error = it.message ?: "보내지 못했어요. 다시 시도해 주세요." }
                                } finally { sending = false }
                            }
                        }
                    }
                }
            }
        }
    }
}
