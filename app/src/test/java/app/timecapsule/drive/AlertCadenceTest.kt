package app.timecapsule.drive

import org.junit.Assert.*
import org.junit.Test

class AlertCadenceTest {
    @Test fun quietTripSuppressesFirstAndLaterPrompts() {
        assertFalse(AlertCadence.allows(true, 1, null, 1))
        assertFalse(AlertCadence.allows(true, 1, 1, 1_000_000))
    }
    @Test fun firstPromptAndExactBoundaryAreAllowed() {
        assertTrue(AlertCadence.allows(false, 5, null, 1))
        assertFalse(AlertCadence.allows(false, 5, 1000, 300999))
        assertTrue(AlertCadence.allows(false, 5, 1000, 301000))
    }
    @Test fun invalidPreferenceAndReversedClockDoNotCreateBurst() {
        assertFalse(AlertCadence.allows(false, 0, 1000, 61000))
        assertFalse(AlertCadence.allows(false, 1, 1000, 999))
        assertTrue(AlertCadence.allows(false, 0, 1000, 301000))
    }
}
