package app.timecapsule.social

import android.content.Context
import android.util.Log
import app.timecapsule.Settings
import app.timecapsule.capsule.CapsuleRepository
import app.timecapsule.data.Capsule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/** What the drive controller tells the sharing layer. */
interface CapsuleSharing {
    fun onBuried(capsule: Capsule)
    fun onDeleted(capsule: Capsule)
}

/**
 * Keeps this car and the server in step:
 *  - my capsules for friends are uploaded (retried until they are);
 *  - "all friends" capsules follow the friend list (a new friend also gets the old ones);
 *  - capsules friends left for me are downloaded with their audio, so the approach alert
 *    and playback work without network on the road;
 *  - my capsules come back on a new car (기획서: 차를 바꿔도 이어짐).
 */
class SocialSync(
    context: Context,
    private val scope: CoroutineScope,
    private val backend: SocialBackend,
    private val repo: CapsuleRepository,
    private val settings: Settings,
) : CapsuleSharing {
    private val dir = File(context.filesDir, "capsules").apply { mkdirs() }
    private val lock = Mutex()
    /** Server ids seen in this session, to tell "deleted on the server" from "not loaded yet". */
    private var seen = emptySet<String>()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    fun start() {
        scope.launch { backend.remote.collect { lock.withLock { pull(it) } } }
        scope.launch {
            combine(backend.me, backend.friends) { me, f -> me to f }.collect { (me, f) ->
                if (me == null) return@collect
                lock.withLock { pushPending(); followFriends(f) }
            }
        }
    }

    override fun onBuried(capsule: Capsule) {
        if (capsule.audience == Audience.SELF.name) return
        if (backend.me.value == null) {
            _messages.tryEmit("친구 탭에서 이름을 정하면 친구에게도 전해져요")
            return
        }
        scope.launch { lock.withLock { upload(capsule, announce = true) } }
    }

    override fun onDeleted(capsule: Capsule) {
        val rid = capsule.remoteId ?: return
        scope.launch {
            if (capsule.isMine) backend.delete(rid) else settings.hide(rid)
        }
    }

    private fun audienceUids(c: Capsule): List<String> = when (c.audience) {
        Audience.FRIENDS.name -> backend.friends.value.map { it.uid }
        Audience.SOME.name -> c.recipients.split(',').filter { it.isNotBlank() }
        else -> emptyList()
    }

    private suspend fun upload(c: Capsule, announce: Boolean) {
        val me = backend.me.value ?: return
        val fresh = repo.get(c.id) ?: return
        if (fresh.remoteId != null || !fresh.isMine || fresh.audience == Audience.SELF.name) return
        val audio = runCatching { File(fresh.audioPath).readBytes() }.getOrNull() ?: return
        val visible = audienceUids(fresh)
        val r = RemoteCapsule(
            ownerUid = me.uid, ownerName = me.name, createdAt = fresh.createdAt,
            lat = fresh.lat, lng = fresh.lng, radiusM = fresh.radiusM, placeName = fresh.placeName,
            durationSec = fresh.durationSec, tags = fresh.tags, rawVehicleState = fresh.rawVehicleState,
            openAfter = repo.lockFor(fresh.id)?.openAfter,
            audience = Audience.valueOf(fresh.audience),
            recipients = fresh.recipients.split(',').filter { it.isNotBlank() },
            visibleTo = visible,
        )
        backend.upload(r, audio).onSuccess { id ->
            repo.setRemoteId(fresh.id, id)
            seen = seen + id
            if (announce) _messages.tryEmit(sentTo(fresh, visible))
        }.onFailure {
            if (announce) _messages.tryEmit("지금은 친구에게 보내지 못했어요. 연결되면 다시 보낼게요")
        }
    }

    private fun sentTo(c: Capsule, visible: List<String>): String {
        val names = backend.friends.value.filter { it.uid in visible }.map { it.name }
        return when {
            c.audience == Audience.FRIENDS.name && names.isEmpty() -> "친구를 추가하면 이 캡슐도 함께 전해져요"
            names.size == 1 -> "${names[0]}님에게 남겼어요"
            c.audience == Audience.FRIENDS.name -> "친구 ${names.size}명에게 남겼어요"
            else -> "${names.first()}님 외 ${names.size - 1}명에게 남겼어요"
        }
    }

    /** Capsules buried before signing in or while offline. */
    private suspend fun pushPending() {
        repo.all().filter { it.isMine && it.remoteId == null && it.audience != Audience.SELF.name }
            .forEach { upload(it, announce = false) }
    }

    /** "All friends" capsules: who may receive them follows the current friend list. */
    private suspend fun followFriends(friends: List<Friend>) {
        val uids = friends.map { it.uid }.sorted()
        val myIds = repo.all().filter { it.isMine && it.audience == Audience.FRIENDS.name }.mapNotNull { it.remoteId }.toSet()
        backend.remote.value.filter { it.id in myIds && it.visibleTo.sorted() != uids }
            .forEach { backend.setVisibleTo(it.id, uids) }
    }

    private suspend fun pull(list: List<RemoteCapsule>) {
        val me = backend.me.value ?: return
        val hidden = settings.hidden()
        for (r in list) {
            if (r.id in hidden || repo.byRemoteId(r.id) != null) continue
            val bytes = backend.audio(r.id) ?: continue
            val ext = if (bytes.size > 4 && String(bytes, 0, 4) == "RIFF") "wav" else "m4a"
            val file = File(dir, "remote_${r.id}.$ext").apply { writeBytes(bytes) }
            val mine = r.ownerUid == me.uid
            repo.insertFromServer(
                Capsule(
                    createdAt = r.createdAt, lat = r.lat, lng = r.lng, radiusM = r.radiusM,
                    placeName = r.placeName, audioPath = file.absolutePath, durationSec = r.durationSec,
                    tags = r.tags, rawVehicleState = r.rawVehicleState, carModel = null, odometerKm = null,
                    ownerUid = if (mine) null else r.ownerUid, ownerName = if (mine) null else r.ownerName,
                    audience = r.audience.name, recipients = r.recipients.joinToString(","), remoteId = r.id,
                ),
                r.openAfter,
            )
            if (!mine) Log.i(TAG, "received ${r.id} from ${r.ownerName}")
        }
        // Gone from the server (owner deleted it, or unfriended): drop friends' copies here.
        val now = list.map { it.id }.toSet()
        (seen - now).forEach { id ->
            repo.byRemoteId(id)?.takeIf { !it.isMine }?.let { repo.delete(it) }
        }
        seen = now
    }

    private companion object { const val TAG = "SocialSync" }
}
