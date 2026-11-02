package app.timecapsule.drive

/** Monotonic, memory-only permission for this drive only; collected capsules live in Room. */
class DriveSession {
    var startedAt: Long? = null; private set
    private var parkedAt: Long? = null
    fun start(now: Long) { end(); startedAt = now }
    fun active(now: Long): Boolean = startedAt?.let { now >= it && now - it < MAX_MS } == true
    fun expired(now: Long, parked: Boolean): Boolean {
        if (startedAt == null) return false
        if (parked) { if (parkedAt == null) parkedAt = now } else parkedAt = null
        return !active(now) || parkedAt?.let { now - it >= PARK_MS } == true
    }
    fun end() { startedAt = null; parkedAt = null }
    companion object {
        const val MAX_MS = 2 * 60 * 60_000L
        const val PARK_MS = 15 * 60_000L
    }
}
