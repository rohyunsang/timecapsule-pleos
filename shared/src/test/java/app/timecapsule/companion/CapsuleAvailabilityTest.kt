package app.timecapsule.companion

import app.timecapsule.social.*
import org.junit.Assert.*
import org.junit.Test

class CapsuleAvailabilityTest {
    private val c = RemoteCapsule("c", "owner", "친구", 0, 37.0, 127.0, 300, "장소", 20, "", "{}", null, Audience.SOME, emptyList(), emptyList())
    @Test fun dateLockTakesPriorityOverCollectionAndForwarding() {
        val locked = c.copy(openAfter = 1, sourceRemoteId = "source")
        assertEquals(CapsuleAvailability.State.WAITING, CapsuleAvailability.state(locked, true, 0))
        // Korea midnight on Jan 2, 1970.
        assertEquals(CapsuleAvailability.State.PLAYABLE, CapsuleAvailability.state(locked, true, 54_000_000))
    }
    @Test fun timedReplyBecomesPlayableWithoutCollectionAtExactDeadline() {
        val reply = c.copy(replyMode = ReplyPolicy.AFTER_THREE_DAYS, replyAvailableAt = 5000)
        assertEquals(CapsuleAvailability.State.WAITING, CapsuleAvailability.state(reply, false, 4999))
        assertEquals(CapsuleAvailability.State.PLAYABLE, CapsuleAvailability.state(reply, false, 5000))
        assertEquals(CapsuleAvailability.State.WAITING, CapsuleAvailability.state(reply.copy(replyAvailableAt = null), true, 5000))
        assertEquals(CapsuleAvailability.State.VISIT_REQUIRED, CapsuleAvailability.state(c, false, 5000))
    }
}
