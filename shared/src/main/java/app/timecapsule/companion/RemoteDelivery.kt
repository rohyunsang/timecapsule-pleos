package app.timecapsule.companion

import app.timecapsule.social.*
import kotlinx.coroutines.CancellationException
import java.util.UUID

/** Idempotent, explicit forwarding of a collected friend's capsule, never an owner's private one. */
object RemoteDelivery {
    fun eligible(c: RemoteCapsule, uid: String, collected: Boolean) = c.ownerUid != uid && collected && c.sourceRemoteId == null && c.replyToId == null
    suspend fun send(backend: SocialBackend, id: String, recipientUid: String,
                     isCollected: (String) -> Boolean, current: (String) -> RemoteCapsule?): Result<Unit> = try {
        val me = backend.me.value ?: error("먼저 로그인해 주세요.")
        val source = current(id) ?: error("원본 캡슐을 찾지 못했어요.")
        check(eligible(source, me.uid, isCollected(id))) { "직접 수집한 친구 캡슐만 전달할 수 있어요." }
        check(backend.canOpen(id)) { "원본의 공유 권한을 확인하지 못했어요." }
        val audio = backend.audio(id) ?: error("음성을 불러오지 못했어요.")
        val friend = backend.friends.value.firstOrNull { it.uid == recipientUid } ?: error("연결된 친구가 아니에요.")
        check(backend.me.value?.uid == me.uid && current(id) == source && isCollected(id)) { "상태가 바뀌었어요. 다시 시도해 주세요." }
        check(backend.canOpen(id)) { "원본 공유가 변경되었어요. 다시 확인해 주세요." }
        check(backend.me.value?.uid == me.uid && backend.friends.value.any { it.uid == recipientUid }) { "계정이나 친구 상태가 바뀌었어요." }
        backend.upload(copy(source, me, friend), audio).getOrThrow()
        Result.success(Unit)
    } catch (e: CancellationException) { throw e }
      catch (e: Exception) { Result.failure(e) }

    fun copy(source: RemoteCapsule, sender: Profile, friend: Friend): RemoteCapsule {
        require(source.sourceRemoteId == null && source.ownerUid != sender.uid && sender.uid != friend.uid)
        val key = UUID.nameUUIDFromBytes("${sender.uid}\u0000${source.id}\u0000${friend.uid}".toByteArray())
        return source.copy(id = "forward_$key", ownerUid = sender.uid, ownerName = sender.name,
            openAfter = null, audience = Audience.SOME, recipients = listOf(friend.uid), visibleTo = listOf(friend.uid),
            sourceRemoteId = source.id, originalOwnerUid = source.ownerUid, originalOwnerName = source.ownerName)
    }
}
