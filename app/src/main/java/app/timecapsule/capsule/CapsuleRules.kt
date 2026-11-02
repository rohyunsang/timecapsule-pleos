package app.timecapsule.capsule

import app.timecapsule.data.Capsule
import app.timecapsule.data.LetterLock
import app.timecapsule.geo.Geo
import app.timecapsule.geo.GeoPoint
import java.time.LocalDate

/** Numbers from spec section 04. */
object Rules {
    const val CAPSULE_RADIUS_M = 300.0
    const val APPROACH_ALERT_M = 1_200.0
    const val MIN_RECORD_SEC = 3
    const val MAX_RECORD_SEC = 30
    const val ARRIVAL_PROMPTS_PER_DAY = 2
    const val ALERT_TIMEOUT_MS = 10_000L
}

/** F-06: a letter capsule stays closed until its open date. */
fun LetterLock?.isLocked(today: LocalDate): Boolean =
    this != null && today.toEpochDay() < openAfter

/** F-03 result: what to announce when a route starts. */
data class RouteForecast(
    /** Capsules near the route that can be opened, oldest first. */
    val open: List<Capsule>,
    /** Letter capsules near the route whose date has not come yet: existence only. */
    val lockedCount: Int,
) {
    val total get() = open.size + lockedCount

    /** One-time announcement, or null when nothing is on the way. */
    fun message(): String? = when {
        total == 0 -> null
        lockedCount == 0 -> "오늘 길에 캡슐이 ${total}개 묻혀 있어요"
        open.isEmpty() -> "오늘 길에 아직 열 수 없는 편지 캡슐이 ${lockedCount}개 있어요"
        else -> "오늘 길에 캡슐이 ${total}개 묻혀 있어요. 그중 ${lockedCount}개는 아직 열 수 없는 편지예요"
    }
}

object RouteForecaster {
    /**
     * NaviHelper does not expose the route polyline, so the route is approximated by straight
     * segments: current position -> waypoints -> destination. A capsule counts when it lies
     * within its radius (300 m) of that path.
     */
    fun forecast(
        path: List<GeoPoint>,
        capsules: List<Capsule>,
        locks: Map<Long, LetterLock>,
        today: LocalDate,
    ): RouteForecast {
        val near = capsules.filter { !it.draft && !it.locationUncertain && Geo.distanceToPathM(GeoPoint(it.lat, it.lng), path) <= it.radiusM }
        val (locked, open) = near.partition { locks[it.id].isLocked(today) }
        return RouteForecast(open.sortedBy { it.createdAt }, locked.size)
    }
}

private fun startOfDayMs(day: LocalDate) =
    day.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

data class Approach(val capsule: Capsule, val distanceM: Double)

object ApproachDetector {
    /**
     * F-04: nearest openable capsule within 1.2 km that has not been offered today.
     * Locked letter capsules never trigger an alert, and neither do my own capsules buried
     * today (driving away from the spot you just recorded at should not ping you). A friend's
     * capsule from today does: that is the point of leaving one for them.
     */
    fun next(
        here: GeoPoint,
        capsules: List<Capsule>,
        locks: Map<Long, LetterLock>,
        notifiedToday: Set<Long>,
        today: LocalDate,
    ): Approach? = capsules.asSequence()
        .filter { !it.draft && !it.locationUncertain && it.id !in notifiedToday && !locks[it.id].isLocked(today) && (!it.isMine || it.createdAt < startOfDayMs(today)) }
        .map { Approach(it, Geo.distanceM(here, GeoPoint(it.lat, it.lng))) }
        .filter { it.distanceM <= Rules.APPROACH_ALERT_M }
        .minByOrNull { it.distanceM }

    /** F-09: is there already a capsule at this spot? */
    fun hasCapsuleAt(here: GeoPoint, capsules: List<Capsule>): Boolean =
        capsules.any { Geo.distanceM(here, GeoPoint(it.lat, it.lng)) <= it.radiusM }
}
