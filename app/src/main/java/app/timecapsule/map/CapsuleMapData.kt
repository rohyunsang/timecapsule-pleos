package app.timecapsule.map

import app.timecapsule.capsule.AlbumData
import app.timecapsule.capsule.OpenPolicy
import app.timecapsule.data.Capsule
import app.timecapsule.data.LetterLock
import app.timecapsule.geo.GeoPoint
import java.time.LocalDate

/** My buried locations plus physical collections; forwarded audio is not a visited place. */
object CapsuleMapData {
    enum class State { LOCKED, WAITING, READY, OPEN }
    fun eligible(c: Capsule) = AlbumData.canNavigate(c)
    fun groups(capsules: List<Capsule>) = capsules.filter(::eligible)
        .groupBy { GeoPoint(it.lat, it.lng) }.values.toList()
    fun state(c: Capsule, lock: LetterLock?, today: LocalDate, here: GeoPoint? = null, positionAt: Long = 0, now: Long = 0): State = when {
        lock != null && today.toEpochDay() < lock.openAfter -> State.WAITING
        c.collectedAt == null && OpenPolicy.reason(c, lock, today, here, positionAt, now) == null -> State.READY
        c.collectedAt == null -> State.LOCKED
        else -> State.OPEN
    }
    fun owner(c: Capsule) = if (c.isMine) "나" else c.ownerName?.trim()?.takeIf { it.isNotBlank() } ?: "친구"
    fun avatar(c: Capsule) = owner(c).let { it.substring(0, it.offsetByCodePoints(0, 1)) }
    fun label(state: State) = when (state) {
        State.LOCKED -> "수집 전"
        State.WAITING -> "개봉 대기"
        State.READY -> "수집 가능"
        State.OPEN -> "수집 완료"
    }
}
