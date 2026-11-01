package app.timecapsule.capsule

import app.timecapsule.data.Capsule
import app.timecapsule.data.LetterLock
import app.timecapsule.geo.Geo
import app.timecapsule.geo.GeoPoint
import app.timecapsule.geo.PlaceName
import app.timecapsule.vehicle.VehicleSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CapsuleRulesTest {
    private val today = LocalDate.of(2026, 11, 1)

    // Gangneung area, roughly 111 m per 0.001 deg latitude.
    private val start = GeoPoint(37.750, 128.900)
    private val end = GeoPoint(37.800, 128.900)

    private val lastYear = today.minusYears(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun capsule(id: Long, lat: Double, lng: Double, createdAt: Long = lastYear + id) = Capsule(
        id = id, createdAt = createdAt, lat = lat, lng = lng, placeName = null,
        audioPath = "", durationSec = 5, tags = "", rawVehicleState = "{}", carModel = null, odometerKm = null,
    )

    @Test fun distanceIsAboutOneKmPerHundredthDegreeLat() {
        val d = Geo.distanceM(GeoPoint(37.0, 127.0), GeoPoint(37.01, 127.0))
        assertEquals(1112.0, d, 5.0)
    }

    @Test fun segmentDistanceMeasuresSideways() {
        // ~0.0034 deg longitude at 37.77 N is ~300 m east of the line.
        val p = GeoPoint(37.775, 128.9034)
        assertEquals(300.0, Geo.distanceToSegmentM(p, start, end), 10.0)
    }

    @Test fun forecastCountsCapsulesWithinCorridorAndHidesLocked() {
        val onRoute = capsule(1, 37.770, 128.9010, createdAt = 100)   // ~90 m off
        val older = capsule(2, 37.790, 128.9000, createdAt = 50)      // on the line
        val farAway = capsule(3, 37.770, 128.9200)                    // ~1.8 km off
        val letter = capsule(4, 37.760, 128.9000)
        val locks = mapOf(4L to LetterLock(4, openAfter = today.plusYears(1).toEpochDay()))

        val f = RouteForecaster.forecast(listOf(start, end), listOf(onRoute, older, farAway, letter), locks, today)

        assertEquals(listOf(2L, 1L), f.open.map { it.id })
        assertEquals(1, f.lockedCount)
        assertEquals("오늘 길에 캡슐이 3개 묻혀 있어요. 그중 1개는 아직 열 수 없는 편지예요", f.message())
    }

    @Test fun forecastWithNothingSaysNothing() {
        assertNull(RouteForecaster.forecast(listOf(start, end), emptyList(), emptyMap(), today).message())
    }

    @Test fun letterOpensOnItsDay() {
        val lock = LetterLock(1, openAfter = today.toEpochDay())
        assertFalse(lock.isLocked(today))
        assertTrue(lock.isLocked(today.minusDays(1)))
        assertFalse((null as LetterLock?).isLocked(today))
    }

    @Test fun approachPicksNearestUnnotifiedWithin1200m() {
        val here = start
        val near = capsule(1, 37.755, 128.900)    // ~556 m
        val nearer = capsule(2, 37.752, 128.900)  // ~222 m, already offered today
        val out = capsule(3, 37.765, 128.900)     // ~1.67 km

        val a = ApproachDetector.next(here, listOf(near, nearer, out), emptyMap(), setOf(2L), today)

        assertEquals(1L, a?.capsule?.id)
        assertEquals(556.0, a!!.distanceM, 10.0)
        assertNull(ApproachDetector.next(here, listOf(out), emptyMap(), emptySet(), today))
    }

    @Test fun capsuleBuriedTodayDoesNotAlert() {
        val now = today.atTime(9, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val fresh = capsule(1, 37.751, 128.900, createdAt = now)
        assertNull(ApproachDetector.next(start, listOf(fresh), emptyMap(), emptySet(), today))
    }

    @Test fun lockedLetterNeverAlerts() {
        val c = capsule(1, 37.751, 128.900)
        val locks = mapOf(1L to LetterLock(1, today.plusDays(3).toEpochDay()))
        assertNull(ApproachDetector.next(start, listOf(c), locks, emptySet(), today))
    }

    @Test fun placeNamePrefersRoadNames() {
        assertEquals("7번 국도", PlaceName.shorten("강원특별자치도 강릉시 7번 국도"))
        assertEquals("세종대로", PlaceName.shorten("서울특별시 종로구 세종대로 175"))
        assertEquals("영동고속도로", PlaceName.shorten("강원 평창군 영동고속도로"))
        assertEquals("주문진읍", PlaceName.shorten("강원 강릉시 주문진읍"))
        assertNull(PlaceName.shorten("  "))
        assertTrue(PlaceName.isHighway("영동고속도로 강릉방향"))
    }

    @Test fun snapshotJsonKeepsOnlyReadValues() {
        val json = VehicleSnapshot(wiperOn = true, occupiedSeats = 3, placeName = "7번 \"국도\"").toJson()
        assertEquals("""{"wiperOn":true,"occupiedSeats":3,"placeName":"7번 \"국도\""}""", json)
    }
}
