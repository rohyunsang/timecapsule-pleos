package app.timecapsule.drive

/** Monotonic, memory-only permission for this drive and one opened capsule. */
class DriveSession {
    var startedAt: Long? = null; private set
    private var parkedAt: Long? = null
    private var capsuleId: Long? = null
    private var openedAt = 0L
    fun start(now: Long) { end(); startedAt = now }
    fun active(now: Long): Boolean = startedAt?.let { now >= it && now - it < MAX_MS } == true
    fun expired(now: Long, parked: Boolean): Boolean {
        if (startedAt == null) return false
        if (parked) { if (parkedAt == null) parkedAt = now } else parkedAt = null
        return !active(now) || parkedAt?.let { now - it >= PARK_MS } == true
    }
    fun grant(id: Long, now: Long) { capsuleId = id; openedAt = now }
    fun granted(id: Long, now: Long) = active(now) && capsuleId == id && now >= openedAt && now - openedAt < VISIT_MS
    fun clearGrant() { capsuleId = null; openedAt = 0 }
    fun end() { startedAt = null; parkedAt = null; clearGrant() }
    companion object {
        const val MAX_MS = 2 * 60 * 60_000L
        const val PARK_MS = 15 * 60_000L
        const val VISIT_MS = 5 * 60_000L
    }
}
