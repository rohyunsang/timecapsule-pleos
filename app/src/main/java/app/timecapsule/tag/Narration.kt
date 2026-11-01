package app.timecapsule.tag

import java.time.LocalDate
import java.time.Period

/**
 * The one line spoken before the voice. Plain on purpose: when, and who for a friend's.
 * "18년 전 오늘 남긴 캡슐이에요." / "민수님이 3일 전 남긴 캡슐이에요."
 * Weather and passengers are shown on screen as tags, not read out.
 */
object Narration {

    @Suppress("UNUSED_PARAMETER")
    fun build(buriedOn: LocalDate, today: LocalDate, tags: List<MoodTag>, from: String? = null): String =
        (from?.let { "${it}님이 " } ?: "") + "${elapsed(buriedOn, today)} 남긴 캡슐이에요."

    /** "18년 전 오늘", "3년 전", "5달 전", "오늘" ("오늘" is added when month and day match). */
    fun elapsed(buriedOn: LocalDate, today: LocalDate): String {
        val p = Period.between(buriedOn, today)
        val sameDay = buriedOn.monthValue == today.monthValue && buriedOn.dayOfMonth == today.dayOfMonth
        return when {
            p.years >= 1 -> if (sameDay) "${p.years}년 전 오늘" else "${p.years}년 전"
            p.months >= 1 -> "${p.months}달 전"
            p.days >= 1 -> "${p.days}일 전"
            else -> "오늘"
        }
    }

    /** Joins a weather adjective with a time noun: "비 오던 밤", "추운 날", "비 오던 날". */
    fun weatherAndTime(tags: List<MoodTag>): String? {
        val night = tags.any { it.label == "밤길" }
        val adjective = when {
            tags.any { it.label == "비 오던" } -> "비 오던"
            tags.any { it.label == "추운 날" } -> "추운"
            tags.any { it.label == "더운 날" } -> "더운"
            else -> null
        }
        return when {
            adjective != null -> "$adjective ${if (night) "밤" else "날"}"
            night -> "어두운 밤"
            else -> null
        }
    }
}
