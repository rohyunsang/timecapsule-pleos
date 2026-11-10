package app.timecapsule.mobile

import app.timecapsule.companion.CarDestination
import org.junit.Assert.*
import org.junit.Test

class DestinationRequestsTest {
    private val values = mutableMapOf<String, String>()
    private fun requests() = DestinationRequests(values::get) { key, value -> values[key] = value; true }

    @Test fun uncertainSendSurvivesRecreationAndIsAccountScoped() {
        val original = requests().idFor("alice", "capsule", emptyList())
        val restarted = requests()
        assertEquals(original, restarted.idFor("alice", "capsule", emptyList()))
        assertNotEquals(original, restarted.idFor("bob", "capsule", emptyList()))
        assertNotEquals(original, restarted.idFor("alice", "another", emptyList()))
    }

    @Test fun failedLocalPersistenceDoesNotReturnAnUnrecoverableRequest() {
        val requests = DestinationRequests({ null }, { _, _ -> false })
        assertThrows(IllegalStateException::class.java) { requests.idFor("alice", "capsule", emptyList()) }
    }

    @Test fun onlyConfirmedTerminalRequestAllowsANewSend() {
        val requests = requests()
        val first = requests.idFor("alice", "capsule", emptyList())
        val pending = CarDestination(first, "capsule", "서울숲", 37.5, 127.0, 1000)
        assertEquals(first, requests.idFor("alice", "capsule", listOf(pending)))
        val next = requests.idFor("alice", "capsule", listOf(pending.copy(status = "accepted")))
        assertNotEquals(first, next)
        assertEquals(next, requests.idFor("alice", "capsule", listOf(pending.copy(status = "accepted"))))
    }
}
