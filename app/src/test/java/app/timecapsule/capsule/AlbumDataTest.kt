package app.timecapsule.capsule

import app.timecapsule.data.Capsule
import app.timecapsule.data.LetterLock
import app.timecapsule.geo.GeoPoint
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AlbumDataTest {
    private val own = Capsule(createdAt = 1, lat = 37.0, lng = 127.0, placeName = "남긴 장소", audioPath = "a.wav",
        durationSec = 5, tags = "", rawVehicleState = "{}", carModel = null, odometerKm = null)
    private val friend = own.copy(ownerUid = "friend", ownerName = "친구")
    @Test fun ownCapsulesStayInOwnTabBeforeAndAfterCollection() {
        listOf(own, own.copy(collectedAt = 100), own.copy(draft = true)).forEach {
            assertTrue(AlbumData.mine(it)); assertFalse(AlbumData.collected(it))
        }
    }
    @Test fun friendsAppearOnlyAfterCollectionOrDirectDelivery() {
        assertFalse(AlbumData.collected(friend))
        assertTrue(AlbumData.collected(friend.copy(collectedAt = 100)))
        assertTrue(AlbumData.collected(friend.copy(sourceRemoteId = "original")))
        assertFalse(AlbumData.collected(friend.copy(draft = true, collectedAt = 100)))
    }
    @Test fun uncollectedOwnLocationIsNavigableWithoutUnlockingAudio() {
        assertTrue(AlbumData.canNavigate(own))
        assertTrue(AlbumData.needsCollection(own))
        val today = LocalDate.now()
        assertNotNull(OpenPolicy.reason(own, null, today, GeoPoint(0.0, 0.0), 1000, 1000))
        assertNull(OpenPolicy.reason(own, null, today, GeoPoint(37.0,127.0), 1000, 1000))
        assertNotNull(OpenPolicy.reason(own, LetterLock(0, today.plusDays(1).toEpochDay()), today, GeoPoint(37.0,127.0), 1000, 1000))
    }
    @Test fun collectedOwnAudioReplaysAnywhere() {
        val c = own.copy(collectedAt = 100)
        assertFalse(AlbumData.needsCollection(c))
        assertNull(OpenPolicy.reason(c, null, LocalDate.now(), null, 0, 1000))
    }
    @Test fun invalidAndUncollectedFriendsCannotBecomeDestinations() {
        listOf(own.copy(draft = true), own.copy(locationUncertain = true), own.copy(lat = Double.NaN),
            own.copy(radiusM = 0), friend, friend.copy(sourceRemoteId = "original", collectedAt = 100)).forEach {
            assertFalse(AlbumData.canNavigate(it))
        }
        assertTrue(AlbumData.canNavigate(friend.copy(collectedAt = 100)))
    }
}
