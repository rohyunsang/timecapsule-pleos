package app.timecapsule.social

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Firebase, free (Spark) plan: anonymous Auth + Firestore only. Both work without Google Play
 * services (Pleos head units have none). Audio is stored as a Firestore blob in its own
 * document (a 30 s clip at 64 kbps is ~240 KB, under the 1 MB document limit), so Cloud
 * Storage, which needs the paid plan, is not used.
 *
 * Firestore layout (rules: firebase/firestore.rules):
 *   users/{uid}                 name, code, createdAt
 *   users/{uid}/friends/{fuid}  name, code, since
 *   codes/{CODE}                uid, name                 (friend code -> account)
 *   capsules/{id}               ownerUid, ownerName, ..., visibleTo[]  (who may receive it)
 *   audio/{id}                  ownerUid, data(blob)
 */
class FirebaseBackend(
    private val context: Context,
    private val scope: CoroutineScope,
    apiKey: String,
    appId: String,
    projectId: String,
) : SocialBackend {
    override val label = "Firebase"

    private val options = FirebaseOptions.Builder().setApiKey(apiKey).setApplicationId(appId).setProjectId(projectId).build()
    private val accounts = Accounts(context, "social_firebase")

    /**
     * Each person on this car gets their own named FirebaseApp ("slot"), because an anonymous
     * account cannot be signed back into once signed out. That lets the dev panel switch
     * between "me" and "my friend" on one emulator. A real car uses slot 0 only.
     */
    private val slots = context.getSharedPreferences("firebase_slots", Context.MODE_PRIVATE)
    private var slot = slots.getInt(KEY_SLOT, 0)
    private var db = FirebaseFirestore.getInstance(appFor(slot))
    private var auth = FirebaseAuth.getInstance(appFor(slot))

    private fun appFor(n: Int): FirebaseApp = "${APP_NAME}_$n".let { name ->
        FirebaseApp.getApps(context).firstOrNull { it.name == name } ?: FirebaseApp.initializeApp(context, options, name)
    }

    private fun useSlot(n: Int) {
        slot = n
        slots.edit().putInt(KEY_SLOT, n).apply()
        db = FirebaseFirestore.getInstance(appFor(n))
        auth = FirebaseAuth.getInstance(appFor(n))
    }

    override val me: StateFlow<Profile?> = accounts.current
    override val knownAccounts: StateFlow<List<Profile>> = accounts.known

    private val _friends = MutableStateFlow<List<Friend>>(emptyList())
    override val friends: StateFlow<List<Friend>> = _friends.asStateFlow()

    private val _remote = MutableStateFlow<List<RemoteCapsule>>(emptyList())
    override val remote: StateFlow<List<RemoteCapsule>> = _remote.asStateFlow()

    private val _problem = MutableStateFlow<String?>(null)
    override val problem: StateFlow<String?> = _problem.asStateFlow()

    private val _synced = MutableStateFlow(false)
    override val synced: StateFlow<Boolean> = _synced.asStateFlow()

    private val _email = MutableStateFlow<String?>(null)
    override val email: StateFlow<String?> = _email.asStateFlow()

    private var listeners = emptyList<ListenerRegistration>()
    private var mine = emptyList<RemoteCapsule>()
    private var forMe = emptyList<RemoteCapsule>()
    /** Which of the three listeners (friends, mine, for me) delivered a server snapshot. */
    private val fromServer = BooleanArray(3)

    init {
        scope.launch {
            ensureAuth()
            me.collect { listen(it) }
        }
    }

    /**
     * Anonymous sign-in gives the rules a request.auth. If it fails (no network yet), the
     * account still works locally and Firestore retries when it can.
     */
    private suspend fun ensureAuth() {
        _email.value = auth.currentUser?.email?.takeIf { it.isNotBlank() }
        if (auth.currentUser != null) return
        val ok = withTimeoutOrNull(10_000) { runCatching { auth.signInAnonymously().await() }.isSuccess } ?: false
        if (!ok) Log.w(TAG, "anonymous sign-in failed; continuing")
    }

    private fun listen(p: Profile?) {
        listeners.forEach { it.remove() }
        listeners = emptyList()
        _friends.value = emptyList()
        mine = emptyList(); forMe = emptyList(); _remote.value = emptyList()
        fromServer.fill(false); _synced.value = false
        _email.value = auth.currentUser?.email?.takeIf { it.isNotBlank() }
        if (p == null) return
        fun loaded(i: Int, s: com.google.firebase.firestore.QuerySnapshot) {
            if (!s.metadata.isFromCache) fromServer[i] = true
            _synced.value = fromServer.all { it }
        }
        val onError = { e: Exception -> _problem.value = "서버에 연결하지 못했어요"; Log.w(TAG, "listen", e); Unit }
        listeners = listOf(
            db.collection("users").document(p.uid).collection("friends").addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { s, e ->
                if (e != null) return@addSnapshotListener onError(e)
                _problem.value = null
                _friends.value = s!!.documents.map { Friend(it.id, it.getString("name") ?: "친구", it.getString("code") ?: "") }
                    .sortedBy { it.name }
                loaded(0, s)
            },
            db.collection("capsules").whereEqualTo("ownerUid", p.uid).addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { s, e ->
                if (e != null) return@addSnapshotListener onError(e)
                mine = s!!.documents.mapNotNull(::toCapsule); publish(); loaded(1, s)
            },
            db.collection("capsules").whereArrayContains("visibleTo", p.uid).addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { s, e ->
                if (e != null) return@addSnapshotListener onError(e)
                forMe = s!!.documents.mapNotNull(::toCapsule); publish(); loaded(2, s)
            },
        )
    }

    private fun publish() { _remote.value = (mine + forMe).distinctBy { it.id } }

    override suspend fun signUp(name: String): Result<Profile> = runCatching {
        ensureAuth()
        // The account is the anonymous Firebase user, so the rules can check request.auth.uid.
        val uid = auth.currentUser?.uid ?: error("인터넷에 연결되면 다시 해 주세요")
        // A fresh code that nobody has, claimed in a transaction.
        var code = ""
        for (attempt in 0 until 5) {
            val c = FriendCode.random()
            val claimed = db.runTransaction { t ->
                val ref = db.collection("codes").document(c)
                if (t.get(ref).exists()) false else { t.set(ref, mapOf("uid" to uid, "name" to name)); true }
            }.await()
            if (claimed) { code = c; break }
        }
        check(code.isNotEmpty()) { "친구 코드를 만들지 못했어요" }
        db.collection("users").document(uid)
            .set(mapOf("name" to name, "code" to code, "createdAt" to FieldValue.serverTimestamp())).await()
        slots.edit().putInt("uid:$uid", slot).apply()
        Profile(uid, name, code).also(accounts::set)
    }.onFailure { Log.w(TAG, "signUp", it) }

    override suspend fun addFriend(code: String): Result<Friend> = runCatching {
        val me = me.value ?: error("먼저 이름을 정해 주세요")
        val c = FriendCode.normalize(code) ?: error("코드는 영문과 숫자 6자리예요")
        require(c != me.code) { "내 코드예요" }
        val doc = db.collection("codes").document(c).get().await()
        val uid = doc.getString("uid") ?: error("없는 코드예요")
        val name = doc.getString("name") ?: "친구"
        val batch = db.batch()
        batch.set(db.collection("users").document(me.uid).collection("friends").document(uid),
            mapOf("name" to name, "code" to c, "since" to FieldValue.serverTimestamp()))
        batch.set(db.collection("users").document(uid).collection("friends").document(me.uid),
            mapOf("name" to me.name, "code" to me.code, "since" to FieldValue.serverTimestamp()))
        withTimeout(WRITE_TIMEOUT_MS) { batch.commit().await() }
        Friend(uid, name, c)
    }.onFailure { Log.w(TAG, "addFriend", it) }

    override suspend fun removeFriend(friend: Friend) {
        val me = me.value ?: return
        runCatching {
            val batch = db.batch()
            batch.delete(db.collection("users").document(me.uid).collection("friends").document(friend.uid))
            batch.delete(db.collection("users").document(friend.uid).collection("friends").document(me.uid))
            batch.commit().await()
        }.onFailure { Log.w(TAG, "removeFriend", it) }
    }

    override suspend fun upload(capsule: RemoteCapsule, audio: ByteArray): Result<String> = runCatching {
        ensureAuth()
        // Same id on every retry, so a write that went through while offline is not duplicated.
        val ref = db.collection("capsules").document("${capsule.ownerUid}_${capsule.createdAt}")
        // Audio first: a capsule doc never points at missing audio.
        withTimeout(WRITE_TIMEOUT_MS) {
            db.collection("audio").document(ref.id)
                .set(mapOf("ownerUid" to capsule.ownerUid, "data" to Blob.fromBytes(audio))).await()
            ref.set(toMap(capsule)).await()
        }
        ref.id
    }.onFailure { Log.w(TAG, "upload", it) }

    override suspend fun setVisibleTo(id: String, uids: List<String>) {
        runCatching { db.collection("capsules").document(id).update("visibleTo", uids).await() }
            .onFailure { Log.w(TAG, "setVisibleTo", it) }
    }

    override suspend fun delete(id: String) {
        runCatching {
            db.collection("capsules").document(id).delete().await()
            db.collection("audio").document(id).delete().await()
        }.onFailure { Log.w(TAG, "delete", it) }
    }

    override suspend fun audio(id: String): ByteArray? = runCatching {
        withTimeout(WRITE_TIMEOUT_MS) { db.collection("audio").document(id).get().await() }.getBlob("data")?.toBytes()
    }.onFailure { Log.w(TAG, "audio", it) }.getOrNull()

    override suspend fun switchAccount(profile: Profile?) {
        val n = if (profile == null) (slots.all.values.filterIsInstance<Int>().maxOrNull() ?: 0) + 1
        else slots.getInt("uid:${profile.uid}", -1).takeIf { it >= 0 } ?: return
        useSlot(n)
        accounts.set(profile)
        ensureAuth()
    }

    override suspend fun linkEmail(email: String, password: String): Result<Unit> = runCatching {
        val user = auth.currentUser ?: error("인터넷에 연결되면 다시 해 주세요")
        withTimeout(WRITE_TIMEOUT_MS) { user.linkWithCredential(EmailAuthProvider.getCredential(email.trim(), password)).await() }
        _email.value = email.trim()
        Unit
    }.recoverCatching { throw IllegalStateException(authMessage(it)) }

    override suspend fun signInEmail(email: String, password: String): Result<Profile> = runCatching {
        // Keep the person already on this car in their own slot; load this one into a fresh slot.
        if (me.value != null) useSlot((slots.all.values.filterIsInstance<Int>().maxOrNull() ?: 0) + 1)
        withTimeout(WRITE_TIMEOUT_MS) { auth.signInWithEmailAndPassword(email.trim(), password).await() }
        val uid = auth.currentUser!!.uid
        val d = withTimeout(WRITE_TIMEOUT_MS) { db.collection("users").document(uid).get().await() }
        val p = Profile(uid, d.getString("name") ?: error("이 계정에는 이름이 없어요"), d.getString("code") ?: "")
        slots.edit().putInt("uid:$uid", slot).apply()
        _email.value = email.trim()
        accounts.set(p)
        p
    }.recoverCatching { throw IllegalStateException(authMessage(it)) }

    private fun authMessage(e: Throwable): String = when (e) {
        is FirebaseAuthWeakPasswordException -> "비밀번호는 6자 이상이어야 해요"
        is FirebaseAuthUserCollisionException -> "이미 다른 계정에 연결된 이메일이에요"
        is FirebaseAuthInvalidUserException -> "그 이메일로 된 계정이 없어요"
        is FirebaseAuthInvalidCredentialsException -> "이메일이나 비밀번호가 맞지 않아요"
        is kotlinx.coroutines.TimeoutCancellationException -> "인터넷 연결을 확인해 주세요"
        else -> e.message ?: "다시 해 주세요"
    }.also { Log.w(TAG, "auth", e) }

    override suspend fun signOut() {
        auth.signOut()
        accounts.set(null)
        ensureAuth()
    }

    private fun toMap(c: RemoteCapsule) = mapOf(
        "ownerUid" to c.ownerUid, "ownerName" to c.ownerName, "createdAt" to c.createdAt,
        "lat" to c.lat, "lng" to c.lng, "radiusM" to c.radiusM, "placeName" to c.placeName,
        "durationSec" to c.durationSec, "tags" to c.tags, "rawVehicleState" to c.rawVehicleState,
        "openAfter" to c.openAfter, "audience" to c.audience.name, "recipients" to c.recipients,
        "visibleTo" to c.visibleTo,
    )

    @Suppress("UNCHECKED_CAST")
    private fun toCapsule(d: DocumentSnapshot): RemoteCapsule? = runCatching {
        RemoteCapsule(
            id = d.id,
            ownerUid = d.getString("ownerUid")!!,
            ownerName = d.getString("ownerName") ?: "친구",
            createdAt = d.getLong("createdAt")!!,
            lat = d.getDouble("lat")!!,
            lng = d.getDouble("lng")!!,
            radiusM = (d.getLong("radiusM") ?: 300).toInt(),
            placeName = d.getString("placeName"),
            durationSec = (d.getLong("durationSec") ?: 0).toInt(),
            tags = d.getString("tags") ?: "",
            rawVehicleState = d.getString("rawVehicleState") ?: "{}",
            openAfter = d.getLong("openAfter"),
            audience = runCatching { Audience.valueOf(d.getString("audience")!!) }.getOrDefault(Audience.FRIENDS),
            recipients = (d.get("recipients") as? List<String>).orEmpty(),
            visibleTo = (d.get("visibleTo") as? List<String>).orEmpty(),
        )
    }.getOrNull()

    private companion object {
        const val TAG = "FirebaseBackend"
        const val APP_NAME = "timecapsule"
        const val WRITE_TIMEOUT_MS = 20_000L
        const val KEY_SLOT = "slot"
    }
}
