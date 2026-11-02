package app.timecapsule.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SilenceWatchTest {
    @Test fun talkingKeepsItGoing() {
        val w = SilenceWatch(7_000, 0)
        for (t in 0L..20_000L step 100) assertFalse(w.feed(0.5f, t))
        assertTrue(w.heardVoice)
    }

    @Test fun endsSevenSecondsAfterTheLastWord() {
        val w = SilenceWatch(7_000, 0)
        assertFalse(w.feed(0.6f, 2_000))
        assertFalse(w.feed(0.05f, 8_900))
        assertTrue(w.feed(0.05f, 9_000))
        assertTrue(w.heardVoice)
    }

    @Test fun nobodySpokeIsMarked() {
        val w = SilenceWatch(5_000, 0)
        assertFalse(w.feed(0.1f, 4_900))
        assertTrue(w.feed(0.1f, 5_000))
        assertFalse(w.heardVoice)
    }

    @Test fun offNeverEnds() {
        val w = SilenceWatch(0, 0)
        assertFalse(w.feed(0f, 600_000))
    }
}
