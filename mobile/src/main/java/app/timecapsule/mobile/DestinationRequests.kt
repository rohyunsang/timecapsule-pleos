package app.timecapsule.mobile

import android.content.SharedPreferences
import app.timecapsule.companion.CarDestination
import java.util.UUID

/** Retains uncertain sends across process restarts, scoped to the signed-in account. */
internal class DestinationRequests(
    private val read: (String) -> String?,
    private val write: (String, String) -> Boolean,
) {
    constructor(preferences: SharedPreferences) : this(
        { key -> preferences.getString(key, null) },
        { key, value -> preferences.edit().putString(key, value).commit() },
    )
    fun idFor(uid: String, capsuleId: String, known: List<CarDestination>): String {
        val key = "${uid.length}:$uid$capsuleId"
        val previous = read(key)
        val completed = known.any { it.id == previous && it.status in setOf("accepted", "dismissed", "failed") }
        if (previous != null && !completed) return previous
        val next = UUID.randomUUID().toString()
        check(write(key, next)) { "요청을 보관하지 못했어요. 저장 공간을 확인해 주세요." }
        return next
    }
}
