package io.timecapsule.vehicle
import org.junit.Assert.*
import org.junit.Test
class CapsulePolicyTest {
    @Test fun staleAndFuturePositionsCannotOpen() {
        assertFalse(CapsulePolicy.fresh(0,10000))
        assertFalse(CapsulePolicy.fresh(20001,20000))
        assertFalse(CapsulePolicy.fresh(1,20000))
        assertTrue(CapsulePolicy.fresh(10000,20000))
    }
    @Test fun onlyThePlaceAndDateTogetherUnlock() {
        assertTrue(CapsulePolicy.canOpen(100.0,true,100,100))
        assertFalse(CapsulePolicy.canOpen(100.01,true,100,100))
        assertFalse(CapsulePolicy.canOpen(1.0,false,100,100))
        assertFalse(CapsulePolicy.canOpen(1.0,true,101,100))
        assertFalse(CapsulePolicy.canOpen(Double.NaN,true,100,100))
        assertFalse(CapsulePolicy.canOpen(-1.0,true,100,100))
    }
    @Test fun recordingRequiresEveryPermissionAndState() {
        assertTrue(CapsulePolicy.canRecord(true,true,true,true))
        for(i in 0..3) {
            val conditions=BooleanArray(4){it!=i}
            assertFalse(CapsulePolicy.canRecord(conditions[0],conditions[1],conditions[2],conditions[3]))
        }
    }
    @Test fun invalidCoordinatesCannotBecomeNearby() {
        assertEquals(Double.POSITIVE_INFINITY,CapsulePolicy.distance(Double.NaN,0.0,0.0,0.0),0.0)
        assertEquals(Double.POSITIVE_INFINITY,CapsulePolicy.distance(91.0,0.0,0.0,0.0),0.0)
        assertEquals(0.0,CapsulePolicy.distance(37.5,127.0,37.5,127.0),0.001)
        assertEquals(111.19,CapsulePolicy.distance(37.5,127.0,37.501,127.0),0.1)
        assertTrue(CapsulePolicy.distance(37.5,127.0,38.0,128.0)>70_000)
    }
}
