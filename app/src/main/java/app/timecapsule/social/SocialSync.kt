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
    /** Album: share one of my capsules with all friends, or take it back to just me. */
    fun setShared(capsule: Capsule, shared: Boolean)
}

/**
 * Keeps this car and the server in step:
 *  - my capsules for friends are uploaded (retried until they are);
 *  - "all friends" capsules retain their original recipient snapshot;
 *  - capsules friends left for me are downloaded with their audio, so the approach alert
 *    and playback files are cached; each new opening checks server access;
 *  - my capsules come back on a new car (기획서: 차를 바꿔도 이어짐).
 */
class SocialSync(
    context: Context,
    private val scope: CoroutineScope,
    private val backend: SocialBackend,
    private val repo: CapsuleRepository,
    private val settings: Settings,
    /** Where the car is now and what the place is called; a DEMO capsule is placed just ahead. */
    private val here: () -> Pair<app.timecapsule.geo.GeoPoint, String?>? = { null },
) : CapsuleSharing {
    private val dir = File(context.filesDir, "capsules").apply { mkdirs() }
    private val lock = Mutex()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    fun start() {
        scope.launch {
            combine(backend.remote, backend.friends, backend.synced) { r, f, s -> Triple(r, f, s) }
                .collect { (r, f, s) -> lock.withLock { pull(r, f, s) } }
        }
        scope.launch {
            combine(backend.me, backend.friends) { me, f -> me to f }.collect { (me, f) ->
                if (me == null) return@collect
                lock.withLock { pushPending(); followFriends(f) }
            }
        }
    }

    override fun onBuried(capsule: Capsule) {
        if (capsule.draft || capsule.locationUncertain || capsule.audience == Audience.SELF.name) return
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

    override fun setShared(capsule: Capsule, shared: Boolean) {
        scope.launch {
            lock.withLock {
                if (!capsule.isMine || capsule.draft || capsule.locationUncertain) return@withLock
                if (shared) {
                    repo.setSharing(capsule.id, Audience.FRIENDS.name, backend.friends.value.map { it.uid })
                    if (backend.me.value == null) _messages.tryEmit("친구 탭에서 이름을 정하면 친구에게도 전해져요")
                    else upload(capsule, announce = true)
                } else {
                    capsule.remoteId?.let { backend.delete(it) }
                    repo.setAudience(capsule.id, Audience.SELF.name)
                    _messages.tryEmit("공유 철회를 요청했어요. 친구 차의 사본은 연결 후 정리돼요")
                }
            }
        }
    }

    /** Send an independent copy only after checking the collected source against the server. */
    suspend fun forward(id: Long, friendUid: String, allowed: () -> Boolean): Result<Unit> = lock.withLock {
        Forwarding.send(backend, id, friendUid, allowed, repo::get) { capsuleId ->
            repo.lockFor(capsuleId)?.openAfter?.let { it <= java.time.LocalDate.now().toEpochDay() } != false
        }
    }

    private fun audienceUids(c: Capsule): List<String> = when (c.audience) {
        Audience.FRIENDS.name -> c.recipients.split(',').filter { id -> backend.friends.value.any { it.uid == id } }
        Audience.SOME.name -> c.recipients.split(',').filter { it.isNotBlank() }
        else -> emptyList()
    }

    private suspend fun upload(c: Capsule, announce: Boolean) {
        val me = backend.me.value ?: return
        val fresh = repo.get(c.id) ?: return
        if (fresh.draft || fresh.locationUncertain || fresh.remoteId != null || !fresh.isMine || fresh.audience == Audience.SELF.name) return
        val audio = runCatching { File(fresh.audioPath).readBytes() }.getOrNull() ?: return
        val visible = audienceUids(fresh)
        val r = RemoteCapsule(
            ownerUid = me.uid, ownerName = me.name, createdAt = fresh.createdAt,
            lat = fresh.lat, lng = fresh.lng, radiusM = fresh.radiusM, placeName = fresh.placeName,
            durationSec = fresh.durationSec, tags = fresh.tags, rawVehicleState = shareableState(fresh.rawVehicleState),
            openAfter = repo.lockFor(fresh.id)?.openAfter,
            audience = Audience.valueOf(fresh.audience),
            recipients = fresh.recipients.split(',').filter { it.isNotBlank() },
            visibleTo = visible,
        )
        backend.upload(r, audio).onSuccess { id ->
            repo.setRemoteId(fresh.id, id)
            if (announce) _messages.tryEmit(sentTo(fresh, visible))
        }.onFailure {
            if (announce) _messages.tryEmit("지금은 친구에게 보내지 못했어요. 연결되면 다시 보낼게요")
        }
    }


    private fun sentTo(c: Capsule, visible: List<String>): String {
        val names = backend.friends.value.filter { it.uid in visible }.map { it.name }
        return when {
            c.audience == Audience.FRIENDS.name && names.isEmpty() -> "공유할 친구가 없어요. 나중에 앨범에서 공개 대상을 다시 정해 주세요"
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

    /** Existing recipients can be removed, but newly added friends never receive older capsules. */
    private suspend fun followFriends(friends: List<Friend>) {
        val uids = friends.map { it.uid }.sorted()
        val myIds = repo.all().filter { it.isMine && it.audience == Audience.FRIENDS.name }.mapNotNull { it.remoteId }.toSet()
        backend.remote.value.filter { it.id in myIds }
            .forEach { remote ->
                val remaining = remote.visibleTo.filter { it in uids }
                if (remaining != remote.visibleTo) backend.setVisibleTo(remote.id, remaining)
            }
    }

    private suspend fun pull(all: List<RemoteCapsule>, friends: List<Friend>, synced: Boolean) {
        val me = backend.me.value ?: return
        val hidden = settings.hidden()
        // Only mine and current friends'. An ex-friend's capsule can still list me until their
        // car updates it; it must not reach me in the meantime.
        val friendUids = friends.map { it.uid }.toSet()
        val list = all.filter { (it.ownerUid == me.uid && it.sourceRemoteId == null) ||
            (it.ownerUid in friendUids && (me.uid in it.visibleTo || it.audience == Audience.DEMO)) }
        for (r in list) {
            if (r.id in hidden || repo.byRemoteId(r.id) != null) continue
            val bytes = backend.audio(r.id) ?: continue
            val ext = if (bytes.size > 4 && String(bytes, 0, 4) == "RIFF") "wav" else "m4a"
            // The folder goes away with "모두 지우기"; a failed write skips this one, it is retried next time.
            val file = runCatching { File(dir.apply { mkdirs() }, "remote_${r.id}.$ext").apply { writeBytes(bytes) } }
                .onFailure { Log.w(TAG, "cannot save ${r.id}", it) }.getOrNull() ?: continue
            val mine = r.ownerUid == me.uid
            // The 체험 친구 cannot know where a judge's car is: put its capsule ~500m from here.
            val spot = if (r.audience == Audience.DEMO) here() else null
            repo.insertFromServer(
                Capsule(
                    createdAt = r.createdAt,
                    lat = spot?.first?.let { it.lat + 0.0045 } ?: r.lat, lng = spot?.first?.lng ?: r.lng, radiusM = r.radiusM,
                    placeName = spot?.let { it.second ?: "이 근처" } ?: r.placeName, audioPath = file.absolutePath, durationSec = r.durationSec,
                    tags = r.tags, rawVehicleState = r.rawVehicleState, carModel = null, odometerKm = null,
                    ownerUid = if (mine) null else r.ownerUid, ownerName = if (mine) null else r.ownerName,
                    audience = r.audience.name, recipients = r.recipients.joinToString(","), remoteId = r.id,
                    sourceRemoteId = r.sourceRemoteId, originalOwnerUid = r.originalOwnerUid,
                    originalOwnerName = r.originalOwnerName,
                ),
                r.openAfter,
            )
            if (!mine) Log.i(TAG, "received ${r.id} from ${r.ownerName}")
        }
        // Gone (owner deleted it, unfriended, or no longer for me): drop the copy on this car.
        // Only against a full server answer, never a half-loaded or offline one.
        if (!synced) return
        val keep = list.map { it.id }.toSet()
        repo.all().filter { !it.isMine && it.remoteId != null && it.remoteId !in keep }.forEach {
            repo.delete(it)
            Log.i(TAG, "removed ${it.remoteId}")
        }
    }

    private companion object { const val TAG = "SocialSync" }
}

/** What a friend gets of the car state: the day (weather, people), not my odometer or car model. */
internal fun shareableState(raw: String): String =
    raw.replace(Regex(""""(odometerKm|carModel)"\s*:\s*("(?:[^"\\]|\\.)*"|[^,}]+),?"""), "").replace(",}", "}")
