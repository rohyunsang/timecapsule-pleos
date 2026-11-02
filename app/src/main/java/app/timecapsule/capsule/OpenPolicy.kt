package app.timecapsule.capsule

import app.timecapsule.data.Capsule
import app.timecapsule.data.LetterLock
import app.timecapsule.geo.Geo
import app.timecapsule.geo.GeoPoint
import java.time.LocalDate

/** Shared by album, alert, voice and media controls; UI visibility never grants access. */
object OpenPolicy {
    const val POSITION_MAX_AGE_MS = 10_000L
    const val VEHICLE_MAX_AGE_MS = 5_000L
    fun fresh(at: Long, now: Long, maxAge: Long) = at > 0 && now >= at && now - at <= maxAge
    fun valid(p: GeoPoint) = p.lat.isFinite() && p.lng.isFinite() && p.lat in -90.0..90.0 && p.lng in -180.0..180.0
    fun reason(c: Capsule, lock: LetterLock?, today: LocalDate, here: GeoPoint?, positionAt: Long,
               now: Long): String? = when {
        c.draft -> "임시보관한 녹음이에요. 주차 후 저장해 주세요"
        c.locationUncertain || !valid(GeoPoint(c.lat, c.lng)) || c.radiusM <= 0 -> "녹음 장소가 확인되지 않았어요"
        lock.isLocked(today) -> "아직 열리는 날짜가 되지 않았어요"
        c.collectedAt != null -> null
        here == null || !valid(here) || !fresh(positionAt, now, POSITION_MAX_AGE_MS) -> "현재 위치를 확인하고 있어요"
        Geo.distanceM(here, GeoPoint(c.lat, c.lng)) > c.radiusM -> "저장한 장소 주변에서 열 수 있어요"
        else -> null
    }
}
