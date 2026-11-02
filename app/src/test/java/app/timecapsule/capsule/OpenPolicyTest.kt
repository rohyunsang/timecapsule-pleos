package app.timecapsule.capsule

import app.timecapsule.data.Capsule
import app.timecapsule.data.LetterLock
import app.timecapsule.geo.GeoPoint
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class OpenPolicyTest {
    private val today = LocalDate.of(2026, 11, 2)
    private val c = Capsule(createdAt = 1, lat = 37.0, lng = 127.0, placeName = "길", audioPath = "voice.m4a",
        durationSec = 10, tags = "", rawVehicleState = "{}", carModel = null, odometerKm = null)
    private fun reason(capsule: Capsule = c, lock: LetterLock? = null, p: GeoPoint? = GeoPoint(37.0,127.0),
                       at: Long = 1000) = OpenPolicy.reason(capsule, lock, today, p, at, 2000)
    @Test fun onlyFreshValidNearbyPositionOpens() {
        assertNull(reason()); assertNotNull(reason(p = null)); assertNotNull(reason(at = 0))
        assertNotNull(reason(at = 3000)); assertNotNull(reason(p = GeoPoint(38.0,127.0)))
        assertNotNull(reason(p = GeoPoint(Double.NaN,127.0)))
    }
    @Test fun collectedCapsuleReplaysWithoutLocationAfterRestart() {
        val collected = c.copy(collectedAt = 1000)
        assertNull(reason(collected, p = null, at = 0))
        assertNull(reason(collected, p = GeoPoint(0.0, 0.0)))
        assertNotNull(reason(c, p = null))
    }
    @Test fun collectionNeverBypassesDraftDateOrInvalidRecordedLocation() {
        val collected = c.copy(collectedAt = 1000)
        assertNotNull(reason(collected, lock = LetterLock(0, today.plusDays(1).toEpochDay())))
        assertNotNull(reason(collected.copy(draft = true)))
        assertNotNull(reason(collected.copy(locationUncertain = true)))
        assertNotNull(reason(collected.copy(lat = 91.0)))
    }
    @Test fun exactOpeningDayAndRadiusAreInclusive() {
        assertNull(reason(lock = LetterLock(0, today.toEpochDay())))
        assertNotNull(reason(c.copy(radiusM = 0)))
    }
    @Test fun staleSamplesAreRejected() {
        assertTrue(OpenPolicy.fresh(100,10100,10000))
        assertFalse(OpenPolicy.fresh(100,10101,10000))
    }
}
