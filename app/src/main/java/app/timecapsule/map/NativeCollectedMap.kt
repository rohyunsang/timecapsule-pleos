package app.timecapsule.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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
    private val onSelect: (Long) -> Unit,
    private val onUnavailable: (Boolean) -> Unit,
) {
    private val density = context.resources.displayMetrics.density
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var disposed = false
    private var framed = savedState.getBoolean("timecapsule-framed", false)
    private var groups = emptyList<List<Capsule>>()
    private var selectedId: Long? = null
    private var current: GeoPoint? = null
    private val badges = mutableSetOf<Int>()
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
            if (!disposed && source == "osm" && operation == TileOperation.Error) {
                view.post { if (!disposed) onUnavailable(true) }
            }
        }
        view.addOnDidFinishLoadingMapListener { if (!disposed) onUnavailable(false) }
        view.getMapAsync { ready ->
            if (disposed) return@getMapAsync
            map = ready
            ready.setPrefetchZoomDelta(0)
            ready.setMaxZoomPreference(19.0)
            ready.addOnMapClickListener { point ->
                val p = ready.projection.toScreenLocation(point)
                val radius = 24f * density
                val feature = ready.queryRenderedFeatures(RectF(p.x-radius,p.y-radius,p.x+radius,p.y+radius), MARKERS)
                    .firstOrNull()
                val id = feature?.getStringProperty("id")?.toLongOrNull()
                val group = groups.firstOrNull { it.first().id == id }
                if (group != null) {
                    // Repeated taps cycle capsules at an identical location; the list remains accessible.
                    val index = group.indexOfFirst { it.id == selectedId }
                    onSelect(group[(index + 1) % group.size].id)
                    true
                } else false
            }
            ready.setStyle(Style.Builder().fromJson(STYLE)) { loaded ->
                if (disposed) return@setStyle
                style = loaded
                loaded.addSource(GeoJsonSource(CAPSULES, FeatureCollection.fromFeatures(emptyArray<Feature>())))
                loaded.addLayer(CircleLayer("selection", CAPSULES).withProperties(
                    circleRadius(27f), circleColor("#3B6FCD"), circleOpacity(0.3f)
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

    fun update(capsules: List<Capsule>, selected: Long?, position: GeoPoint?) {
        val nextGroups = CollectedMapData.groups(capsules)
        if (groups == nextGroups && selectedId == selected && current == position) return
        groups = nextGroups
        selectedId = selected
        current = position
        render()
    }

    private fun render() {
        val s = style ?: return
        groups.map { it.size }.distinct().filter { badges.add(it) }.forEach { count ->
            s.addImage("count-$count", badge(count))
        }
        val features = groups.map { group ->
            val c = group.first()
            Feature.fromGeometry(Point.fromLngLat(c.lng, mapLatitude(c.lat))).apply {
                addStringProperty("id", c.id.toString())
                addStringProperty("badge", "count-${group.size}")
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

    fun showAll() {
        val points = groups.map { LatLng(mapLatitude(it.first().lat), it.first().lng) }.distinct()
        if (points.size == 1) map?.moveCamera(CameraUpdateFactory.newLatLngZoom(points.first(), 14.0))
        else if (points.isNotEmpty()) map?.moveCamera(CameraUpdateFactory.newLatLngBounds(
            LatLngBounds.Builder().includes(points).build(), (48*density).toInt()
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

    private fun badge(count: Int): Bitmap {
        val size = (64 * density).toInt()
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.density = (160 * density).toInt()
            val canvas = Canvas(bitmap)
            canvas.scale(density, density)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val shell = RectF(8f, 19f, 56f, 45f)
            val shape = Path().apply { addRoundRect(shell, 13f, 13f, Path.Direction.CW) }

            canvas.save()
            canvas.rotate(-35f, 32f, 32f)
            // A white silhouette keeps the capsule legible over detailed map tiles.
            paint.color = Color.WHITE
            canvas.drawRoundRect(RectF(5f, 16f, 59f, 48f), 16f, 16f, paint)
            canvas.save()
            canvas.clipPath(shape)
            paint.color = Color.rgb(241, 235, 220)
            canvas.drawRect(shell, paint)
            paint.color = Color.rgb(66, 71, 82)
            canvas.drawRect(8f, 19f, 32f, 45f, paint)
            paint.color = Color.argb(55, 255, 255, 255)
            canvas.drawRoundRect(RectF(12f, 22f, 52f, 25f), 1.5f, 1.5f, paint)
            // The seam and short waveform suggest a container holding a voice.
            paint.color = Color.rgb(143, 145, 148)
            paint.strokeWidth = 1.2f
            canvas.drawLine(32f, 19f, 32f, 45f, paint)
            paint.color = Color.WHITE
            paint.strokeWidth = 2f
            paint.strokeCap = Paint.Cap.ROUND
            for ((x, height) in listOf(17f to 4f, 22f to 9f, 27f to 5f)) {
                canvas.drawLine(x, 33f - height / 2, x, 33f + height / 2, paint)
            }
            canvas.restore()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.2f
            paint.color = Color.rgb(66, 71, 82)
            canvas.drawRoundRect(shell, 13f, 13f, paint)
            canvas.restore()

            // A single capsule needs no number; stacked collections retain their count.
            if (count > 1) {
                paint.style = Paint.Style.FILL
                paint.color = Color.WHITE
                canvas.drawCircle(51f, 13f, 12f, paint)
                paint.color = Color.rgb(66, 71, 82)
                canvas.drawCircle(51f, 13f, 10f, paint)
                paint.color = Color.WHITE
                paint.textSize = if (count > 99) 9f else 12f
                paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(if (count > 99) "99+" else count.toString(), 51f,
                    13f - (paint.descent() + paint.ascent()) / 2, paint)
            }
        }
    }

    companion object {
        private const val CAPSULES = "capsules"
        private const val MARKERS = "capsule-markers"
        private const val LOCATION = "vehicle"
        private fun mapLatitude(lat: Double) = lat.coerceIn(-85.05112878, 85.05112878)
        private const val STYLE = """{"version":8,"sources":{"osm":{"type":"raster","tiles":["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],"tileSize":256,"maxzoom":19,"attribution":"© OpenStreetMap contributors"}},"layers":[{"id":"background","type":"background","paint":{"background-color":"#ECECE8"}},{"id":"osm","type":"raster","source":"osm"}]}"""
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
