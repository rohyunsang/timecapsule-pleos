package app.timecapsule.drive

import org.junit.Assert.*
import org.junit.Test

class DriveSessionTest {
    @Test fun grantsExpireAndNeverSurviveRestart() {
        val s = DriveSession()
        assertFalse(s.active(100))
        s.start(100); s.grant(7, 200)
        assertTrue(s.granted(7, 201)); assertFalse(s.granted(8, 201))
        assertFalse(s.granted(7, 200 + DriveSession.VISIT_MS))
        s.end(); assertFalse(s.granted(7, 201))
        s.start(300); assertFalse(s.granted(7, 301))
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
