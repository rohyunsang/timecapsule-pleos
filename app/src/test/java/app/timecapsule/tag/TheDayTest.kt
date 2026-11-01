package app.timecapsule.tag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class TheDayTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val fridayNight = ZonedDateTime.of(2026, 10, 30, 21, 5, 0, 0, zone).toInstant().toEpochMilli()

    @Test fun factsFromRawState() {
        val raw = """{"wiperOn":true,"headlightOn":true,"occupiedSeats":3,"outsideTempC":11.6,"placeName":"7번 국도, \"강릉\""}"""
        assertEquals(listOf("금요일 밤", "비", "바깥 12도", "셋이서"), TheDay.facts(fridayNight, raw, zone))
    }

    @Test fun emptyStateStillSaysWhen() {
        assertEquals(listOf("금요일 밤"), TheDay.facts(fridayNight, "{}", zone))
    }

    @Test fun spokenIsOnePlainSentence() {
        assertEquals("그날은 비가 왔고, 셋이 타고 있었어요.", TheDay.spoken("""{"wiperOn":true,"occupiedSeats":3}"""))
        assertEquals("그날은 비가 왔어요.", TheDay.spoken("""{"wiperOn":true,"occupiedSeats":1}"""))
        assertEquals("그날은 둘이 타고 있었어요.", TheDay.spoken("""{"wiperOn":false,"occupiedSeats":2}"""))
        assertNull(TheDay.spoken("""{"headlightOn":true}"""))
    }
}
