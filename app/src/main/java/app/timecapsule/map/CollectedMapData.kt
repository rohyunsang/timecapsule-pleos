package app.timecapsule.map

import app.timecapsule.capsule.OpenPolicy
import app.timecapsule.data.Capsule
import app.timecapsule.geo.GeoPoint
import app.timecapsule.navi.NaviPosition

/** Only verified collections may become selectable map destinations. */
object CollectedMapData {
    fun eligible(c: Capsule) = !c.isForwarded && c.collectedAt != null && !c.draft && !c.locationUncertain &&
        OpenPolicy.valid(GeoPoint(c.lat, c.lng))

    fun groups(capsules: List<Capsule>): List<List<Capsule>> = capsules.filter(::eligible)
        .groupBy { GeoPoint(it.lat, it.lng) }.values.toList()

    fun currentPoint(position: NaviPosition?, now: Long): GeoPoint? = position?.takeIf {
        OpenPolicy.valid(it.point) && OpenPolicy.fresh(it.receivedAt, now, OpenPolicy.POSITION_MAX_AGE_MS)
    }?.point
}
