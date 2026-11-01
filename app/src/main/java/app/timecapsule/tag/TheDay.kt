package app.timecapsule.tag

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/**
 * What the day was like when the capsule was buried, for whoever listens to it later.
 * Built from the raw vehicle values saved with the capsule (Capsule.rawVehicleState), so a
 * friend's car shows the same thing. Facts only, no mood words:
 *   screen: "금요일 밤 · 비 · 바깥 12도 · 셋이서"
 *   spoken: "그날은 비가 왔고, 셋이 타고 있었어요."
 */
object TheDay {

    /** Short facts for the screen, in order: when, weather, temperature, people, road. */
    fun facts(createdAt: Long, raw: String, zone: ZoneId = ZoneId.systemDefault()): List<String> {
        val t = Instant.ofEpochMilli(createdAt).atZone(zone)
        val v = parse(raw)
        return listOfNotNull(
            "${dayName(t.dayOfWeek)} ${partOfDay(t.hour)}",
            "비".takeIf { v["wiperOn"] == "true" },
            v["outsideTempC"]?.toFloatOrNull()?.let { "바깥 ${Math.round(it)}도" },
            v["occupiedSeats"]?.toIntOrNull()?.let(::people),
            "창문 열고".takeIf { v["anyWindowOpen"] == "true" },
            "고속도로".takeIf { v["onHighway"] == "true" },
        )
    }

    /** One plain sentence after the narration, or null when there is nothing worth saying. */
    fun spoken(raw: String): String? {
        val v = parse(raw)
        val rain = v["wiperOn"] == "true"
        val seats = v["occupiedSeats"]?.toIntOrNull()?.takeIf { it >= 2 }
        val who = seats?.let { "${count(it)} 타고 있었어요" }
        return when {
            rain && who != null -> "그날은 비가 왔고, $who."
            rain -> "그날은 비가 왔어요."
            who != null -> "그날은 $who."
            else -> null
        }
    }

    fun partOfDay(hour: Int) = when (hour) {
        in 0..4 -> "새벽"
        in 5..10 -> "아침"
        in 11..16 -> "낮"
        in 17..19 -> "저녁"
        else -> "밤"
    }

    private fun dayName(d: DayOfWeek) = "월화수목금토일"[d.value - 1] + "요일"

    private fun people(n: Int) = when {
        n <= 0 -> null
        n == 1 -> "혼자"
        n <= 4 -> count(n) + "서" // 둘이서
        else -> "${n}명"
    }

    private fun count(n: Int) = when (n) {
        2 -> "둘이"
        3 -> "셋이"
        4 -> "넷이"
        else -> "${n}명이"
    }

    /** Flat {"key":value} written by VehicleSnapshot.toJson; no JSON library so it runs in unit tests. */
    private fun parse(raw: String): Map<String, String> =
        Regex("\"(\\w+)\"\\s*:\\s*(\"(?:[^\"\\\\]|\\\\.)*\"|[^,}]+)").findAll(raw)
            .associate { it.groupValues[1] to it.groupValues[2].trim().trim('"') }
}
