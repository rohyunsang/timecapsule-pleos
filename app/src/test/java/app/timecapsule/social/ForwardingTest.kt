package app.timecapsule.social

import app.timecapsule.capsule.ApproachDetector
import app.timecapsule.capsule.OpenPolicy
import app.timecapsule.data.Capsule
import app.timecapsule.data.LetterLock
import app.timecapsule.geo.GeoPoint
import app.timecapsule.map.CollectedMapData
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ForwardingTest {
    private val source = Capsule(createdAt = 123, lat = 37.0, lng = 127.0, placeName = "길",
        audioPath = "voice.wav", durationSec = 3, tags = "밤길", rawVehicleState = "{\"carModel\":\"secret\",\"odometerKm\":123}",
        carModel = null, odometerKm = null, ownerUid = "author", ownerName = "원작자", remoteId = "original", collectedAt = 1000)
    private val sender = Profile("sender", "전달자", "SEND27")
    private val friend = Friend("recipient", "받는 친구", "RECV27")
    private val today = LocalDate.of(2026, 11, 4)
    private fun received() = source.copy(ownerUid = sender.uid, ownerName = sender.name,
        sourceRemoteId = "original", originalOwnerUid = "author", originalOwnerName = "원작자", collectedAt = null)

    @Test fun onlyCollectedRoadCapsulesFromOthersCanBeForwarded() {
        assertTrue(Forwarding.eligible(source))
        listOf(source.copy(ownerUid = null), source.copy(collectedAt = null), source.copy(remoteId = null),
            source.copy(remoteId = ""), source.copy(draft = true), source.copy(locationUncertain = true),
            received().copy(collectedAt = 1000)).forEach { assertFalse(Forwarding.eligible(it)) }
    }
    @Test fun deliveryPreservesAuthorAndOriginalDateWithOnlySelectedRecipient() {
        val copy = Forwarding.copy(source, sender, friend)
        assertEquals("sender", copy.ownerUid)
        assertEquals("전달자", copy.ownerName)
        assertEquals("author", copy.originalOwnerUid)
        assertEquals("원작자", copy.originalOwnerName)
        assertEquals("original", copy.sourceRemoteId)
        assertEquals(source.createdAt, copy.createdAt)
        assertEquals(listOf("recipient"), copy.visibleTo)
        assertEquals(copy.visibleTo, copy.recipients)
        assertEquals(Audience.SOME, copy.audience)
        assertNull(copy.openAfter)
        assertFalse(copy.rawVehicleState.contains("secret"))
        assertFalse(copy.rawVehicleState.contains("odometerKm"))
    }
    @Test fun retriesAreIdempotentAndDifferentRecipientsCannotOverwriteEachOther() {
        val id = Forwarding.copy(source, sender, friend).id
        assertEquals(id, Forwarding.copy(source, sender, friend).id)
        assertNotEquals(id, Forwarding.copy(source, sender, friend.copy(uid = "other")).id)
        assertNotEquals(id, Forwarding.copy(source.copy(remoteId = "other"), sender, friend).id)
        assertNotEquals(id, Forwarding.copy(source, sender.copy(uid = "other"), friend).id)
    }
    @Test(expected = IllegalArgumentException::class) fun cannotSendToYourself() {
        Forwarding.copy(source, sender, friend.copy(uid = sender.uid))
    }
    @Test fun deliveredCapsuleNeedsNeitherGpsNorPhysicalCollection() {
        val c = received()
        assertNull(c.collectedAt)
        assertNull(OpenPolicy.reason(c, null, today, null, 0, 5000))
        assertNull(OpenPolicy.reason(c, null, today, GeoPoint(0.0, 0.0), 100, 5000))
        assertNotNull(OpenPolicy.reason(source.copy(collectedAt = null), null, today, null, 0, 5000))
    }
    @Test fun deliveryStillHonorsDateLockAndDraftRestrictions() {
        assertNotNull(OpenPolicy.reason(received(), LetterLock(0, today.plusDays(1).toEpochDay()), today, null, 0, 5000))
        assertNotNull(OpenPolicy.reason(received().copy(draft = true), null, today, null, 0, 5000))
    }
    @Test fun deliveryNeverAddsFalseMapCollectionsOrRoadAlerts() {
        val c = received().copy(collectedAt = 1000)
        assertFalse(CollectedMapData.eligible(c))
        assertNull(ApproachDetector.next(GeoPoint(c.lat, c.lng), listOf(c), emptyMap(), emptySet(), today))
        assertTrue(CollectedMapData.eligible(source))
    }
}
