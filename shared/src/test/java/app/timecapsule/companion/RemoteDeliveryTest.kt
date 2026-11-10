package app.timecapsule.companion

import app.timecapsule.social.*
import org.junit.Assert.*
import org.junit.Test

class RemoteDeliveryTest {
    private val source = RemoteCapsule("source", "author", "작성자", 100, 37.0, 127.0, 300, "장소", 20, "", "{}", 10, Audience.FRIENDS, listOf("sender"), listOf("sender"))
    private val sender = Profile("sender", "전달자", "SEND27")
    private val friend = Friend("friend", "친구", "FRND27")
    @Test fun ownerUncollectedAndForwardedCopiesAreNotEligible() {
        assertFalse(RemoteDelivery.eligible(source, "author", true))
        assertFalse(RemoteDelivery.eligible(source, sender.uid, false))
        assertFalse(RemoteDelivery.eligible(source.copy(sourceRemoteId = "older"), sender.uid, true))
        assertTrue(RemoteDelivery.eligible(source, sender.uid, true))
    }
    @Test fun deliveryPreservesOriginalAuthorAndIsIdempotent() {
        val copy = RemoteDelivery.copy(source, sender, friend)
        assertEquals(copy, RemoteDelivery.copy(source, sender, friend))
        assertEquals("author", copy.originalOwnerUid)
        assertEquals("source", copy.sourceRemoteId)
        assertEquals(listOf("friend"), copy.visibleTo)
        assertEquals("sender", copy.ownerUid)
        assertNull(copy.openAfter)
        assertNotEquals(copy.id, RemoteDelivery.copy(source, sender, friend.copy(uid = "other")).id)
        assertEquals("author", source.ownerUid)
    }
}
