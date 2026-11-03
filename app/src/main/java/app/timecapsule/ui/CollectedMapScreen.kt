package app.timecapsule.ui

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.timecapsule.drive.DriveController
import app.timecapsule.geo.GeoPoint
import app.timecapsule.map.CollectedMapData
import app.timecapsule.map.NativeCollectedMap
import kotlinx.coroutines.delay

@Composable
fun CollectedMapScreen(drive: DriveController, back: () -> Unit) {
    val capsules by drive.capsules.collectAsState()
    val position by drive.position.collectAsState()
    val collected = capsules.filter(CollectedMapData::eligible)
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var routeTarget by remember { mutableStateOf<Long?>(null) }
    val selected = collected.firstOrNull { it.id == selectedId } ?: collected.firstOrNull()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var unavailable by remember { mutableStateOf(false) }
    val savedMap = rememberSaveable { Bundle() }
    val now by produceState(SystemClock.elapsedRealtime()) {
        while (true) { value = SystemClock.elapsedRealtime(); delay(2_000) }
    }
    // A position can arrive between expiry ticks; compare it with the actual current clock.
    val current = CollectedMapData.currentPoint(position, maxOf(now, SystemClock.elapsedRealtime()))
    Column(Modifier.fillMaxSize().padding(Tc.Gutter), verticalArrangement = Arrangement.spacedBy(Tc.Gap)) {
        Header("수집 지도", back)
        if (collected.isEmpty()) {
            Text("아직 수집한 캡슐이 없어요", style=Type.TitleS, color=Tc.Text)
            Text("현장에서 캡슐을 열면 이 지도에 남아요. 이후에는 어디서든 다시 들을 수 있어요.", style=Type.BodyS, color=Tc.TextSub)
        } else {
            val native = remember(context) {
                NativeCollectedMap(context, savedMap,
                    onSelect = { if (drive.canEdit()) selectedId = it },
                    onUnavailable = { unavailable = it })
            }
            MapLifecycle(native, savedMap)
            AndroidView(factory = { native.view }, update = {
                native.update(collected, selected?.id, current)
            }, modifier = Modifier.fillMaxWidth().weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (unavailable) "지도 배경을 불러오지 못했어요. 아래 목록에서 선택해 주세요."
                    else if (current == null) "현재 위치를 확인하고 있어요 · 수집 캡슐은 다시 들을 수 있어요"
                    else "파란 점은 현재 차량 위치예요", style=Type.BodyS, color=Tc.TextSub, modifier=Modifier.weight(1f))
                Text("© OpenStreetMap contributors", style=Type.BodyS, color=Tc.TextSub,
                    modifier=Modifier.clickable { runCatching { uriHandler.openUri("https://www.openstreetmap.org/copyright") } }
                        .padding(12.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Btn("전체 보기", Modifier.weight(1f)) { if (drive.canEdit()) native.showAll() }
                Btn("현재 위치", Modifier.weight(1f), enabled=current != null) {
                    if (drive.canEdit()) current?.let(native::focus)
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(collected, key={it.id}) { c ->
                    Btn("${c.placeName ?: "이름 없는 장소"} · ${c.date().format(dayFmt)}",
                        style=if(c.id==selected?.id) BtnStyle.Filled else BtnStyle.Basic) {
                        if (drive.canEdit()) {
                            selectedId = c.id
                            native.focus(GeoPoint(c.lat, c.lng))
                        }
                    }
                }
            }
            selected?.let { c ->
                Text("${c.placeName ?: "이름 없는 장소"} · ${c.durationSec}초", style=Type.BodyStrong, color=Tc.Text)
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Btn("다시 듣기", Modifier.weight(1f), style=BtnStyle.Filled) { drive.playCollected(c.id) }
                    Btn("이 장소로 길 안내", Modifier.weight(1f)) { if (drive.canEdit()) routeTarget = c.id }
                }
            }
        }
    }
    routeTarget?.let { id ->
        val target = collected.firstOrNull { it.id == id }
        if (target != null) ConfirmPopup(
            title="이 장소로 안내할까요?",
            body="${target.placeName ?: "캡슐 지점"}을 Pleos 내비게이션 목적지로 설정해요. 기존 경로가 있다면 바뀝니다.",
            confirm="길 안내 시작",
            onConfirm={ routeTarget=null; drive.driveToCollected(id) },
            onDismiss={ routeTarget=null },
        )
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
