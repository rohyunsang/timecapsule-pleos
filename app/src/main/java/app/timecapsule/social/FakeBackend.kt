package app.timecapsule.social

import android.content.Context
import app.timecapsule.geo.GeoPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/**
 * In-memory server used until Firebase keys are set (and for demos without network).
 * Two people already exist: 민수 (code MNSU27) and 지영 (code JYNG38). Adding one of them as a
 * friend works like the real thing, and the dev panel can make them leave a capsule nearby.
 */
class FakeBackend(context: Context) : SocialBackend {
    override val label = "가짜 서버"

    private val accounts = Accounts(context, "social_fake")
    override val me: StateFlow<Profile?> = accounts.current
    override val knownAccounts: StateFlow<List<Profile>> = accounts.known

    private val directory = mutableMapOf(
        "MNSU27" to Profile("fake-minsu", "민수", "MNSU27"),
        "JYNG38" to Profile("fake-jiyoung", "지영", "JYNG38"),
    )

    /** uid -> friend uids. Kept across restarts so a demo setup survives; capsules are not. */
    private val linkPrefs = context.getSharedPreferences("social_fake_links", Context.MODE_PRIVATE)
    private val links: MutableMap<String, MutableSet<String>> =
        linkPrefs.all.mapValues { (_, v) -> (v as? Set<*>).orEmpty().map { it.toString() }.toMutableSet() }.toMutableMap()

    private fun saveLinks() = linkPrefs.edit().apply { clear(); links.forEach { (k, v) -> putStringSet(k, v) } }.apply()
    private val capsules = mutableMapOf<String, RemoteCapsule>()
    private val audioById = mutableMapOf<String, ByteArray>()
    private var seq = 0

    private val _friends = MutableStateFlow<List<Friend>>(emptyList())
    override val friends: StateFlow<List<Friend>> = _friends.asStateFlow()
    private val _remote = MutableStateFlow<List<RemoteCapsule>>(emptyList())
    override val remote: StateFlow<List<RemoteCapsule>> = _remote.asStateFlow()
    override val problem: StateFlow<String?> = MutableStateFlow(null)
    private val _synced = MutableStateFlow(false)
    override val synced: StateFlow<Boolean> = _synced.asStateFlow()

    /** email -> "password|uid", kept across restarts like the friend links. */
    private val emails = context.getSharedPreferences("social_fake_emails", Context.MODE_PRIVATE)
    private val _email = MutableStateFlow<String?>(null)
    override val email: StateFlow<String?> = _email.asStateFlow()

    override suspend fun linkEmail(email: String, password: String): Result<Unit> {
        val p = me.value ?: return Result.failure(IllegalStateException("먼저 이름을 정해 주세요"))
        if (password.length < 6) return Result.failure(IllegalArgumentException("비밀번호는 6자 이상이어야 해요"))
        if (emails.contains(email.trim())) return Result.failure(IllegalArgumentException("이미 다른 계정에 연결된 이메일이에요"))
        emails.edit().putString(email.trim(), "$password|${p.uid}").apply()
        _email.value = email.trim()
        return Result.success(Unit)
    }

    override suspend fun signInEmail(email: String, password: String): Result<Profile> {
        val v = emails.getString(email.trim(), null) ?: return Result.failure(IllegalArgumentException("그 이메일로 된 계정이 없어요"))
        if (v.substringBefore('|') != password) return Result.failure(IllegalArgumentException("이메일이나 비밀번호가 맞지 않아요"))
        val p = knownAccounts.value.firstOrNull { it.uid == v.substringAfter('|') }
            ?: return Result.failure(IllegalStateException("이 차에서 만든 계정만 불러올 수 있어요(가짜 서버)"))
        switchAccount(p)
        return Result.success(p)
    }

    private fun emailOf(uid: String?) = emails.all.entries.firstOrNull { (it.value as? String)?.substringAfter('|') == uid }?.key

    init {
        knownAccounts.value.forEach { directory[it.code] = it }
        refresh()
    }

    private fun refresh() {
        val p = me.value
        _email.value = emailOf(p?.uid)
        if (p == null) { _friends.value = emptyList(); _remote.value = emptyList(); _synced.value = false; return }
        _friends.value = links[p.uid].orEmpty().mapNotNull { uid -> directory.values.firstOrNull { it.uid == uid } }
            .map { Friend(it.uid, it.name, it.code) }.sortedBy { it.name }
        _remote.value = capsules.values.filter { it.ownerUid == p.uid || p.uid in it.visibleTo }
        _synced.value = true
    }

    override suspend fun signUp(name: String): Result<Profile> {
        var code: String
        do { code = FriendCode.random() } while (code in directory)
        val p = Profile("fake-${System.currentTimeMillis()}", name, code)
        directory[code] = p
        accounts.set(p)
        refresh()
        return Result.success(p)
    }

    override suspend fun addFriend(code: String): Result<Friend> {
        val me = me.value ?: return Result.failure(IllegalStateException("먼저 이름을 정해 주세요"))
        val c = FriendCode.normalize(code) ?: return Result.failure(IllegalArgumentException("코드는 영문과 숫자 6자리예요"))
        if (c == me.code) return Result.failure(IllegalArgumentException("내 코드예요"))
        val other = directory[c] ?: return Result.failure(IllegalArgumentException("없는 코드예요"))
        links.getOrPut(me.uid) { mutableSetOf() } += other.uid
        links.getOrPut(other.uid) { mutableSetOf() } += me.uid
        saveLinks()
        refresh()
        return Result.success(Friend(other.uid, other.name, other.code))
    }

    override suspend fun removeFriend(friend: Friend) {
        val me = me.value ?: return
        links[me.uid]?.remove(friend.uid)
        links[friend.uid]?.remove(me.uid)
        saveLinks()
        refresh()
    }

    override suspend fun upload(capsule: RemoteCapsule, audio: ByteArray): Result<String> {
        val id = "fake-capsule-${++seq}"
        capsules[id] = capsule.copy(id = id)
        audioById[id] = audio
        refresh()
        return Result.success(id)
    }

    override suspend fun setVisibleTo(id: String, uids: List<String>) {
        capsules[id]?.let { capsules[id] = it.copy(visibleTo = uids) }
        refresh()
    }

    override suspend fun delete(id: String) {
        capsules.remove(id); audioById.remove(id)
        refresh()
    }

    override suspend fun audio(id: String): ByteArray? = audioById[id]

    override suspend fun signOut() = switchAccount(null)

    override suspend fun switchAccount(profile: Profile?) {
        accounts.set(profile)
        profile?.let { directory[it.code] = it }
        refresh()
    }

    /**
     * Dev panel: [friend] leaves a capsule for me at [at] (a short tone stands in for the
     * voice, since the fake friend cannot speak).
     */
    fun friendLeavesCapsule(friend: Friend, at: GeoPoint, placeName: String?, daysAgo: Long = 0) {
        val me = me.value ?: return
        val id = "fake-capsule-${++seq}"
        capsules[id] = RemoteCapsule(
            id = id, ownerUid = friend.uid, ownerName = friend.name,
            createdAt = System.currentTimeMillis() - daysAgo * 86_400_000L,
            lat = at.lat, lng = at.lng, radiusM = 300, placeName = placeName, durationSec = 3,
            tags = "밤길|둘이서", rawVehicleState = "{}", openAfter = null,
            audience = Audience.FRIENDS, recipients = emptyList(), visibleTo = listOf(me.uid),
        )
        audioById[id] = toneWav()
        refresh()
    }

    /** 3 s of two soft alternating tones as a WAV file. */
    private fun toneWav(seconds: Int = 3, rate: Int = 16_000): ByteArray {
        val n = seconds * rate
        val pcm = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until n) {
            val f = if ((i / (rate / 2)) % 2 == 0) 523.0 else 659.0
            pcm.putShort((sin(2 * PI * f * i / rate) * 6000).toInt().toShort())
        }
        val out = ByteArrayOutputStream()
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()).putInt(36 + n * 2).put("WAVE".toByteArray())
        h.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        h.put("data".toByteArray()).putInt(n * 2)
        out.write(h.array()); out.write(pcm.array())
        return out.toByteArray()
    }
}
