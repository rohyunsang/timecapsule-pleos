package app.timecapsule.mobile

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.timecapsule.companion.*
import app.timecapsule.social.RemoteCapsule

@Composable
internal fun LoginContent(busy: Boolean, login: (String, String) -> Unit, register: (String, String, String) -> Unit, reset: (String) -> Unit) {
    var registering by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Spacer(Modifier.height(16.dp)); Text("그 길의 목소리,\n이제 손안에서도", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold) }
        item { Text("차량과 같은 계정으로 연결하세요. 장소에서 수집한 캡슐은 어디서든 다시 들을 수 있어요.", color = Color(0xFF515151)) }
        if (registering) item { OutlinedTextField(name, { name = it.take(12) }, enabled = !busy, label = { Text("이름") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { OutlinedTextField(email, { email = it }, enabled = !busy, label = { Text("이메일") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { OutlinedTextField(password, { password = it }, enabled = !busy, label = { Text("비밀번호") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { Button(onClick = { if (registering) register(name, email.trim(), password) else login(email.trim(), password) }, enabled = !busy && email.contains('@') && password.length >= 6 && (!registering || name.isNotBlank()), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(if (registering) "계정 만들기" else "차량 계정으로 로그인") } }
        item { TextButton(onClick = { registering = !registering }, enabled = !busy) { Text(if (registering) "이미 계정이 있어요" else "처음이에요 · 계정 만들기") } }
        item { TextButton(onClick = { reset(email.trim()) }, enabled = !busy && email.contains('@')) { Text("비밀번호를 잊었어요") } }
    }
}

@Composable
internal fun MemoryLibraryContent(records: CompanionSnapshot, capsules: List<RemoteCapsule>, uid: String?, now: Long, open: (String) -> Unit) {
    var query by rememberSaveable(uid) { mutableStateOf("") }
    var filter by rememberSaveable(uid) { mutableIntStateOf(0) }
    val memories = records.memories.associateBy { it.capsuleId }
    val collectedIds = records.collections.map { it.capsuleId }.toSet()
    fun collected(c: RemoteCapsule) = c.id in collectedIds || c.sourceRemoteId != null
    val filtered = capsules.filter { c ->
        val memory = memories[c.id]
        val matches = listOf(c.placeName, c.ownerName, c.originalOwnerName, memory?.title, memory?.note).any { it?.contains(query.trim(), true) == true }
        matches && when (filter) { 1 -> c.ownerUid == uid; 2 -> collected(c); 3 -> memory?.visitLater == true; 4 -> memory?.favorite == true; else -> true }
    }.sortedByDescending { it.createdAt }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("차곡차곡 쌓인 목소리", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { Text("수집한 추억 ${capsules.count(::collected)}개 · 캡슐 ${capsules.size}개", color = Color(0xFF6C6C6C)) }
        item { OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text("검색") }, placeholder = { Text("장소, 사람, 메모", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true) }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("전체", "내 캡슐", "수집", "갈 곳", "즐겨찾기").forEachIndexed { i, text -> FilterChip(filter == i, onClick = { filter = i }, label = { Text(text) }) }
        } }
        if (filtered.isEmpty()) item {
            when {
                records.loading -> EmptyPanel("추억을 불러오고 있어요", "연결 상태에 따라 잠시 걸릴 수 있어요.")
                records.error != null -> EmptyPanel("기록을 불러오지 못했어요", "인터넷 연결과 로그인 상태를 확인해 주세요. 문제가 계속되면 친구·설정에서 계정을 확인할 수 있어요.")
                query.isNotBlank() -> { EmptyPanel("검색 결과가 없어요", "다른 장소나 이름으로 검색해 보세요."); TextButton(onClick = { query = "" }) { Text("검색어 지우기") } }
                filter != 0 -> { EmptyPanel("이 목록은 아직 비어 있어요", when (filter) { 2 -> "장소에 방문해 캡슐을 수집하면 이곳에 모여요."; 3 -> "캡슐 상세에서 방문할 장소를 저장해 보세요."; 4 -> "캡슐 상세에서 즐겨찾기를 눌러 보세요."; else -> "차량에서 남긴 캡슐을 동기화하면 이곳에 모여요." }); TextButton(onClick = { filter = 0 }) { Text("전체 캡슐 보기") } }
                else -> EmptyPanel("첫 추억을 기다리고 있어요", "차량에서 같은 계정으로 로그인하고 캡슐 동기화를 켜 주세요. 친구가 공유한 캡슐도 이곳에 모입니다.")
            }
        }
        items(filtered, key = { it.id }) { c -> MemoryCapsuleCard(c, memories[c.id] ?: CapsuleMemory(c.id), collected(c), now) { open(c.id) } }
    }
}
