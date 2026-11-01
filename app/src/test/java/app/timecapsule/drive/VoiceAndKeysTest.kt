package app.timecapsule.drive

import app.timecapsule.social.shareableState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceAndKeysTest {
    @Test fun answers() {
        assertEquals(Answer.LISTEN, AnswerParser.parse("들려줘"))
        assertEquals(Answer.LISTEN, AnswerParser.parse("응 틀어 줘"))
        assertEquals(Answer.LATER, AnswerParser.parse("다음에"))
        assertEquals(Answer.LATER, AnswerParser.parse("응 나중에 들을게"))
        assertEquals(Answer.LATER, AnswerParser.parse("아니 괜찮아"))
        assertNull(AnswerParser.parse("오늘 날씨 좋다"))
    }

    @Test fun friendsDoNotGetOdometerOrCarModel() {
        val raw = """{"wiperOn":true,"occupiedSeats":2,"odometerKm":32104.5,"carModel":"IONIQ \"5\"","sky":"RAIN"}"""
        assertEquals("""{"wiperOn":true,"occupiedSeats":2,"sky":"RAIN"}""", shareableState(raw))
        assertEquals("""{"wiperOn":true}""", shareableState("""{"wiperOn":true,"odometerKm":1.0}"""))
    }
}
