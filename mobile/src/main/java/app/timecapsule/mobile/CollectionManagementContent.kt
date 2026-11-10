package app.timecapsule.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.timecapsule.companion.*
import app.timecapsule.social.*

private val CapsuleIdSetSaver = listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() })

@Composable
internal fun AlbumsContent(albums: List<MemoryAlbum>, capsules: List<RemoteCapsule>, uid: String?, friends: List<Friend>, busy: Boolean,
    open: (String) -> Unit, save: (MemoryAlbum, () -> Unit) -> Unit, delete: (String, () -> Unit) -> Unit) {
    var editingId by rememberSaveable(uid) { mutableStateOf<String?>(null) }
    val editing = editingId?.let { id -> albums.firstOrNull { it.id == id } ?: if (id.isEmpty()) MemoryAlbum("", "", emptyList()) else null }
    var title by rememberSaveable(uid) { mutableStateOf("") }
    var selected by rememberSaveable(uid, stateSaver = CapsuleIdSetSaver) { mutableStateOf(setOf<String>()) }
    var members by rememberSaveable(uid, stateSaver = CapsuleIdSetSaver) { mutableStateOf(setOf<String>()) }
    var deleting by remember { mutableStateOf<MemoryAlbum?>(null) }
    fun edit(album: MemoryAlbum) { editingId = album.id; title = album.title; selected = album.capsuleIds.toSet(); members = album.members.filter { it != uid }.toSet() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("한 여행을 하나의 앨범으로", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { Button(onClick = { if (editing != null) editingId = null else edit(MemoryAlbum("", "", emptyList())) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (editing != null) "편집 닫기" else "새 앨범 만들기") } }
        if (editingId != null && editing == null) item { Text("편집하던 앨범을 확인하고 있어요. 연결과 공유 상태를 확인해 주세요.") }
        editing?.let { album ->
            item { OutlinedTextField(title, { title = it.take(100) }, label = { Text("앨범 이름") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) }
            item { Text("담을 캡슐", style = MaterialTheme.typography.titleMedium) }
            items(capsules, key = { "select-${it.id}" }) { c -> Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { selected = if (c.id in selected) selected - c.id else selected + c.id }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) { Checkbox(c.id in selected, onCheckedChange = null); Text(c.placeName ?: "캡슐") } }
            selected.filter { id -> capsules.none { it.id == id } }.forEach { missing ->
                item(key = "unavailable-$missing") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("공유가 끝났거나 확인할 수 없는 캡슐", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { selected = selected - missing }, enabled = !busy) { Text("앨범에서 빼기") }
                    }
                }
            }
            item { Text("가족·친구와 함께 보기", style = MaterialTheme.typography.titleMedium) }
            item { Text("앨범 이름과 목록을 공유합니다. 음성은 각 캡슐의 공개 범위와 수집 상태를 따라요. 내 사진과 메모는 공유되지 않아요.", style = MaterialTheme.typography.bodySmall) }
            items(friends, key = { "member-${it.uid}" }) { friend -> Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { members = if (friend.uid in members) members - friend.uid else members + friend.uid }, verticalAlignment = Alignment.CenterVertically) { Checkbox(friend.uid in members, null); Text(friend.name) } }
            if (members.any { id -> friends.none { it.uid == id } }) item { TextButton(onClick = { members = members.filter { id -> friends.any { it.uid == id } }.toSet() }, enabled = !busy) { Text("연결이 끊긴 친구를 공유 대상에서 빼기") } }
            item { Button(onClick = { save(album.copy(title = title, capsuleIds = selected.toList(), members = members.toList())) { editingId = null } }, enabled = !busy && title.isNotBlank()) { Text("앨범 저장") } }
        }
        if (albums.isEmpty() && editing == null) item { EmptyPanel("아직 앨범이 없어요", "여행에서 모은 캡슐을 골라 나만의 앨범을 만들어 보세요.") }
        items(albums, key = { it.id }) { album ->
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(album.title, style = MaterialTheme.typography.titleLarge)
                Text("캡슐 ${album.capsuleIds.size}개 · 함께 보는 사람 ${album.members.size}명")
                album.capsuleIds.forEach { id ->
                    val c = capsules.firstOrNull { it.id == id }
                    if (c != null) TextButton(onClick = { open(id) }) { Text(c.placeName ?: "캡슐 열기") }
                    else Text("비공개이거나 공유가 종료된 캡슐", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                if (album.ownerUid == uid) Row {
                    TextButton(onClick = { edit(album) }, enabled = !busy) { Text("앨범 편집·공유") }
                    TextButton(onClick = { deleting = album }, enabled = !busy) { Text("앨범 삭제") }
                }
            } }
        }
    }
    deleting?.let { album -> AlertDialog(onDismissRequest = { if (!busy) deleting = null }, title = { Text("앨범을 삭제할까요?") }, text = { Text("함께 보는 사람에게도 앨범이 사라져요. 원본 캡슐과 음성은 지워지지 않아요.") }, confirmButton = { TextButton(onClick = { delete(album.id) { deleting = null } }, enabled = !busy) { Text("삭제") } }, dismissButton = { TextButton(onClick = { deleting = null }, enabled = !busy) { Text("취소") } }) }
}

@Composable
internal fun AccountContent(me: Profile?, friends: List<Friend>, email: String?, busy: Boolean,
    connectEmail: (String, String) -> Unit, addFriend: (String) -> Unit, removeFriend: (Friend, () -> Unit) -> Unit, logout: () -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    var linkEmail by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var removing by remember { mutableStateOf<Friend?>(null) }
    var loggingOut by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("${me?.name}님의 타임캡슐", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { Text(email ?: "이메일을 연결하면 차량에서도 같은 기록을 볼 수 있어요.") }
        if (email == null) {
            item { OutlinedTextField(linkEmail, { linkEmail = it }, label = { Text("연결할 이메일") }) }
            item { OutlinedTextField(password, { password = it }, label = { Text("비밀번호") }, visualTransformation = PasswordVisualTransformation()) }
            item { Button(onClick = { connectEmail(linkEmail, password) }, enabled = !busy && linkEmail.contains('@') && password.length >= 6) { Text("이메일 연결") } }
        }
        item { Text("내 친구 코드  ${me?.code}", style = MaterialTheme.typography.titleLarge) }
        item { OutlinedTextField(code, { code = it.uppercase().take(8) }, label = { Text("친구 코드") }, modifier = Modifier.fillMaxWidth()) }
        item { Button(onClick = { addFriend(code) }, enabled = !busy && FriendCode.normalize(code) != null) { Text("친구 추가") } }
        items(friends, key = { it.uid }) { friend -> ListItem(headlineContent = { Text(friend.name) }, supportingContent = { Text(friend.code) }, trailingContent = { TextButton(onClick = { removing = friend }, enabled = !busy) { Text("연결 해제") } }) }
        item { HorizontalDivider() }
        item { Text("차량 연결", style = MaterialTheme.typography.titleLarge) }
        item { Text("차량의 친구 화면에서 같은 이메일로 로그인하세요. 비공개 캡슐은 차량에서 동기화를 켜야 모바일로 가져옵니다. 수집 기록과 방문할 장소는 같은 계정에 저장됩니다.") }
        item { OutlinedButton(onClick = { loggingOut = true }, enabled = !busy) { Text("로그아웃") } }
    }
    removing?.let { friend -> AlertDialog(onDismissRequest = { if (!busy) removing = null }, title = { Text("${friend.name}님과 연결을 해제할까요?") }, text = { Text("새로운 공유 캡슐을 주고받을 수 없어요. 이미 전달된 사본은 회수되지 않습니다.") }, confirmButton = { TextButton(onClick = { removeFriend(friend) { removing = null } }, enabled = !busy) { Text("연결 해제") } }, dismissButton = { TextButton(onClick = { removing = null }, enabled = !busy) { Text("취소") } }) }
    if (loggingOut) AlertDialog(onDismissRequest = { loggingOut = false }, title = { Text("로그아웃할까요?") }, text = { Text("서버의 기록은 그대로 보관됩니다.") }, confirmButton = { TextButton(onClick = { loggingOut = false; logout() }) { Text("로그아웃") } }, dismissButton = { TextButton(onClick = { loggingOut = false }) { Text("취소") } })
}

