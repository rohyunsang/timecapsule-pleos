package io.timecapsule.vehicle

import kotlin.math.*

/** Pure decisions. Missing, stale or invalid observations never unlock a capsule. */
object CapsulePolicy {
    const val RADIUS_METERS = 100.0
    fun fresh(timestamp: Long, now: Long, maxAge: Long = 10_000) =
        timestamp > 0 && now >= timestamp && now - timestamp <= maxAge
    fun coordinate(lat: Double, lon: Double) =
        lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0
    fun distance(a: Double, b: Double, c: Double, d: Double): Double {
        if (!coordinate(a,b) || !coordinate(c,d)) return Double.POSITIVE_INFINITY
        val lat = Math.toRadians(c-a); val lon = Math.toRadians(d-b)
        val h = sin(lat/2).pow(2) + cos(Math.toRadians(a))*cos(Math.toRadians(c))*sin(lon/2).pow(2)
        return 6_371_000 * 2 * asin(sqrt(h.coerceIn(0.0,1.0)))
    }
    fun canOpen(distance: Double, locationFresh: Boolean, unlockAt: Long, now: Long) =
        locationFresh && distance.isFinite() && distance >= 0 && distance <= RADIUS_METERS && now >= unlockAt
    fun canRecord(prepared: Boolean, vehicleStateKnown: Boolean, locationFresh: Boolean, mic: Boolean) =
        prepared && vehicleStateKnown && locationFresh && mic
}
