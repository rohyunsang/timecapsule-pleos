package app.timecapsule.tag

import app.timecapsule.weather.Sky
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/**
 * What the day was like when the capsule was buried, for whoever listens to it later.
 * Built from the raw values saved with the capsule (Capsule.rawVehicleState), so a friend's
 * car shows the same thing. Facts only, no mood words:
 *   screen: "금요일 밤   흐림   바깥 12도   3명 탑승"
 *   spoken: "그날은 흐렸고, 3명이 타고 있었어요."
 */
object TheDay {

    /** The weather of that day: the saved sky, but a running wiper always means rain. */
    fun sky(raw: String): Sky? {
        val v = parse(raw)
        val saved = v["sky"]?.let { s -> Sky.entries.firstOrNull { it.name == s } }
        val wet = saved == Sky.RAIN || saved == Sky.SNOW || saved == Sky.STORM
        return if (v["wiperOn"] == "true" && !wet) Sky.RAIN else saved
    }

    fun isNight(createdAt: Long, zone: ZoneId = ZoneId.systemDefault()) =
        Instant.ofEpochMilli(createdAt).atZone(zone).hour.let { it >= 20 || it < 5 }

    /** Short facts for the screen, in order: when, weather, temperature, people, road. */
    fun facts(createdAt: Long, raw: String, zone: ZoneId = ZoneId.systemDefault(), thisYear: Int = java.time.LocalDate.now(zone).year): List<String> {
        val t = Instant.ofEpochMilli(createdAt).atZone(zone)
        val v = parse(raw)
        return listOfNotNull(
            // The date itself ("11월 2일 월요일 아침"); the year only when it is not this year.
            (if (t.year != thisYear) "${t.year}년 " else "") +
                "${t.monthValue}월 ${t.dayOfMonth}일 ${dayName(t.dayOfWeek)} ${partOfDay(t.hour)}",
            sky(raw)?.let(::label),
            v["outsideTempC"]?.toFloatOrNull()?.let { "바깥 ${Math.round(it)}도" },
            v["occupiedSeats"]?.toIntOrNull()?.let(::people),
            "창문 열림".takeIf { v["anyWindowOpen"] == "true" },
            "고속도로".takeIf { v["onHighway"] == "true" },
        )
    }

    /** One plain sentence after the narration, or null when there is nothing worth saying. */
    fun spoken(raw: String): String? {
        val v = parse(raw)
        val sky = sky(raw)
        val seats = v["occupiedSeats"]?.toIntOrNull()?.takeIf { it >= 2 }
        val who = seats?.let { "${count(it)} 타고 있었" }
        return when {
            sky != null && who != null -> "그날은 ${verb(sky)}고, ${who}어요."
            sky != null -> "그날은 ${verb(sky)}어요."
            who != null -> "그날은 ${who}어요."
            else -> null
        }
    }

    fun label(s: Sky) = when (s) {
        Sky.CLEAR -> "맑음"
        Sky.PARTLY -> "구름 조금"
        Sky.CLOUDY -> "흐림"
        Sky.FOG -> "안개"
        Sky.RAIN -> "비"
        Sky.SNOW -> "눈"
        Sky.STORM -> "천둥 번개"
    }

    /** Past stem, finished with "어요" or "고": 맑았어요 / 맑았고. */
    private fun verb(s: Sky) = when (s) {
        Sky.CLEAR -> "맑았"
        Sky.PARTLY -> "구름이 조금 있었"
        Sky.CLOUDY -> "흐렸"
        Sky.FOG -> "안개가 꼈"
        Sky.RAIN -> "비가 왔"
        Sky.SNOW -> "눈이 왔"
        Sky.STORM -> "천둥 번개가 쳤"
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
        else -> "${n}명 탑승"
    }

    private fun count(n: Int) = "${n}명이"

    /** Flat {"key":value} written by VehicleSnapshot.toJson; no JSON library so it runs in unit tests. */
    private fun parse(raw: String): Map<String, String> =
        Regex("\"(\\w+)\"\\s*:\\s*(\"(?:[^\"\\\\]|\\\\.)*\"|[^,}]+)").findAll(raw)
            .associate { it.groupValues[1] to it.groupValues[2].trim().trim('"') }
}
