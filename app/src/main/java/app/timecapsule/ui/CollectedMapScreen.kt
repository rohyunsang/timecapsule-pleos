package app.timecapsule.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.timecapsule.capsule.OpenPolicy
import app.timecapsule.drive.DriveController
import app.timecapsule.geo.GeoPoint
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream

/** Trusted bundled map code only; audio and titles never leave the native app. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CollectedMapScreen(drive: DriveController, back: () -> Unit) {
    val capsules by drive.capsules.collectAsState()
    val collected = capsules.filter { it.collectedAt != null && !it.draft && !it.locationUncertain && OpenPolicy.valid(GeoPoint(it.lat,it.lng)) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    val selected = collected.firstOrNull { it.id == selectedId } ?: collected.firstOrNull()
    val context = LocalContext.current
    var ready by remember { mutableStateOf(false) }
    val web = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.domStorageEnabled = false
            settings.userAgentString += " TimeCapsule/1.0 (+https://github.com/rohyunsang/timecapsule-pleos)"
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val uri = request.url
                    if (uri.scheme == "https" && uri.host == "tile.openstreetmap.org") return null
                    if (uri.scheme == "https" && uri.host == "timecapsule.local") {
                        val name = uri.lastPathSegment
                        if (name in setOf("index.html","leaflet.js","leaflet.css","map.js")) {
                            val mime = when { name!!.endsWith(".js") -> "application/javascript"; name.endsWith(".css") -> "text/css"; else -> "text/html" }
                            return WebResourceResponse(mime,"UTF-8",context.assets.open("collected-map/$name"))
                        }
                    }
                    return WebResourceResponse("text/plain","UTF-8",ByteArrayInputStream(byteArrayOf()))
                }
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val uri = request.url
                    if (request.isForMainFrame && uri.scheme == "https" && uri.host == "timecapsule.local" && uri.path == "/select") {
                        if (drive.canEdit()) selectedId = uri.getQueryParameter("id")?.toLongOrNull()
                    } else if (request.hasGesture() && uri.toString() == "https://www.openstreetmap.org/copyright") {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                    }
                    return true
                }
                override fun onPageFinished(view: WebView, url: String) { ready = true }
            }

        }
    }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(collected.isNotEmpty()) {
        if (collected.isNotEmpty() && !loaded) { loaded = true; web.loadUrl("https://timecapsule.local/index.html") }
    }
    DisposableEffect(web) { onDispose { web.stopLoading(); web.destroy() } }
    LaunchedEffect(ready, collected, selected?.id) {
        if (ready) {
            val markers = JSONArray().apply { collected.forEach { c -> put(JSONObject().put("id",c.id.toString()).put("lat",c.lat).put("lng",c.lng)) } }
            web.evaluateJavascript("window.renderCapsules($markers, ${JSONObject.quote(selected?.id?.toString().orEmpty())});",null)
        }
    }
    Column(Modifier.fillMaxSize().padding(Tc.Gutter),verticalArrangement=Arrangement.spacedBy(Tc.Gap)) {
        Header("수집 지도",back)
        if (collected.isEmpty()) {
            Text("아직 수집한 캡슐이 없어요",style=Type.TitleS,color=Tc.Text)
            Text("현장에서 캡슐을 열면 이 지도에 남아요. 이후에는 어디서든 다시 들을 수 있어요.",style=Type.BodyS,color=Tc.TextSub)
        } else {
            AndroidView(factory={web},modifier=Modifier.fillMaxWidth().weight(1f))
            LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                items(collected,key={it.id}) { c ->
                    Btn(c.placeName ?: "이름 없는 장소",style=if(c.id==selected?.id) BtnStyle.Filled else BtnStyle.Basic) {
                        selectedId=c.id
                        if(ready) web.evaluateJavascript("window.focusCapsule(${c.lat},${c.lng});",null)
                    }
                }
            }
            selected?.let { c ->
                Text("${c.placeName ?: "이름 없는 장소"} · ${c.durationSec}초",style=Type.BodyStrong,color=Tc.Text)
                Text("수집한 캡슐 · ${c.date().format(dayFmt)}",style=Type.BodyS,color=Tc.TextSub)
                Btn("다시 듣기",Modifier.fillMaxWidth(),style=BtnStyle.Filled) { drive.playCollected(c.id) }
            }
        }
    }
}
