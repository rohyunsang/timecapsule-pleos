package app.timecapsule.tag

import app.timecapsule.vehicle.VehicleSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class NarrationTest {

    @Test fun specExample() {
        val tags = MoodTagEngine.tagsFor(VehicleSnapshot(wiperOn = true, headlightOn = true, occupiedSeats = 3))
        val text = Narration.build(LocalDate.of(2027, 7, 30), LocalDate.of(2045, 7, 30), tags)
        assertEquals("18년 전 오늘, 비 오던 밤. 셋이서 이 길을 지나며 남긴 목소리예요.", text)
    }

    @Test fun elapsedPhrases() {
        val buried = LocalDate.of(2026, 3, 10)
        assertEquals("3년 전", Narration.elapsed(buried, LocalDate.of(2029, 5, 1)))
        assertEquals("5달 전", Narration.elapsed(buried, LocalDate.of(2026, 8, 20)))
        assertEquals("4일 전", Narration.elapsed(buried, LocalDate.of(2026, 3, 14)))
        assertEquals("오늘", Narration.elapsed(buried, buried))
    }

    @Test fun noWeatherNoPeople() {
        val text = Narration.build(LocalDate.of(2026, 1, 1), LocalDate.of(2028, 6, 1), emptyList())
        assertEquals("2년 전. 이 길을 지나며 남긴 목소리예요.", text)
    }

    @Test fun coldDay() {
        val tags = MoodTagEngine.tagsFor(VehicleSnapshot(outsideTempC = -3f, occupiedSeats = 2))
        assertEquals("추운 날", Narration.weatherAndTime(tags))
    }
}
