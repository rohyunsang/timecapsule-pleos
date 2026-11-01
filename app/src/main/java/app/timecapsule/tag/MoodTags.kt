package app.timecapsule.tag

import app.timecapsule.vehicle.VehicleSnapshot

/** Narration order from spec F-02: time -> weather -> people -> place. */
enum class TagCategory { TIME, WEATHER, PEOPLE, PLACE }

data class MoodTag(val label: String, val category: TagCategory)

/** Rule table from feature-spec section 04. Thresholds are provisional until the SDK values are confirmed. */
object MoodTagEngine {
    const val MAX_TAGS = 5
    const val COLD_AT_OR_BELOW_C = 5f
    const val HOT_AT_OR_ABOVE_C = 28f

    fun tagsFor(s: VehicleSnapshot): List<MoodTag> {
        val tags = mutableListOf<MoodTag>()
        if (s.headlightOn == true) tags += MoodTag("밤길", TagCategory.TIME)
        if (s.wiperOn == true) tags += MoodTag("비 오던", TagCategory.WEATHER)
        s.outsideTempC?.let {
            when {
                it <= COLD_AT_OR_BELOW_C -> tags += MoodTag("추운 날", TagCategory.WEATHER)
                it >= HOT_AT_OR_ABOVE_C -> tags += MoodTag("더운 날", TagCategory.WEATHER)
            }
        }
        if (s.anyWindowOpen == true) tags += MoodTag("창문 열고", TagCategory.WEATHER)
        when (s.occupiedSeats) {
            null, 0, 1 -> Unit
            2 -> tags += MoodTag("둘이서", TagCategory.PEOPLE)
            3 -> tags += MoodTag("셋이서", TagCategory.PEOPLE)
            else -> tags += MoodTag("넷이서", TagCategory.PEOPLE)
        }
        if (s.gearInPark == true && s.anyDoorOpen == true) tags += MoodTag("도착한 순간", TagCategory.PLACE)
        if (s.onHighway == true) tags += MoodTag("고속도로를 달리던", TagCategory.PLACE)
        s.placeName?.takeIf { it.isNotBlank() }?.let { tags += MoodTag(it, TagCategory.PLACE) }
        return tags.sortedBy { it.category.ordinal }.take(MAX_TAGS)
    }
}
