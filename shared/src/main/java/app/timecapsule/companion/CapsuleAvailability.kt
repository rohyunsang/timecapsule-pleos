package app.timecapsule.companion

import app.timecapsule.social.RemoteCapsule
import java.time.Instant
import java.time.ZoneId

/** Display state only; opening still checks server authorization and location. */
object CapsuleAvailability {
    enum class State { WAITING, VISIT_REQUIRED, PLAYABLE }
    fun state(c: RemoteCapsule, collected: Boolean, now: Long): State {
        val today = Instant.ofEpochMilli(now).atZone(ZoneId.of("Asia/Seoul")).toLocalDate().toEpochDay()
        if (c.openAfter?.let { it > today } == true || ReplyPolicy.waiting(c.replyMode, c.replyAvailableAt, now)) return State.WAITING
        return if (collected || c.sourceRemoteId != null || ReplyPolicy.remoteOpen(c.replyMode, c.replyAvailableAt, now)) State.PLAYABLE else State.VISIT_REQUIRED
    }
    fun label(state: State) = when (state) {
        State.WAITING -> "개봉 대기"
        State.VISIT_REQUIRED -> "현장에서 수집"
        State.PLAYABLE -> "어디서든 듣기"
    }
}
