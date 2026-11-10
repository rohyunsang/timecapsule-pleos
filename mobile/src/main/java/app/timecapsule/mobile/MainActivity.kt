package app.timecapsule.mobile

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.timecapsule.companion.*
import app.timecapsule.social.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFFD92D3A), secondary = Color(0xFF515151),
                background = Color(0xFFF5F5F6), surface = Color.White, onSurface = Color(0xFF222222))) {
                MobileApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileApp(model: MobileModel = viewModel()) {
    val me by model.me.collectAsState()
    val capsules by model.capsules.collectAsState()
    val records by model.records.collectAsState()
    val busy by model.busy.collectAsState()
    val message by model.message.collectAsState()
    val problem by model.serverProblem.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = capsules.firstOrNull { it.id == selectedId }
    val tabs = listOf("추억", "지도", "앨범", "친구·설정")
    val icons = listOf(Icons.Rounded.Home, Icons.Rounded.Map, Icons.Rounded.PhotoLibrary, Icons.Rounded.People)
    BackHandler(selectedId != null) { model.stop(); selectedId = null }
    Scaffold(
        topBar = { TopAppBar(title = { Text(if (selected != null) "캡슐의 기억" else "타임캡슐", fontWeight = FontWeight.Bold) },
            navigationIcon = { if (selectedId != null) IconButton(onClick = { model.stop(); selectedId = null }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "뒤로") } }) },
        bottomBar = { if (me != null && selectedId == null) NavigationBar(containerColor = Color.White) {
            tabs.forEachIndexed { index, title -> NavigationBarItem(selected = tab == index, onClick = { model.stop(); tab = index }, icon = { Icon(icons[index], title) }, label = { Text(title) }) }
        } },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (problem != null || records.error != null) Text(problem ?: records.error.orEmpty(), Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer).padding(16.dp))
            when {
                !model.configured || model.backend == null -> SetupScreen()
                me == null -> LoginScreen(model, busy)
                selectedId != null && selected == null -> EmptyPanel("캡슐을 확인할 수 없어요", "동기화가 끝나지 않았거나 공유가 종료되었을 수 있어요.")
                selected != null -> CapsuleDetail(model, selected, busy)
                tab == 0 -> LibraryScreen(model, records, capsules) { selectedId = it }
                tab == 1 -> CapsuleMapScreen(capsules, { model.isCollected(it) }) { selectedId = it }
                tab == 2 -> AlbumsScreen(model, records.albums, capsules) { selectedId = it }
                else -> AccountScreen(model, busy)
            }
        }
    }
    message?.let { AlertDialog(onDismissRequest = model::dismissMessage, title = { Text("알림") }, text = { Text(it) }, confirmButton = { TextButton(onClick = model::dismissMessage) { Text("확인") } }) }
}

@Composable
private fun SetupScreen() {
    EmptyPanel("추억을 연결할 준비 중이에요", "아직 서버 연결이 설정되지 않았습니다. 연결이 준비되면 차량에서 쓰는 계정으로 로그인해 캡슐을 이어서 볼 수 있어요.")
}

@Composable
private fun LoginScreen(model: MobileModel, busy: Boolean) {
    var registering by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("그 길의 목소리,\n이제 손안에서도", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold) }
        item { Text("차량과 같은 계정으로 연결하세요. 장소에서 수집한 캡슐은 어디서든 다시 들을 수 있어요.", color = Color(0xFF666666)) }
        if (registering) item { OutlinedTextField(name, { name = it.take(12) }, label = { Text("이름") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { OutlinedTextField(email, { email = it }, label = { Text("이메일") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { OutlinedTextField(password, { password = it }, label = { Text("비밀번호") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { Button(onClick = { if (registering) model.register(name, email, password) else model.login(email, password) }, enabled = !busy && email.contains('@') && password.length >= 6, modifier = Modifier.fillMaxWidth()) { Text(if (registering) "계정 만들기" else "차량 계정으로 로그인") } }
        item { TextButton(onClick = { registering = !registering }, enabled = !busy) { Text(if (registering) "이미 계정이 있어요" else "처음이에요 · 계정 만들기") } }
        item { TextButton(onClick = { model.resetPassword(email) }, enabled = !busy && email.contains('@')) { Text("비밀번호를 잊었어요") } }
    }
}

@Composable
private fun LibraryScreen(model: MobileModel, records: CompanionSnapshot, capsules: List<RemoteCapsule>, open: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val uid by model.me.collectAsState()
    val filtered = capsules.filter { c ->
        val memory = records.memories.firstOrNull { it.capsuleId == c.id }
        val matches = listOf(c.placeName, c.ownerName, memory?.title, memory?.note).any { it?.contains(query, true) == true }
        matches && when (filter) { 1 -> c.ownerUid == uid?.uid; 2 -> model.isCollected(c); 3 -> memory?.visitLater == true; else -> true }
    }.sortedByDescending { it.createdAt }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("차곡차곡 쌓인 목소리", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { Text("수집한 추억 ${records.collections.size}개 · 캡슐 ${capsules.size}개", color = Color.Gray) }
        item { OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text("장소, 사람, 메모 검색") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("전체", "내 캡슐", "수집", "갈 곳").forEachIndexed { i, text -> FilterChip(filter == i, onClick = { filter = i }, label = { Text(text) }) } } }
        if (records.loading && capsules.isEmpty()) item { Text("계정의 기록을 불러오고 있어요. 연결 상태에 따라 잠시 걸릴 수 있어요.") }
        if (filtered.isEmpty()) item { EmptyPanel(if (query.isNotBlank()) "검색 결과가 없어요" else "아직 모인 캡슐이 없어요", "차량에서 계정을 연결하고 캡슐 동기화를 켜 주세요. 친구가 공유한 캡슐도 이곳에 모입니다.") }
        items(filtered, key = { it.id }) { CapsuleCard(model, it) { open(it.id) } }
    }
}

@Composable
private fun CapsuleCard(model: MobileModel, c: RemoteCapsule, open: () -> Unit) {
    val collected = model.isCollected(c)
    val dateLocked = c.openAfter?.let { it > LocalDate.now().toEpochDay() } == true
    Card(onClick = open, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(if (dateLocked || !collected) Icons.Rounded.Lock else Icons.Rounded.PlayCircle, null, Modifier.size(36.dp), tint = if (collected && !dateLocked) MaterialTheme.colorScheme.primary else Color.Gray)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(model.memory(c.id).title.ifBlank { c.placeName ?: "이름 없는 장소" }, fontWeight = FontWeight.Bold)
                Text("${c.originalOwnerName ?: c.ownerName} · ${c.durationSec}초", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Text(if (dateLocked) "${LocalDate.ofEpochDay(c.openAfter!!)} 개봉" else if (collected) "어디서든 듣기" else "장소에서 수집하기", style = MaterialTheme.typography.labelMedium)
            }
            Icon(Icons.Rounded.ChevronRight, "상세 보기")
        }
    }
}

@Composable
private fun CapsuleDetail(model: MobileModel, c: RemoteCapsule, busy: Boolean) {
    val context = LocalContext.current
    val records by model.records.collectAsState()
    val playing by model.playing.collectAsState()
    val photo by model.photo.collectAsState()
    val bitmap = remember(photo) { photo?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { model.savePhoto(c.id, it) } }
    var deletePhoto by remember { mutableStateOf(false) }
    LaunchedEffect(c.id) { model.loadPhoto(c.id) }
    val stored = records.memories.firstOrNull { it.capsuleId == c.id } ?: CapsuleMemory(c.id)
    var title by rememberSaveable(c.id, stored.title) { mutableStateOf(stored.title) }
    var note by rememberSaveable(c.id, stored.note) { mutableStateOf(stored.note) }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) model.open(c) else permissionDenied = true
    }
    val lockedDate = c.openAfter?.let { it > LocalDate.now().toEpochDay() } == true
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(c.placeName ?: "캡슐을 남긴 장소", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item { Text("${c.originalOwnerName ?: c.ownerName}의 목소리 · ${c.durationSec}초") }
        item { Text(if (lockedDate) "${LocalDate.ofEpochDay(c.openAfter!!).format(DateTimeFormatter.ofPattern("yyyy년 M월 d일"))}부터 열 수 있어요." else if (model.isCollected(c)) "수집한 목소리예요. 어디서든 다시 들을 수 있어요." else "이 장소에서 수집해 주세요. 수집하면 다음부터 어디서든 들을 수 있어요.") }
        item { Button(onClick = {
            if (playing == c.id) model.stop()
            else if (model.needsLocation(c) && !PhoneLocation(context).permitted()) locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            else model.open(c)
        }, enabled = !busy && !lockedDate, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Icon(if (playing == c.id) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null)
            Spacer(Modifier.width(8.dp)); Text(if (playing == c.id) "재생 정지" else if (model.isCollected(c)) "목소리 듣기" else "여기서 수집하고 듣기")
        } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${c.lat},${c.lng}(${Uri.encode(c.placeName ?: "캡슐")})"))) }.onFailure { model.notify("지도 앱을 열지 못했어요. 지도 앱을 설치한 뒤 다시 시도해 주세요.") } }, Modifier.weight(1f)) { Text("길 찾기") }
            OutlinedButton(onClick = { model.sendToCar(c) }, Modifier.weight(1f), enabled = !busy) { Text("차량으로 보내기") }
        } }
        item { OutlinedButton(onClick = { model.saveMemory(stored.copy(visitLater = !stored.visitLater)) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (stored.visitLater) "방문할 장소에서 빼기" else "방문할 장소에 저장") } }
        item { HorizontalDivider() }
        item { Text("나의 기록", style = MaterialTheme.typography.titleLarge) }
        item { OutlinedButton(onClick = { model.saveMemory(stored.copy(favorite = !stored.favorite)) }, enabled = !busy) { Text(if (stored.favorite) "즐겨찾기 해제" else "즐겨찾기") } }
        bitmap?.let { image -> item { Image(image, "이 캡슐에 추가한 내 사진", Modifier.fillMaxWidth().height(220.dp)) } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { photoPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy) { Text(if (bitmap == null) "사진 추가" else "사진 바꾸기") }
            if (bitmap != null) TextButton(onClick = { deletePhoto = true }, enabled = !busy) { Text("사진 삭제") }
        } }
        item { OutlinedTextField(title, { title = it.take(120) }, label = { Text("추억의 제목") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(note, { note = it.take(4000) }, label = { Text("직접 남기는 메모") }, modifier = Modifier.fillMaxWidth(), minLines = 3) }
        item { Button(onClick = { model.saveMemory(stored.copy(title = title, note = note)) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("기록 저장") } }
    }
    if (deletePhoto) AlertDialog(onDismissRequest = { deletePhoto = false }, title = { Text("사진을 삭제할까요?") }, text = { Text("원본 음성과 메모는 그대로 남아요.") }, confirmButton = { TextButton(onClick = { deletePhoto = false; model.deletePhoto(c.id) }) { Text("삭제") } }, dismissButton = { TextButton(onClick = { deletePhoto = false }) { Text("취소") } })
    DisposableEffect(c.id) { onDispose { model.stop() } }
    if (permissionDenied) AlertDialog(onDismissRequest = { permissionDenied = false }, title = { Text("정확한 위치가 필요해요") },
        text = { Text("현장 수집에만 위치를 사용해요. 설정에서 정확한 위치를 허용한 뒤 수집 버튼을 다시 눌러 주세요.") },
        confirmButton = { TextButton(onClick = { permissionDenied = false; runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }.onFailure { model.notify("설정 → 앱 → 타임캡슐 → 권한에서 위치를 허용해 주세요.") } }) { Text("설정으로 이동") } },
        dismissButton = { TextButton(onClick = { permissionDenied = false }) { Text("취소") } })
}

@Composable
private fun AlbumsScreen(model: MobileModel, albums: List<MemoryAlbum>, capsules: List<RemoteCapsule>, open: (String) -> Unit) {
    var creating by remember { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var deleting by remember { mutableStateOf<MemoryAlbum?>(null) }
    val busy by model.busy.collectAsState()
    val me by model.me.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("한 여행을 하나의 앨범으로", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { Button(onClick = { creating = !creating }, modifier = Modifier.fillMaxWidth()) { Text(if (creating) "앨범 만들기 닫기" else "새 앨범 만들기") } }
        if (creating) {
            item { OutlinedTextField(title, { title = it.take(100) }, label = { Text("앨범 이름") }, modifier = Modifier.fillMaxWidth()) }
            items(capsules, key = { "select-${it.id}" }) { c -> Row(Modifier.fillMaxWidth().clickable { selected = if (c.id in selected) selected - c.id else selected + c.id }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) { Checkbox(c.id in selected, onCheckedChange = null); Text(c.placeName ?: "캡슐") } }
            item { Button(onClick = { model.saveAlbum(MemoryAlbum("", title, selected.toList())); creating = false }, enabled = !busy && title.isNotBlank()) { Text("앨범 저장") } }
        }
        if (albums.isEmpty() && !creating) item { EmptyPanel("아직 앨범이 없어요", "여행에서 모은 캡슐을 골라 나만의 앨범을 만들어 보세요.") }
        items(albums, key = { it.id }) { album ->
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(album.title, style = MaterialTheme.typography.titleLarge)
                Text("캡슐 ${album.capsuleIds.size}개 · 함께 보는 사람 ${album.members.size}명")
                album.capsuleIds.forEach { id -> capsules.firstOrNull { it.id == id }?.let { c -> TextButton(onClick = { open(id) }) { Text(c.placeName ?: "캡슐 열기") } } }
                if (album.ownerUid == me?.uid) TextButton(onClick = { deleting = album }) { Text("앨범 삭제") }
            } }
        }
    }
    deleting?.let { album -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("앨범을 삭제할까요?") }, text = { Text("원본 캡슐과 음성은 지워지지 않아요.") }, confirmButton = { TextButton(onClick = { model.deleteAlbum(album.id); deleting = null }) { Text("삭제") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("취소") } }) }
}

@Composable
private fun AccountScreen(model: MobileModel, busy: Boolean) {
    val me by model.me.collectAsState()
    val friends by model.friends.collectAsState()
    val email by model.email.collectAsState()
    var code by rememberSaveable { mutableStateOf("") }
    var linkEmail by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loggingOut by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("${me?.name}님의 타임캡슐", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { Text(email ?: "이메일을 연결하면 차량에서도 같은 기록을 볼 수 있어요.") }
        if (email == null) {
            item { OutlinedTextField(linkEmail, { linkEmail = it }, label = { Text("연결할 이메일") }) }
            item { OutlinedTextField(password, { password = it }, label = { Text("비밀번호") }, visualTransformation = PasswordVisualTransformation()) }
            item { Button(onClick = { model.linkEmail(linkEmail, password) }, enabled = !busy && linkEmail.contains('@') && password.length >= 6) { Text("이메일 연결") } }
        }
        item { Text("내 친구 코드  ${me?.code}", style = MaterialTheme.typography.titleLarge) }
        item { OutlinedTextField(code, { code = it.uppercase().take(8) }, label = { Text("친구 코드") }, modifier = Modifier.fillMaxWidth()) }
        item { Button(onClick = { model.addFriend(code) }, enabled = !busy && FriendCode.normalize(code) != null) { Text("친구 추가") } }
        items(friends, key = { it.uid }) { friend -> ListItem(headlineContent = { Text(friend.name) }, supportingContent = { Text(friend.code) }) }
        item { HorizontalDivider() }
        item { Text("차량 연결", style = MaterialTheme.typography.titleLarge) }
        item { Text("차량의 친구 화면에서 같은 이메일로 로그인하세요. 비공개 캡슐은 차량에서 동기화를 켜야 모바일로 가져옵니다. 수집 기록과 방문할 장소는 같은 계정에 저장됩니다.") }
        item { OutlinedButton(onClick = { loggingOut = true }, enabled = !busy) { Text("로그아웃") } }
    }
    if (loggingOut) AlertDialog(onDismissRequest = { loggingOut = false }, title = { Text("로그아웃할까요?") }, text = { Text("서버의 기록은 그대로 보관됩니다.") }, confirmButton = { TextButton(onClick = { loggingOut = false; model.logout() }) { Text("로그아웃") } }, dismissButton = { TextButton(onClick = { loggingOut = false }) { Text("취소") } })
}

@Composable
internal fun EmptyPanel(title: String, message: String) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Rounded.Inventory2, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(message, color = Color(0xFF666666))
    }
}
