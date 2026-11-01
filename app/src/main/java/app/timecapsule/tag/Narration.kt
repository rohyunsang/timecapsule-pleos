package app.timecapsule.tag

import java.time.LocalDate
import java.time.Period

/**
 * Builds the spoken intro from spec section 04:
 * "{경과}년 전 {오늘?}, {날씨/시간}. {인원} 이 길을 지나며 남긴 목소리예요."
 * e.g. "18년 전 오늘, 비 오던 밤. 셋이서 이 길을 지나며 남긴 목소리예요."
 */
object Narration {

    fun build(buriedOn: LocalDate, today: LocalDate, tags: List<MoodTag>): String {
        val head = buildString {
            append(elapsed(buriedOn, today))
            weatherAndTime(tags)?.let { append(", ").append(it) }
            append('.')
        }
        val people = tags.firstOrNull { it.category == TagCategory.PEOPLE }?.label
        val tail = if (people != null) "$people 이 길을 지나며 남긴 목소리예요." else "이 길을 지나며 남긴 목소리예요."
        return "$head $tail"
    }

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
