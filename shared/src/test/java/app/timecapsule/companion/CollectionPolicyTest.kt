package app.timecapsule.companion

import app.timecapsule.social.*
import org.junit.Assert.*
import org.junit.Test

class CollectionPolicyTest {
    private val capsule = RemoteCapsule("id", "me", "나", 1, 37.0, 127.0, 300, "장소", 20, "", "{}", null, Audience.SELF, emptyList(), emptyList())
    private val here = CollectionPolicy.Fix(37.0, 127.0, 10.0, 1000)
    private fun reason(c: RemoteCapsule = capsule, fix: CollectionPolicy.Fix? = here, authorized: Boolean = true, collected: Boolean = false, now: Long = 2000) =
        CollectionPolicy.reason(c, authorized, collected, 20000, fix, now)
    @Test fun freshAccuratePhysicalVisitCollects() { assertNull(reason()) }
    @Test fun missingStaleFutureAndMockedLocationsNeverCollect() {
        assertEquals(CollectionPolicy.Block.LOCATION_REQUIRED, reason(fix = null))
        assertEquals(CollectionPolicy.Block.STALE_LOCATION, reason(now = 11001))
        assertEquals(CollectionPolicy.Block.STALE_LOCATION, reason(now = 999))
        assertEquals(CollectionPolicy.Block.MOCK_LOCATION, reason(fix = here.copy(mocked = true)))
    }
    @Test fun approximateLocationCannotClaimVisit() {
        assertEquals(CollectionPolicy.Block.INACCURATE_LOCATION, reason(fix = here.copy(accuracyM = 1000.0)))
        assertEquals(CollectionPolicy.Block.INACCURATE_LOCATION, reason(fix = here.copy(accuracyM = Double.NaN)))
        assertEquals(CollectionPolicy.Block.OUT_OF_RANGE, reason(fix = here.copy(lat = 38.0)))
        assertEquals(CollectionPolicy.Block.LOCATION_REQUIRED, reason(fix = here.copy(lat = Double.NaN)))
    }
    @Test fun uncertaintyCircleMustFitWithinRadius() {
        assertEquals(CollectionPolicy.Block.OUT_OF_RANGE, reason(capsule.copy(radiusM = 5)))
        assertNull(reason(capsule.copy(radiusM = 10)))
    }
    @Test fun collectionAndForwardingRemoveOnlyLocationRestriction() {
        assertNull(reason(fix = null, collected = true))
        assertNull(reason(capsule.copy(sourceRemoteId = "source"), fix = null))
        assertEquals(CollectionPolicy.Block.NO_ACCESS, reason(collected = true, authorized = false))
        assertEquals(CollectionPolicy.Block.WAITING_DATE, reason(capsule.copy(openAfter = 20001), collected = true))
        assertEquals(CollectionPolicy.Block.INVALID_PLACE, reason(capsule.copy(lat = 91.0), collected = true))
    }
    @Test fun opensOnExactDateAndLocationAgeBoundary() {
        assertNull(reason(capsule.copy(openAfter = 20000), now = 11000))
    }
    @Test fun timedReplyOpensWithoutLocationOnlyAtDeadline() {
        val reply = capsule.copy(replyToId = "parent", replyMode = ReplyPolicy.AFTER_THREE_DAYS, replyAvailableAt = 10000)
        assertEquals(CollectionPolicy.Block.WAITING_DATE, CollectionPolicy.reason(reply, true, true, 20000, here, 2000, 9999))
        assertNull(CollectionPolicy.reason(reply, true, false, 20000, null, 2000, 10000))
        assertEquals(CollectionPolicy.Block.NO_ACCESS, CollectionPolicy.reason(reply, false, false, 20000, null, 2000, 10000))
        assertEquals(CollectionPolicy.Block.WAITING_DATE, CollectionPolicy.reason(reply.copy(replyAvailableAt = null), true, false, 20000, null, 2000, 10000))
    }
    @Test fun locationReplyStillRequiresVisit() {
        val reply = capsule.copy(replyToId = "parent", replyMode = ReplyPolicy.LOCATION)
        assertEquals(CollectionPolicy.Block.LOCATION_REQUIRED, reason(reply, fix = null))
        assertNull(reason(reply))
        assertNull(reason(reply, fix = null, collected = true))
    }
}
