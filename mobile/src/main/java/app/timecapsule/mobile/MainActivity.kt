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
import androidx.lifecycle.repeatOnLifecycle
import app.timecapsule.companion.*
import app.timecapsule.social.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TimeCapsuleMobileTheme {
                MobileApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileApp(model: MobileModel = viewModel()) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val now by produceState(System.currentTimeMillis(), lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) { value = System.currentTimeMillis(); kotlinx.coroutines.delay(1000) }
        }
    }
    CompositionLocalProvider(LocalCapsuleTime provides now) {
    val me by model.me.collectAsState()
    val capsules by model.capsules.collectAsState()
    val records by model.records.collectAsState()
    val busy by model.busy.collectAsState()
    val message by model.message.collectAsState()
    val problem by model.serverProblem.collectAsState()
    val reply by model.reply.collectAsState()
    var replyParent by rememberSaveable { mutableStateOf<String?>(null) }
    var composingReply by rememberSaveable { mutableStateOf(false) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) { model.setReplyForeground(false); model.stop() }
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) model.setReplyForeground(true)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(me?.uid) { selectedId = null; composingReply = false }
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
            if (me != null && reply != null) TextButton(onClick = { replyParent = reply?.parentId; composingReply = true }, enabled = !busy) { Text("임시 보관한 음성 답장 이어서 보내기") }
            when {
                !model.configured || model.backend == null -> SetupScreen()
                me == null -> LoginScreen(model, busy)
                selectedId != null && selected == null -> EmptyPanel("캡슐을 확인할 수 없어요", "동기화가 끝나지 않았거나 공유가 종료되었을 수 있어요.")
                selected != null -> CapsuleDetail(model, selected, busy, { selectedId = null; tab = 3 }, { replyParent = selected.id; composingReply = true })
                tab == 0 -> LibraryScreen(model, records, capsules) { selectedId = it }
                tab == 1 -> CapsuleMapScreen(capsules, { model.isCollected(it) }) { selectedId = it }
                tab == 2 -> AlbumsScreen(model, records.albums, capsules) { selectedId = it }
                else -> AccountScreen(model, busy)
            }
        }
    }
    if (composingReply && me != null) ReplyComposer(model, capsules.firstOrNull { it.id == replyParent }) { composingReply = false }
    message?.let { AlertDialog(onDismissRequest = model::dismissMessage, title = { Text("알림") }, text = { Text(it) }, confirmButton = { TextButton(onClick = model::dismissMessage) { Text("확인") } }) }
    }
}

@Composable
private fun SetupScreen() {
    EmptyPanel("추억을 연결할 준비 중이에요", "아직 서버 연결이 설정되지 않았습니다. 연결이 준비되면 차량에서 쓰는 계정으로 로그인해 캡슐을 이어서 볼 수 있어요.")
}

@Composable
private fun LoginScreen(model: MobileModel, busy: Boolean) {
    LoginContent(busy, model::login, model::register, model::resetPassword)
}

@Composable
private fun LibraryScreen(model: MobileModel, records: CompanionSnapshot, capsules: List<RemoteCapsule>, open: (String) -> Unit) {
    val profile by model.me.collectAsState()
    MemoryLibraryContent(records, capsules, profile?.uid, LocalCapsuleTime.current, open)
}

@Composable
private fun CapsuleDetail(model: MobileModel, c: RemoteCapsule, busy: Boolean, friendsScreen: () -> Unit, reply: () -> Unit) {
    val me by model.me.collectAsState()
    var sharing by remember { mutableStateOf(false) }
    var forwarding by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val records by model.records.collectAsState()
    val playing by model.playing.collectAsState()
    val photo by model.photo.collectAsState()
    val bitmap = remember(photo) { photo?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { model.savePhoto(c.id, it) } }
    var deletePhoto by remember { mutableStateOf(false) }
    LaunchedEffect(c.id) { model.loadPhoto(c.id) }
    val stored = records.memories.firstOrNull { it.capsuleId == c.id } ?: CapsuleMemory(c.id)
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) model.open(c) else permissionDenied = true
    }
    CapsuleDetailContent(c, stored, me?.uid, model.isCollected(c), records.destinations.firstOrNull { it.capsuleId == c.id },
        bitmap, playing == c.id, busy, LocalCapsuleTime.current, CapsuleDetailActions(
            listen = {
                if (playing == c.id) model.stop()
                else if (model.needsLocation(c) && !PhoneLocation(context).permitted()) locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                else model.open(c)
            },
            navigate = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${c.lat},${c.lng}(${Uri.encode(c.placeName ?: "캡슐")})"))) }.onFailure { model.notify("지도 앱을 열지 못했어요. 지도 앱을 설치한 뒤 다시 시도해 주세요.") } },
            sendToCar = { model.sendToCar(c) }, saveMemory = model::saveMemory,
            share = { sharing = true }, forward = { forwarding = true }, reply = reply,
            pickPhoto = { photoPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            deletePhoto = { deletePhoto = true },
        ))
    if (sharing || forwarding) CapsuleSharingDialog(model, c, forwarding, { sharing = false; forwarding = false }, friendsScreen)
    if (deletePhoto) AlertDialog(onDismissRequest = { deletePhoto = false }, title = { Text("사진을 삭제할까요?") }, text = { Text("원본 음성과 메모는 그대로 남아요.") }, confirmButton = { TextButton(onClick = { deletePhoto = false; model.deletePhoto(c.id) }) { Text("삭제") } }, dismissButton = { TextButton(onClick = { deletePhoto = false }) { Text("취소") } })
    DisposableEffect(c.id) { onDispose { model.stop() } }
    if (permissionDenied) AlertDialog(onDismissRequest = { permissionDenied = false }, title = { Text("정확한 위치가 필요해요") },
        text = { Text("현장 수집에만 위치를 사용해요. 설정에서 정확한 위치를 허용한 뒤 수집 버튼을 다시 눌러 주세요.") },
        confirmButton = { TextButton(onClick = { permissionDenied = false; runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }.onFailure { model.notify("설정 → 앱 → 타임캡슐 → 권한에서 위치를 허용해 주세요.") } }) { Text("설정으로 이동") } },
        dismissButton = { TextButton(onClick = { permissionDenied = false }) { Text("취소") } })
}

@Composable
private fun AlbumsScreen(model: MobileModel, albums: List<MemoryAlbum>, capsules: List<RemoteCapsule>, open: (String) -> Unit) {
    var editing by remember { mutableStateOf<MemoryAlbum?>(null) }
    var title by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var members by remember { mutableStateOf(setOf<String>()) }
    var deleting by remember { mutableStateOf<MemoryAlbum?>(null) }
    val busy by model.busy.collectAsState()
    val me by model.me.collectAsState()
    val friends by model.friends.collectAsState()
    fun edit(album: MemoryAlbum) { editing = album; title = album.title; selected = album.capsuleIds.toSet(); members = album.members.filter { it != me?.uid }.toSet() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("한 여행을 하나의 앨범으로", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { Button(onClick = { if (editing != null) editing = null else edit(MemoryAlbum("", "", emptyList())) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (editing != null) "편집 닫기" else "새 앨범 만들기") } }
        editing?.let { album ->
            item { OutlinedTextField(title, { title = it.take(100) }, label = { Text("앨범 이름") }, modifier = Modifier.fillMaxWidth()) }
            item { Text("담을 캡슐", style = MaterialTheme.typography.titleMedium) }
            items(capsules, key = { "select-${it.id}" }) { c -> Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { selected = if (c.id in selected) selected - c.id else selected + c.id }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) { Checkbox(c.id in selected, onCheckedChange = null); Text(c.placeName ?: "캡슐") } }
            item { Text("가족·친구와 함께 보기", style = MaterialTheme.typography.titleMedium) }
            item { Text("앨범 이름과 목록을 공유합니다. 음성은 각 캡슐의 공개 범위와 수집 상태를 따라요. 내 사진과 메모는 공유되지 않아요.", style = MaterialTheme.typography.bodySmall) }
            items(friends, key = { "member-${it.uid}" }) { friend -> Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { members = if (friend.uid in members) members - friend.uid else members + friend.uid }, verticalAlignment = Alignment.CenterVertically) { Checkbox(friend.uid in members, null); Text(friend.name) } }
            if (members.any { id -> friends.none { it.uid == id } }) item { TextButton(onClick = { members = members.filter { id -> friends.any { it.uid == id } }.toSet() }) { Text("연결이 끊긴 친구를 공유 대상에서 빼기") } }
            item { Button(onClick = { model.saveAlbum(album.copy(title = title, capsuleIds = selected.toList(), members = members.toList())) { editing = null } }, enabled = !busy && title.isNotBlank()) { Text("앨범 저장") } }
        }
        if (albums.isEmpty() && editing == null) item { EmptyPanel("아직 앨범이 없어요", "여행에서 모은 캡슐을 골라 나만의 앨범을 만들어 보세요.") }
        items(albums, key = { it.id }) { album ->
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(album.title, style = MaterialTheme.typography.titleLarge)
                Text("캡슐 ${album.capsuleIds.size}개 · 함께 보는 사람 ${album.members.size}명")
                album.capsuleIds.forEach { id ->
                    val c = capsules.firstOrNull { it.id == id }
                    if (c != null) TextButton(onClick = { open(id) }) { Text(c.placeName ?: "캡슐 열기") }
                    else Text("비공개이거나 공유가 종료된 캡슐", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                if (album.ownerUid == me?.uid) Row {
                    TextButton(onClick = { edit(album) }, enabled = !busy) { Text("앨범 편집·공유") }
                    TextButton(onClick = { deleting = album }, enabled = !busy) { Text("앨범 삭제") }
                }
            } }
        }
    }
    deleting?.let { album -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("앨범을 삭제할까요?") }, text = { Text("함께 보는 사람에게도 앨범이 사라져요. 원본 캡슐과 음성은 지워지지 않아요.") }, confirmButton = { TextButton(onClick = { model.deleteAlbum(album.id); deleting = null }) { Text("삭제") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("취소") } }) }
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
