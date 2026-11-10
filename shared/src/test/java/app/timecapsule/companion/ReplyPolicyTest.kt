package app.timecapsule.companion
import org.junit.Assert.*
import org.junit.Test
class ReplyPolicyTest {
    @Test fun threeDaysMeansSeventyTwoHoursAndDoesNotOpenEarly() {
        val available = 1234L + ReplyPolicy.WAIT_MS
        assertEquals(259200000L, ReplyPolicy.WAIT_MS)
        assertTrue(ReplyPolicy.waiting(ReplyPolicy.AFTER_THREE_DAYS, available, available-1))
        assertFalse(ReplyPolicy.remoteOpen(ReplyPolicy.AFTER_THREE_DAYS, available, available-1))
        assertTrue(ReplyPolicy.remoteOpen(ReplyPolicy.AFTER_THREE_DAYS, available, available))
    }
    @Test fun missingServerTimestampStaysLocked() {
        assertTrue(ReplyPolicy.waiting(ReplyPolicy.AFTER_THREE_DAYS, null, Long.MAX_VALUE))
        assertFalse(ReplyPolicy.remoteOpen(ReplyPolicy.AFTER_THREE_DAYS, null, Long.MAX_VALUE))
    }
    @Test fun defaultLocationAndLegacyCapsulesNeverGainRemoteAccessFromTimer() {
        assertFalse(ReplyPolicy.waiting(ReplyPolicy.LOCATION, null, 100))
        assertFalse(ReplyPolicy.remoteOpen(ReplyPolicy.LOCATION, 1, 100))
        assertFalse(ReplyPolicy.remoteOpen(null, 1, 100))
    }
}
