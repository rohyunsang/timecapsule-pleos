package app.timecapsule.drive

import org.junit.Assert.*
import org.junit.Test

class DriveSessionTest {
    @Test fun sessionDoesNotSurviveRestart() {
        val s = DriveSession()
        assertFalse(s.active(100))
        s.start(100); assertTrue(s.active(101))
        s.end(); assertFalse(s.active(102))
        assertFalse(DriveSession().active(103))
    }
    @Test fun briefParkingDoesNotEndDriveButLongParkingDoes() {
        val s = DriveSession(); s.start(100)
        assertFalse(s.expired(200, true)); assertFalse(s.expired(300, false))
        assertFalse(s.expired(400, true))
        assertTrue(s.expired(400 + DriveSession.PARK_MS, true))
    }
    @Test fun monotonicRollbackAndMaximumLifetimeEndDrive() {
        val s = DriveSession(); s.start(100)
        assertTrue(s.expired(99, false))
        assertTrue(s.expired(100 + DriveSession.MAX_MS, false))
    }
}
