package app.timecapsule.map

import android.content.Context
import android.graphics.RectF
import android.os.Bundle
import app.timecapsule.data.Capsule
import app.timecapsule.geo.GeoPoint
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.module.http.HttpRequestUtil
import org.maplibre.android.style.expressions.Expression.*
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.tile.TileOperation
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import java.io.File

/** Native renderer. Capsule features stay in memory; only background tiles use the network. */
class NativeCollectedMap(
    context: Context,
    savedState: Bundle,
    private val onSelect: (List<Long>) -> Unit,
    private val onUnavailable: (Boolean) -> Unit,
) {
    private val density = context.resources.displayMetrics.density
    private val mapStyle = context.assets.open("native-map/calm.json").bufferedReader().use { it.readText() }
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var disposed = false
    private var framed = savedState.getBoolean("timecapsule-framed", false)
    private var groups = emptyList<List<Capsule>>()
    private var selectedId: Long? = null
    private var current: GeoPoint? = null
    private val badges = mutableSetOf<String>()
    private var states = emptyMap<Long, CapsuleMapData.State>()
    val view: MapView

    init {
        MapNetwork.initialize(context)
        view = MapView(context, MapLibreMapOptions.createFromAttributes(context)
            .textureMode(true).rotateGesturesEnabled(false).tiltGesturesEnabled(false)
            .attributionEnabled(false))
        view.layoutParams = android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
        )
        view.onCreate(savedState)
        view.addOnDidFailLoadingMapListener { if (!disposed) onUnavailable(true) }
        view.addOnTileActionListener { operation, _, _, _, _, _, source ->
            if (!disposed && source == "openmaptiles" && operation == TileOperation.Error) {
                view.post { if (!disposed) onUnavailable(true) }
            }
        }
        view.addOnDidFinishLoadingMapListener { if (!disposed) onUnavailable(false) }
        view.getMapAsync { ready ->
            if (disposed) return@getMapAsync
            map = ready
            ready.setPrefetchZoomDelta(0)
            ready.setMinZoomPreference(2.0)
            ready.setMaxZoomPreference(19.0)
            ready.addOnMapClickListener { point ->
                val p = ready.projection.toScreenLocation(point)
                val radius = 36f * density
                val feature = ready.queryRenderedFeatures(RectF(p.x-radius,p.y-radius,p.x+radius,p.y+radius), MARKERS)
                    .minByOrNull { candidate ->
                        val location = candidate.geometry() as? Point
                        if (location == null) Float.MAX_VALUE else {
                            val screen = ready.projection.toScreenLocation(LatLng(location.latitude(), location.longitude()))
                            (screen.x-p.x)*(screen.x-p.x) + (screen.y-p.y)*(screen.y-p.y)
                        }
                    }
                val id = feature?.getStringProperty("id")?.toLongOrNull()
                val group = groups.firstOrNull { it.first().id == id }
                if (group != null) {
                    onSelect(group.map { it.id })
                    true
                } else false
            }
            ready.setStyle(Style.Builder().fromJson(mapStyle)) { loaded ->
                if (disposed) return@setStyle
                style = loaded
                loaded.addSource(GeoJsonSource(CAPSULES, FeatureCollection.fromFeatures(emptyArray<Feature>())))
                loaded.addLayer(CircleLayer("selection", CAPSULES).withProperties(
                    circleRadius(43f), circleColor("#24272B"), circleOpacity(0.18f)
                ).withFilter(eq(get("selected"), literal(true))))
                loaded.addLayer(SymbolLayer(MARKERS, CAPSULES).withProperties(
                    iconImage(get("badge")), iconAllowOverlap(true), iconIgnorePlacement(true)
                ))
                loaded.addSource(GeoJsonSource(LOCATION, FeatureCollection.fromFeatures(emptyArray<Feature>())))
                loaded.addLayer(CircleLayer("vehicle-position", LOCATION).withProperties(
                    circleRadius(7f), circleColor("#2669D9"), circleStrokeColor("#FFFFFF"), circleStrokeWidth(3f)
                ))
                render()
            }
        }
    }

    fun update(capsules: List<Capsule>, selected: Long?, position: GeoPoint?, nextStates: Map<Long, CapsuleMapData.State>) {
        val nextGroups = CapsuleMapData.groups(capsules)
        if (groups == nextGroups && selectedId == selected && current == position && states == nextStates) return
        states = nextStates
        groups = nextGroups
        selectedId = selected
        current = position
        render()
    }

    private fun render() {
        val s = style ?: return
        val features = groups.map { group ->
            val c = group.firstOrNull { it.id == selectedId } ?: group.first()
            val open = group.all { states[it.id] in setOf(CapsuleMapData.State.OPEN, CapsuleMapData.State.READY) }
            val mixed = !open && group.any { states[it.id] in setOf(CapsuleMapData.State.OPEN, CapsuleMapData.State.READY) }
            val label = if (group.map { it.ownerUid }.distinct().size > 1) "모음" else CapsuleMapData.avatar(c)
            val badge = "$label:$open:$mixed:${group.size}"
            if (badges.add(badge)) s.addImage(badge, CapsuleMapArtwork.bitmap(density, label, open, mixed, group.size))
            Feature.fromGeometry(Point.fromLngLat(c.lng, mapLatitude(c.lat))).apply {
                addStringProperty("id", group.first().id.toString())
                addStringProperty("badge", badge)
                addBooleanProperty("selected", group.any { it.id == selectedId })
            }
        }
        s.getSourceAs<GeoJsonSource>(CAPSULES)?.setGeoJson(FeatureCollection.fromFeatures(features))
        val location = current?.let { Feature.fromGeometry(Point.fromLngLat(it.lng, mapLatitude(it.lat))) }
        s.getSourceAs<GeoJsonSource>(LOCATION)?.setGeoJson(FeatureCollection.fromFeatures(listOfNotNull(location)))
        if (!framed && groups.isNotEmpty()) {
            framed = true
            showAll()
        }
    }

    fun focus(point: GeoPoint) {
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(mapLatitude(point.lat), point.lng), 15.0))
    }

    fun zoomBy(delta: Double) {
        val ready = map ?: return
        ready.animateCamera(CameraUpdateFactory.zoomTo((ready.cameraPosition.zoom + delta).coerceIn(2.0, 19.0)))
    }

    fun showAll() {
        val points = groups.map { LatLng(mapLatitude(it.first().lat), it.first().lng) }.distinct()
        if (points.size == 1) map?.moveCamera(CameraUpdateFactory.newLatLngZoom(points.first(), 14.0))
        else if (points.isNotEmpty()) map?.moveCamera(CameraUpdateFactory.newLatLngBounds(
            LatLngBounds.Builder().includes(points).build(), (160*density).toInt()
        ))
    }

    fun destroy(savedState: Bundle) {
        if (disposed) return
        view.onSaveInstanceState(savedState)
        savedState.putBoolean("timecapsule-framed", framed)
        disposed = true
        style = null
        map = null
        view.onDestroy()
    }


    companion object {
        private const val CAPSULES = "capsules"
        private const val MARKERS = "capsule-markers"
        private const val LOCATION = "vehicle"
        private fun mapLatitude(lat: Double) = lat.coerceIn(-85.05112878, 85.05112878)
    }
}

private object MapNetwork {
    private var initialized = false
    @Synchronized fun initialize(context: Context) {
        if (initialized) return
        MapLibre.getInstance(context.applicationContext)
        HttpRequestUtil.setOkHttpClient(OkHttpClient.Builder()
            .cache(Cache(File(context.applicationContext.cacheDir, "map-tiles"), 64L*1024*1024))
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder()
                    .header("User-Agent", "TimeCapsule/0.1 (+https://github.com/rohyunsang/timecapsule-pleos)")
                    .build())
            }.build())
        HttpRequestUtil.setPrintRequestUrlOnFailure(false)
        initialized = true
    }
}
