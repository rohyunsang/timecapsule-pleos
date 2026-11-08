package app.timecapsule.ui

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.timecapsule.drive.DriveController
import app.timecapsule.map.CapsuleMapData
import app.timecapsule.map.CollectedMapData
import app.timecapsule.map.NativeCollectedMap
import kotlinx.coroutines.delay
import java.time.LocalDate

@Composable
fun CollectedMapScreen(drive: DriveController, back: () -> Unit) {
    val capsules by drive.capsules.collectAsState()
    val locks by drive.locks.collectAsState()
    val position by drive.position.collectAsState()
    val mapped = capsules.filter(CapsuleMapData::eligible)
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingId by remember { mutableStateOf<Long?>(null) }
    var pickerIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    val selected = mapped.firstOrNull { it.id == selectedId }
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var unavailable by remember { mutableStateOf(false) }
    val savedMap = rememberSaveable { Bundle() }
    val now by produceState(SystemClock.elapsedRealtime()) {
        while (true) { value = SystemClock.elapsedRealtime(); delay(2_000) }
    }
    val today = remember(now) { LocalDate.now() }
    val states = mapped.associate { it.id to CapsuleMapData.state(it, locks[it.id], today, position?.point, position?.receivedAt ?: 0L, maxOf(now, SystemClock.elapsedRealtime())) }
    val current = CollectedMapData.currentPoint(position, maxOf(now, SystemClock.elapsedRealtime()))
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Header("캡슐 지도", back) {
            if (mapped.isNotEmpty()) IconBtn(Icons.Rounded.List, "지도 캡슐 목록") {
                if (drive.canEdit()) pickerIds = mapped.map { it.id }
            }
        }
        if (mapped.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
                Icon(Icons.Rounded.Map, null, Modifier.size(64.dp), tint = Tc.TextMute)
                Text("아직 지도에 표시할 캡슐이 없어요", style = Type.TitleS, color = Tc.Text)
                Text("내가 남긴 장소와 직접 수집한 친구 캡슐이 여기에 모여요.", style = Type.BodyS, color = Tc.TextSub)
            }
        } else {
            val native = remember(context) {
                NativeCollectedMap(context, savedMap,
                    onSelect = { ids ->
                        if (drive.canEdit()) {
                            if (ids.size == 1) { selectedId = ids.first(); pendingId = ids.first() }
                            else pickerIds = ids
                        }
                    }, onUnavailable = { unavailable = it })
            }
            MapLifecycle(native, savedMap)
            Box(Modifier.fillMaxWidth().weight(1f)) {
                AndroidView(factory = { native.view }, update = {
                    native.update(mapped, selected?.id, current, states)
                }, modifier = Modifier.fillMaxSize())
                Surface(Modifier.align(Alignment.TopStart).padding(16.dp), shape = RoundedCornerShape(16.dp), color = Tc.Surface) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        MapLegend(Icons.Rounded.Lock, "잠김 ${states.values.count { it == CapsuleMapData.State.LOCKED || it == CapsuleMapData.State.WAITING }}", Color(0xFF44464E))
                        MapLegend(Icons.Rounded.PlayArrow, "듣기 가능 ${states.values.count { it == CapsuleMapData.State.OPEN || it == CapsuleMapData.State.READY }}", Color(0xFF176B53))
                    }
                }
                Column(Modifier.align(Alignment.TopEnd).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconBtn(Icons.Rounded.Add, "지도 확대", style = BtnStyle.Basic) { if (drive.canEdit()) native.zoomBy(1.0) }
                    IconBtn(Icons.Rounded.Remove, "지도 축소", style = BtnStyle.Basic) { if (drive.canEdit()) native.zoomBy(-1.0) }
                    IconBtn(Icons.Rounded.ZoomOutMap, "전체 보기", style = BtnStyle.Basic) { if (drive.canEdit()) native.showAll() }
                    Surface(onClick = clickWithSound { if (drive.canEdit()) current?.let(native::focus) },
                        enabled = current != null, modifier = Modifier.size(88.dp), shape = RoundedCornerShape(20.dp), color = Tc.Basic) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.MyLocation, if (current == null) "현재 위치 확인 중" else "현재 위치",
                                Modifier.size(36.dp), tint = if (current != null) Tc.Text else Tc.TextMute)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text("© OpenMapTiles", style = Type.LabelM, color = Tc.TextMute,
                    modifier = Modifier.clickable { runCatching { uriHandler.openUri("https://openmaptiles.org/") } }.padding(8.dp))
                Text("© OpenStreetMap", style = Type.LabelM, color = Tc.TextMute,
                    modifier = Modifier.clickable { runCatching { uriHandler.openUri("https://www.openstreetmap.org/copyright") } }.padding(8.dp))
            }
            if (unavailable) Btn("지도 배경 연결 확인 중 · 캡슐 목록 열기", Modifier.fillMaxWidth()) {
                if (drive.canEdit()) pickerIds = mapped.map { it.id }
            }
            if (selected == null) {
                Text("이름 마커를 누르면 길 안내 또는 재생을 확인해요.", style = Type.LabelM, color = Tc.TextSub,
                    modifier = Modifier.padding(bottom = 20.dp))
            } else {
                val state = states.getValue(selected.id)
                Surface(shape = RoundedCornerShape(16.dp), color = Tc.Basic) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(selected.placeName ?: "캡슐 지점", style = Type.TitleS, color = Tc.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${CapsuleMapData.owner(selected)} · ${CapsuleMapData.label(state)} · ${selected.durationSec}초", style = Type.LabelM, color = Tc.TextSub)
                            }
                            Btn(if (state == CapsuleMapData.State.READY) "수집하고 듣기" else if (state == CapsuleMapData.State.OPEN) "재생" else "길 안내",
                                icon = if (state == CapsuleMapData.State.OPEN || state == CapsuleMapData.State.READY) Icons.Rounded.PlayArrow else Icons.Rounded.Lock,
                                style = BtnStyle.Filled) { if (drive.canEdit()) pendingId = selected.id }
                            IconBtn(Icons.Rounded.Close, "선택 해제") { selectedId = null }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
    if (pickerIds.isNotEmpty()) {
        val choices = mapped.filter { it.id in pickerIds }
        Dialog(onDismissRequest = { pickerIds = emptyList() }) {
            Surface(Modifier.widthIn(max = 760.dp), shape = RoundedCornerShape(24.dp), color = Tc.Surface) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("캡슐 선택", style = Type.TitleS, color = Tc.Text)
                    LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(choices, key = { it.id }) { c ->
                            Surface(onClick = clickWithSound {
                                if (drive.canEdit()) { selectedId = c.id; pendingId = c.id; pickerIds = emptyList() }
                            }, shape = RoundedCornerShape(16.dp), color = Tc.Basic) {
                                Row(Modifier.fillMaxWidth().heightIn(min = 104.dp).padding(20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (states[c.id] == CapsuleMapData.State.OPEN) Icons.Rounded.PlayArrow else Icons.Rounded.Lock,
                                        null, Modifier.size(36.dp), tint = Tc.Text)
                                    Column(Modifier.weight(1f)) {
                                        Text(c.placeName ?: "캡슐 지점", style = Type.BodyStrong, color = Tc.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${CapsuleMapData.owner(c)} · ${CapsuleMapData.label(states.getValue(c.id))} · ${c.durationSec}초", style = Type.LabelM, color = Tc.TextSub)
                                    }
                                }
                            }
                        }
                    }
                    Btn("닫기", Modifier.fillMaxWidth()) { pickerIds = emptyList() }
                }
            }
        }
    }
    pendingId?.let { id ->
        val target = mapped.firstOrNull { it.id == id }
        if (target != null) {
            val state = states.getValue(id)
            val ready = state == CapsuleMapData.State.READY
            val open = state == CapsuleMapData.State.OPEN || ready
            val dateNote = if (state == CapsuleMapData.State.WAITING) "${LocalDate.ofEpochDay(locks.getValue(id).openAfter).format(dayFmt)}에 열려요. " else ""
            ConfirmPopup(
                title = if (ready) "이 캡슐을 수집하고 들을까요?" else if (open) "이 캡슐을 재생할까요?" else "이 장소로 안내할까요?",
                body = if (open) "${target.placeName ?: "캡슐"} · ${CapsuleMapData.owner(target)} · ${target.durationSec}초\n${if (ready) "이 장소에서 수집하면 다음부터 어디서든 들을 수 있어요." else "수집한 음성을 지금 재생해요."}"
                    else "$dateNote${target.placeName ?: "캡슐 지점"}을 내비게이션 목적지로 설정해요. 기존 경로가 있다면 바뀝니다.",
                confirm = if (ready) "수집하고 듣기" else if (open) "재생" else "길 안내 시작",
                onConfirm = { pendingId = null; if (open) drive.playCapsule(id) else drive.driveToCapsule(id) },
                onDismiss = { pendingId = null })
        }
    }
}

@Composable
private fun MapLegend(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(28.dp), tint = if (Tc.P.isDark) Tc.Text else color)
        Text(label, style = Type.LabelStrong, color = Tc.Text)
    }
}

/** Forward lifecycle and memory pressure, including when playback replaces this Compose screen. */
@Composable
private fun MapLifecycle(native: NativeCollectedMap, savedState: Bundle) {
    val owner = LocalLifecycleOwner.current
    val appContext = LocalContext.current.applicationContext
    DisposableEffect(native, owner) {
        var started = false
        var resumed = false
        fun pause() { if (resumed) { native.view.onPause(); resumed=false } }
        fun stop() { pause(); if (started) { native.view.onStop(); started=false } }
        val observer = LifecycleEventObserver { _, event ->
            when(event) {
                Lifecycle.Event.ON_START -> { native.view.onStart(); started=true }
                Lifecycle.Event.ON_RESUME -> { native.view.onResume(); resumed=true }
                Lifecycle.Event.ON_PAUSE -> pause()
                Lifecycle.Event.ON_STOP -> stop()
                else -> Unit
            }
        }
        val memory = object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            override fun onLowMemory() = native.view.onLowMemory()
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) native.view.onLowMemory()
            }
        }
        owner.lifecycle.addObserver(observer)
        appContext.registerComponentCallbacks(memory)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            appContext.unregisterComponentCallbacks(memory)
            stop()
            native.destroy(savedState)
        }
    }
}
