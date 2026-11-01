package app.timecapsule.social

import app.timecapsule.capsule.ApproachDetector
import app.timecapsule.data.Capsule
import app.timecapsule.geo.GeoPoint
import app.timecapsule.tag.MoodTagEngine
import app.timecapsule.tag.Narration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class SocialTest {
    @Test fun friendCodesUseReadableCharactersOnly() {
        repeat(200) {
            val c = FriendCode.random()
            assertEquals(6, c.length)
            assertTrue(c, c.none { it in "01OI" })
        }
    }

    @Test fun codeInputIsForgiving() {
        assertEquals("MNSU27", FriendCode.normalize("mnsu 27"))
        assertEquals("MNSU27", FriendCode.normalize("MNS-U27"))
        assertNull(FriendCode.normalize("MNSU2"))      // too short
        assertNull(FriendCode.normalize("MNSU20"))     // 0 is never in a code
    }

    @Test fun qrLinkRoundTrips() {
        assertEquals("JYNG38", FriendCode.fromLink(FriendCode.link("JYNG38")))
        assertNull(FriendCode.fromLink("timecapsule://friend"))
    }

    @Test fun friendNarrationSaysWhoLeftIt() {
        val tags = MoodTagEngine.fromStored("밤길|둘이서")
        val text = Narration.build(LocalDate.of(2023, 5, 1), LocalDate.of(2026, 11, 2), tags, from = "민수")
        assertEquals("민수님이 3년 전 남긴 캡슐이에요.", text)
        assertEquals("지영님이 오늘 남긴 캡슐이에요.", Narration.build(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 2), emptyList(), from = "지영"))
    }

    @Test fun aFriendsCapsuleFromTodayStillAlerts() {
        val today = LocalDate.of(2026, 11, 2)
        val now = today.atTime(15, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        fun c(id: Long, owner: String?) = Capsule(
            id = id, createdAt = now, lat = 37.755, lng = 128.9, placeName = null, audioPath = "", durationSec = 5,
            tags = "", rawVehicleState = "{}", carModel = null, odometerKm = null, ownerUid = owner, ownerName = owner,
        )
        val here = GeoPoint(37.750, 128.9)
        assertNull(ApproachDetector.next(here, listOf(c(1, null)), emptyMap(), emptySet(), today))
        assertNotNull(ApproachDetector.next(here, listOf(c(2, "민수")), emptyMap(), emptySet(), today))
    }
}
