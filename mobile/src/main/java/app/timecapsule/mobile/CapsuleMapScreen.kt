package app.timecapsule.mobile

import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.timecapsule.companion.CollectionPolicy
import app.timecapsule.social.RemoteCapsule
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

@Suppress("DEPRECATION")
@Composable
fun CapsuleMapScreen(capsules: List<RemoteCapsule>, collected: (RemoteCapsule) -> Boolean, open: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var failed by remember { mutableStateOf(false) }
    var showList by remember { mutableStateOf(false) }
    val currentOpen by rememberUpdatedState(open)
    val points = capsules.filter { it.sourceRemoteId == null && CollectionPolicy.valid(it.lat, it.lng) }
    val markerIds = remember { mutableMapOf<Long, String>() }
    val view = remember {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).apply {
            onCreate(Bundle())
            addOnDidFailLoadingMapListener { failed = true }
            getMapAsync { ready ->
                ready.setOnMarkerClickListener { marker -> markerIds[marker.id]?.let(currentOpen); true }
                ready.setStyle(Style.Builder().fromJson(context.assets.open("native-map/calm.json").bufferedReader().use { it.readText() })) {
                    map = ready; failed = false
                }
            }
        }
    }
    DisposableEffect(view, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> when(event) {
            Lifecycle.Event.ON_START -> view.onStart()
            Lifecycle.Event.ON_RESUME -> view.onResume()
            Lifecycle.Event.ON_PAUSE -> view.onPause()
            Lifecycle.Event.ON_STOP -> view.onStop()
            else -> Unit
        } }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) view.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.onResume()
        onDispose { lifecycle.removeObserver(observer); view.onPause(); view.onStop(); view.onDestroy() }
    }
    LaunchedEffect(map, points) {
        map?.let { ready ->
            ready.clear(); markerIds.clear()
            points.forEach { c ->
                val marker = ready.addMarker(MarkerOptions().position(LatLng(c.lat.coerceIn(-85.0,85.0), c.lng)).title(c.placeName ?: "캡슐").snippet(if (collected(c)) "수집 완료" else "현장에서 수집"))
                markerIds[marker.id] = c.id
            }
            if (points.size == 1) ready.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(points[0].lat, points[0].lng), 14.0))
            else if (points.isNotEmpty()) ready.moveCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(points.map { LatLng(it.lat.coerceIn(-85.0,85.0), it.lng) }).build(), 70))
            else ready.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(37.5665,126.978), 10.0))
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("목소리가 남아 있는 곳", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { showList = !showList }) { Text(if (showList) "지도" else "목록") }
        }
        if (failed) Text("지도를 불러오지 못했어요. 아래 목록에서 장소를 확인할 수 있어요.", Modifier.padding(16.dp))
        if (points.isEmpty()) EmptyPanel("표시할 장소가 없어요", "차량에서 캡슐을 동기화하거나 친구와 연결해 주세요.")
        else if (showList || failed) LazyColumn(Modifier.weight(1f)) { items(points, key = { it.id }) { c ->
            TextButton(onClick = { open(c.id) }, modifier = Modifier.fillMaxWidth().padding(12.dp)) { Text("${c.placeName ?: "장소"} · ${if (collected(c)) "수집 완료" else "수집 전"}") }
        } } else AndroidView(factory = { view }, modifier = Modifier.fillMaxWidth().weight(1f))
    }
}
