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
    val busy by model.busy.collectAsState()
    val me by model.me.collectAsState()
    val friends by model.friends.collectAsState()
    AlbumsContent(albums, capsules, me?.uid, friends, busy, open, model::saveAlbum, model::deleteAlbum)
}

@Composable
private fun AccountScreen(model: MobileModel, busy: Boolean) {
    val me by model.me.collectAsState()
    val friends by model.friends.collectAsState()
    val email by model.email.collectAsState()
    AccountContent(me, friends, email, busy, model::linkEmail, model::addFriend, model::removeFriend, model::logout)
}

@Composable
internal fun EmptyPanel(title: String, message: String) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Rounded.Inventory2, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(message, color = Color(0xFF666666))
    }
}
