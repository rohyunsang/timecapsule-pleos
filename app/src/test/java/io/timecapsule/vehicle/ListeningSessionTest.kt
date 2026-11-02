package io.timecapsule.vehicle
import org.junit.Assert.*
import org.junit.Test

class ListeningSessionTest {
    @Test fun mustLeaveAndRevisitNotSimplyEnableAtPlace() {
        val s=ListeningSession();s.start(100,false)
        assertFalse(s.observe("a",30.0,true,101))
        assertFalse(s.observe("a",149.0,false,102))
        assertFalse(s.observe("a",151.0,false,103))
        assertFalse(s.observe("a",80.0,false,104)) // date still locked
        assertTrue(s.observe("a",80.0,true,105))
        assertFalse(s.observe("a",90.0,true,106)) // noisy callback must not repeat
    }
    @Test fun openedCapsuleSurvivesLeavingButNotExpiryOrRestart() {
        val s=ListeningSession();s.start(100,true);s.grant("a",200)
        s.observe("a",2000.0,false,300)
        assertTrue(s.validGrant("a",300))
        assertFalse(s.validGrant("b",300))
        assertFalse(s.validGrant("a",199))
        assertFalse(s.validGrant("a",200+ListeningSession.GRANT_MS))
        s.end();assertFalse(s.validGrant("a",400))
        s.start(500,false);assertFalse(s.validGrant("a",501))
    }
    @Test fun oneCapsuleAtATimeAndNoReplayAfterCompletion() {
        val s=ListeningSession();s.start(100,true)
        s.observe("a",200.0,false,101);s.observe("b",200.0,false,101)
        assertTrue(s.observe("a",1.0,true,102));s.grant("a",102)
        assertFalse(s.observe("b",1.0,true,103))
        s.finish(104)
        s.observe("a",200.0,false,105)
        assertFalse(s.observe("a",1.0,true,100_000))
        assertFalse(s.observe("b",1.0,true,100_000)) // must leave again, no chain
        s.observe("b",200.0,false,100_001)
        assertTrue(s.observe("b",1.0,true,100_002))
    }
    @Test fun cooldownAndSessionEndApplyEvenToNewCapsules() {
        val s=ListeningSession();s.start(100,true);s.grant("a",110);s.finish(120)
        s.observe("b",200.0,false,130)
        assertFalse(s.observe("b",1.0,true,140))
        assertTrue(s.expired(100+ListeningSession.SESSION_MS))
        assertFalse(s.observe("c",1.0,true,100+ListeningSession.SESSION_MS))
    }
}
