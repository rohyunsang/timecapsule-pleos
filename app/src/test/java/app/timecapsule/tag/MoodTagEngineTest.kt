package app.timecapsule.tag

import app.timecapsule.vehicle.VehicleSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoodTagEngineTest {

    private fun labels(s: VehicleSnapshot) = MoodTagEngine.tagsFor(s).map { it.label }

    @Test fun rainyNightWithThree() {
        val s = VehicleSnapshot(wiperOn = true, headlightOn = true, occupiedSeats = 3, placeName = "7번 국도")
        assertEquals(listOf("밤", "비", "3명 탑승", "7번 국도"), labels(s))
    }

    @Test fun unreadDomainsProduceNoTags() {
        assertTrue(labels(VehicleSnapshot()).isEmpty())
    }

    @Test fun seatCounts() {
        assertTrue(labels(VehicleSnapshot(occupiedSeats = 1)).isEmpty())
        assertEquals(listOf("2명 탑승"), labels(VehicleSnapshot(occupiedSeats = 2)))
        assertEquals(listOf("5명 탑승"), labels(VehicleSnapshot(occupiedSeats = 5)))
    }

    @Test fun temperatureThresholds() {
        assertEquals(listOf("추운 날"), labels(VehicleSnapshot(outsideTempC = 5f)))
        assertTrue(labels(VehicleSnapshot(outsideTempC = 5.1f)).isEmpty())
        assertEquals(listOf("더운 날"), labels(VehicleSnapshot(outsideTempC = 28f)))
    }

    @Test fun arrivalNeedsParkAndOpenDoor() {
        assertTrue(labels(VehicleSnapshot(gearInPark = true, anyDoorOpen = false)).isEmpty())
        assertEquals(listOf("도착"), labels(VehicleSnapshot(gearInPark = true, anyDoorOpen = true)))
    }

    @Test fun capsAtFiveInNarrationOrder() {
        val s = VehicleSnapshot(
            wiperOn = true, headlightOn = true, occupiedSeats = 4, outsideTempC = 0f,
            anyWindowOpen = true, onHighway = true, placeName = "영동고속도로",
        )
        val tags = MoodTagEngine.tagsFor(s)
        assertEquals(5, tags.size)
        assertEquals(tags.sortedBy { it.category.ordinal }, tags)
        assertEquals("밤", tags.first().label)
    }

    @Test fun storedTagsRoundTripWithCategories() {
        val tags = MoodTagEngine.tagsFor(VehicleSnapshot(headlightOn = true, wiperOn = true, occupiedSeats = 3, placeName = "7번 국도"))
        val back = MoodTagEngine.fromStored(MoodTagEngine.toStored(tags))
        assertEquals(tags, back)
    }
}
