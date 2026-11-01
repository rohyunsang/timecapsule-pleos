package app.timecapsule.geo

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoPoint(val lat: Double, val lng: Double)

object Geo {
    private const val EARTH_RADIUS_M = 6_371_000.0

    /** Great-circle distance in meters. */
    fun distanceM(a: GeoPoint, b: GeoPoint): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(h)))
    }

    /**
     * Distance from [p] to the segment [a]-[b] in meters. Uses a local flat projection,
     * which is accurate enough for the few-kilometer segments we compare against 300 m.
     */
    fun distanceToSegmentM(p: GeoPoint, a: GeoPoint, b: GeoPoint): Double {
        val k = cos(Math.toRadians(p.lat))
        fun x(g: GeoPoint) = Math.toRadians(g.lng) * k * EARTH_RADIUS_M
        fun y(g: GeoPoint) = Math.toRadians(g.lat) * EARTH_RADIUS_M
        val (px, py) = x(p) to y(p)
        val (ax, ay) = x(a) to y(a)
        val (bx, by) = x(b) to y(b)
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / len2))
        val cx = ax + t * dx - px
        val cy = ay + t * dy - py
        return sqrt(cx * cx + cy * cy)
    }

    /** Shortest distance from [p] to a polyline; a single point counts as a degenerate line. */
    fun distanceToPathM(p: GeoPoint, path: List<GeoPoint>): Double = when (path.size) {
        0 -> Double.MAX_VALUE
        1 -> distanceM(p, path[0])
        else -> path.zipWithNext { a, b -> distanceToSegmentM(p, a, b) }.min()
    }
}

/**
 * Turns a NaviHelper address into a short place tag, e.g.
 * "강원특별자치도 강릉시 7번 국도" -> "7번 국도", "서울특별시 종로구 세종대로 175" -> "세종대로".
 */
object PlaceName {
    private val road = Regex("""(\d+번\s?국도|\S+고속도로|\S+(대로|로|길))""")

    fun shorten(address: String?): String? {
        val a = address?.trim().orEmpty()
        if (a.isEmpty()) return null
        road.findAll(a).lastOrNull()?.let { return it.value }
        val parts = a.split(' ').filter { it.isNotBlank() }
        return parts.lastOrNull { it.endsWith("동") || it.endsWith("읍") || it.endsWith("면") }
            ?: parts.lastOrNull { it.endsWith("구") || it.endsWith("시") || it.endsWith("군") }
            ?: parts.last()
    }

    fun isHighway(address: String?): Boolean = address?.contains("고속도로") == true
}
