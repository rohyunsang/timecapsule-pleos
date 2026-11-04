package app.timecapsule.capsule

import app.timecapsule.data.Capsule
import app.timecapsule.geo.GeoPoint

/** Album ownership and collection are independent: my collected capsules stay in my tab. */
object AlbumData {
    fun mine(c: Capsule) = c.isMine
    fun collected(c: Capsule) = !c.isMine && !c.draft && (c.collectedAt != null || c.isForwarded)
    fun needsCollection(c: Capsule) = !c.draft && !c.canReplayAnywhere
    fun canNavigate(c: Capsule) = !c.draft && !c.locationUncertain && !c.isForwarded &&
        (c.isMine || c.collectedAt != null) && OpenPolicy.valid(GeoPoint(c.lat, c.lng)) && c.radiusM > 0
}
