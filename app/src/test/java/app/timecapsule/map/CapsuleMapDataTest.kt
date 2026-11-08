package app.timecapsule.map

import app.timecapsule.data.Capsule
import app.timecapsule.data.LetterLock
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CapsuleMapDataTest {
    private val c = Capsule(id = 1, createdAt = 123, lat = 37.0, lng = 127.0, placeName = "장소",
        audioPath = "a.wav", durationSec = 3, tags = "", rawVehicleState = "{}", carModel = null, odometerKm = null)
    private val today = LocalDate.of(2026, 11, 4)
    @Test fun ownBuriedLocationsAppearBeforeCollection() {
        assertTrue(CapsuleMapData.eligible(c))
        assertEquals(CapsuleMapData.State.LOCKED, CapsuleMapData.state(c, null, today))
        assertFalse(CollectedMapData.eligible(c)) // still not a verified collection
    }
    @Test fun onlyCollectedFriendsAppearAndForwardedCopiesStayOffMap() {
        assertFalse(CapsuleMapData.eligible(c.copy(ownerUid = "friend")))
        assertTrue(CapsuleMapData.eligible(c.copy(ownerUid = "friend", collectedAt = 1)))
        assertFalse(CapsuleMapData.eligible(c.copy(ownerUid = "friend", collectedAt = 1, sourceRemoteId = "source")))
    }
    @Test fun draftAndInvalidPlacesNeverBecomeDestinations() {
        listOf(c.copy(draft = true), c.copy(locationUncertain = true), c.copy(lat = 91.0),
            c.copy(lng = Double.NaN), c.copy(radiusM = 0)).forEach { assertFalse(CapsuleMapData.eligible(it)) }
    }
    @Test fun dateLockTakesPrecedenceOverCollection() {
        val collected = c.copy(collectedAt = 100)
        assertEquals(CapsuleMapData.State.OPEN, CapsuleMapData.state(collected, null, today))
        assertEquals(CapsuleMapData.State.WAITING, CapsuleMapData.state(collected, LetterLock(1, today.plusDays(1).toEpochDay()), today))
        assertEquals(CapsuleMapData.State.OPEN, CapsuleMapData.state(collected, LetterLock(1, today.toEpochDay()), today))
        assertEquals(CapsuleMapData.State.LOCKED, CapsuleMapData.state(c, LetterLock(1, today.toEpochDay()), today))
    }
    @Test fun mixedStateColocatedCapsulesStayIndividuallySelectable() {
        val collected = c.copy(id = 2, collectedAt = 123)
        assertEquals(listOf(listOf(c, collected)), CapsuleMapData.groups(listOf(c, collected)))
        assertEquals(listOf(listOf(collected)), CapsuleMapData.groups(listOf(collected)))
    }
    @Test fun nameAvatarHandlesMissingNamesWhitespaceAndUnicode() {
        assertEquals("나", CapsuleMapData.avatar(c))
        assertEquals("민", CapsuleMapData.avatar(c.copy(ownerUid = "friend", ownerName = " 민수 ")))
        assertEquals("친", CapsuleMapData.avatar(c.copy(ownerUid = "friend", ownerName = " ")))
        assertEquals("😀", CapsuleMapData.avatar(c.copy(ownerUid = "friend", ownerName = "😀친구")))
    }
    @Test fun arrivalEnablesCollectionOnlyWithFreshNearbyPosition() {
        val here = app.timecapsule.geo.GeoPoint(c.lat, c.lng)
        assertEquals(CapsuleMapData.State.READY, CapsuleMapData.state(c, null, today, here, 1000, 2000))
        assertEquals(CapsuleMapData.State.LOCKED, CapsuleMapData.state(c, null, today, here, 1000, 11001))
        assertEquals(CapsuleMapData.State.LOCKED, CapsuleMapData.state(c, null, today, app.timecapsule.geo.GeoPoint(38.0, 127.0), 1000, 2000))
        assertEquals(CapsuleMapData.State.LOCKED, CapsuleMapData.state(c, null, today, null, 1000, 2000))
    }
    @Test fun arrivalCannotBypassDateLockOrUncertainLocation() {
        val here = app.timecapsule.geo.GeoPoint(c.lat, c.lng)
        assertEquals(CapsuleMapData.State.WAITING, CapsuleMapData.state(c, LetterLock(1, today.plusDays(1).toEpochDay()), today, here, 1000, 2000))
        assertEquals(CapsuleMapData.State.LOCKED, CapsuleMapData.state(c.copy(locationUncertain = true), null, today, here, 1000, 2000))
        assertEquals(CapsuleMapData.State.LOCKED, CapsuleMapData.state(c.copy(draft = true), null, today, here, 1000, 2000))
    }
    @Test fun collectedCapsuleRemainsPlayableAfterLeavingLocation() {
        assertEquals(CapsuleMapData.State.OPEN, CapsuleMapData.state(c.copy(collectedAt = 1000), null, today))
    }
}
