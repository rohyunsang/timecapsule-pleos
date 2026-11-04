package app.timecapsule.social

import app.timecapsule.data.Capsule
import java.util.UUID

object Forwarding {
    fun eligible(c: Capsule) = !c.isMine && !c.isForwarded && c.collectedAt != null &&
        !c.draft && !c.locationUncertain && !c.remoteId.isNullOrBlank()

    suspend fun send(
        backend: SocialBackend, id: Long, friendUid: String, allowed: () -> Boolean,
        read: suspend (Long) -> Capsule?, unlocked: suspend (Long) -> Boolean,
    ): Result<Unit> {
        try {
            val me = backend.me.value ?: error("친구 탭에서 먼저 이름을 정해 주세요")
            check(allowed()) { "차를 세운 뒤 보내 주세요" }
            val c = read(id) ?: error("캡슐이 삭제되었어요")
            check(eligible(c)) { "직접 수집한 친구 캡슐만 보낼 수 있어요" }
            check(unlocked(id)) { "아직 열리지 않은 캡슐이에요" }
            check(backend.canOpen(c.remoteId!!)) { "원본 공유 권한을 확인하지 못했어요. 연결 후 다시 시도해 주세요" }
            val audio = backend.audio(c.remoteId) ?: error("음성을 불러오지 못했어요. 다시 시도해 주세요")
            val friend = backend.friends.value.firstOrNull { it.uid == friendUid } ?: error("현재 연결된 친구가 아니에요")
            check(backend.me.value?.uid == me.uid && allowed() && read(id) == c) { "상태가 바뀌었어요. 다시 시도해 주세요" }
            backend.upload(copy(c, me, friend), audio).getOrElse { error("보내지 못했어요. 인터넷 연결을 확인하고 다시 시도해 주세요") }
            return Result.success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.failure(IllegalStateException(e.message ?: "보내지 못했어요. 다시 시도해 주세요", e))
        }
    }

    fun copy(c: Capsule, sender: Profile, friend: Friend): RemoteCapsule {
        require(eligible(c) && sender.uid != friend.uid)
        // A retry to the same friend overwrites only this delivery, never the road capsule.
        val key = UUID.nameUUIDFromBytes("${sender.uid}\u0000${c.remoteId}\u0000${friend.uid}".toByteArray())
        return RemoteCapsule(
            id = "forward_$key", ownerUid = sender.uid, ownerName = sender.name,
            createdAt = c.createdAt, lat = c.lat, lng = c.lng, radiusM = c.radiusM,
            placeName = c.placeName, durationSec = c.durationSec, tags = c.tags,
            rawVehicleState = shareableState(c.rawVehicleState), openAfter = null,
            audience = Audience.SOME, recipients = listOf(friend.uid), visibleTo = listOf(friend.uid),
            sourceRemoteId = c.remoteId, originalOwnerUid = c.ownerUid, originalOwnerName = c.ownerName,
        )
    }
}
