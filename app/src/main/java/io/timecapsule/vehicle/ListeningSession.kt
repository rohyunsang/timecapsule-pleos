package io.timecapsule.vehicle

/** In-memory, one-visit grants. Nothing here survives a profile/process restart. */
class ListeningSession {
    companion object {
        const val SESSION_MS = 2 * 60 * 60 * 1000L
        const val GRANT_MS = 5 * 60 * 1000L
        const val PARK_IDLE_MS = 15 * 60 * 1000L
        const val GAP_MS = 60_000L
        const val EXIT_METERS = 150.0
    }
    var active = false; private set
    var automatic = false; private set
    var started = 0L; private set
    var currentId: String? = null; private set
    var grantedAt = 0L; private set
    private val outside = mutableSetOf<String>()
    private val seen = mutableSetOf<String>()
    private val completed = mutableSetOf<String>()
    private var nextArrivalAt = 0L
    fun start(now: Long, auto: Boolean) {
        end(); active=true; automatic=auto; started=now
    }
    fun expired(now: Long) = active && (now < started || now-started >= SESSION_MS)
    fun observe(id: String, distance: Double, eligible: Boolean, now: Long): Boolean {
        if (!active || expired(now) || !distance.isFinite()) return false
        if (distance >= EXIT_METERS) { outside.add(id); seen.remove(id); return false }
        if (!eligible || distance > CapsulePolicy.RADIUS_METERS || id !in outside || id in seen) return false
        seen.add(id)
        return currentId == null && id !in completed && now >= nextArrivalAt
    }
    fun grant(id: String, now: Long) { currentId=id; grantedAt=now }
    fun validGrant(id: String, now: Long) = active && !expired(now) && currentId==id && now>=grantedAt && now-grantedAt<GRANT_MS
    fun finish(now: Long) {
        currentId?.let { completed.add(it) }
        currentId=null; grantedAt=0; nextArrivalAt=now+GAP_MS
    }
    fun end() {
        active=false; automatic=false; started=0; currentId=null; grantedAt=0
        outside.clear(); seen.clear(); completed.clear(); nextArrivalAt=0
    }
}
