package app.timecapsule.social

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

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
    val companion: app.timecapsule.companion.CompanionStore = app.timecapsule.companion.FirebaseCompanionStore({ db }, { auth })

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

    private var listenerGeneration = 0L
    private var listeners = emptyList<ListenerRegistration>()
    private var mine = emptyList<RemoteCapsule>()
    private var forMe = emptyList<RemoteCapsule>()
    private var demo = emptyList<RemoteCapsule>()
    /** Which listeners (friends, mine, for me, demo) delivered a server snapshot. */
    private val fromServer = BooleanArray(4)

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
        val generation = ++listenerGeneration
        listeners.forEach { it.remove() }
        listeners = emptyList()
        _friends.value = emptyList()
        mine = emptyList(); forMe = emptyList(); demo = emptyList(); _remote.value = emptyList()
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
                if (generation != listenerGeneration || me.value?.uid != p.uid || auth.currentUser?.uid != p.uid) return@addSnapshotListener
                if (e != null) return@addSnapshotListener onError(e)
                _problem.value = null
                _friends.value = s!!.documents.map { Friend(it.id, it.getString("name") ?: "친구", it.getString("code") ?: "") }
                    .sortedBy { it.name }
                loaded(0, s)
            },
            db.collection("capsules").whereEqualTo("ownerUid", p.uid).addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { s, e ->
                if (generation != listenerGeneration || me.value?.uid != p.uid || auth.currentUser?.uid != p.uid) return@addSnapshotListener
                if (e != null) return@addSnapshotListener onError(e)
                mine = s!!.documents.mapNotNull(::toCapsule); publish(); loaded(1, s)
            },
            db.collection("capsules").whereArrayContains("visibleTo", p.uid).addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { s, e ->
                if (generation != listenerGeneration || me.value?.uid != p.uid || auth.currentUser?.uid != p.uid) return@addSnapshotListener
                if (e != null) return@addSnapshotListener onError(e)
                forMe = s!!.documents.mapNotNull(::toCapsule); publish(); loaded(2, s)
            },
            db.collection("capsules").whereEqualTo("audience", Audience.DEMO.name).addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { s, e ->
                if (generation != listenerGeneration || me.value?.uid != p.uid || auth.currentUser?.uid != p.uid) return@addSnapshotListener
                if (e != null) return@addSnapshotListener onError(e)
                demo = s!!.documents.mapNotNull(::toCapsule); publish(); loaded(3, s)
            },
        )
    }

    private fun publish() { _remote.value = (mine + forMe + demo).distinctBy { it.id } }

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
        removeFriendChecked(friend).onFailure { Log.w(TAG, "removeFriend", it) }
    }
    suspend fun removeFriendChecked(friend: Friend): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: error("먼저 로그인해 주세요.")
        check(me.value?.uid == uid) { "계정이 바뀌었어요. 다시 시도해 주세요." }
        withTimeout(WRITE_TIMEOUT_MS) {
            val batch = db.batch()
            batch.delete(db.collection("users").document(uid).collection("friends").document(friend.uid))
            batch.delete(db.collection("users").document(friend.uid).collection("friends").document(uid))
            batch.commit().await()
        }
        Unit
    }

    override suspend fun upload(capsule: RemoteCapsule, audio: ByteArray): Result<String> = runCatching {
        ensureAuth()
        // Same id on every retry, so a write that went through while offline is not duplicated.
        check(me.value?.uid == capsule.ownerUid && auth.currentUser?.uid == capsule.ownerUid) { "계정이 바뀌었어요. 다시 시도해 주세요" }
        val ref = db.collection("capsules").document(capsule.id.takeIf { capsule.sourceRemoteId != null && it.isNotBlank() }
            ?: "${capsule.ownerUid}_${capsule.createdAt}")
        withTimeout(WRITE_TIMEOUT_MS) {
            val audioRef = db.collection("audio").document(ref.id)
            val payload = mapOf("ownerUid" to capsule.ownerUid, "data" to Blob.fromBytes(audio))
            if (capsule.replyToId != null) {
                // Atomic, immutable retry: neither the voice nor the server deadline changes.
                db.runTransaction { tx ->
                    val existing = tx.get(ref)
                    if (!existing.exists()) {
                        tx.set(audioRef, payload)
                        tx.set(ref, toMap(capsule) + ("replySentAt" to FieldValue.serverTimestamp()))
                    }
                }.await()
            } else {
                audioRef.set(payload).await()
                ref.set(toMap(capsule)).await()
            }
        }
        ref.id
    }.onFailure { Log.w(TAG, "upload", it) }

    override suspend fun setVisibleTo(id: String, uids: List<String>) {
        runCatching { withTimeoutOrNull(WRITE_TIMEOUT_MS) { db.collection("capsules").document(id).update("visibleTo", uids).await() } }
            .onFailure { Log.w(TAG, "setVisibleTo", it) }
    }

    override suspend fun updateSharing(id: String, recipients: List<String>): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: error("먼저 로그인해 주세요.")
        val selected = recipients.distinct()
        check(selected.all { id -> friends.value.any { it.uid == id } }) { "연결된 친구만 선택해 주세요." }
        val ref = db.collection("capsules").document(id)
        withTimeout(WRITE_TIMEOUT_MS) {
            db.runTransaction { transaction ->
                val capsule = transaction.get(ref)
                check(capsule.getString("replyToId") == null) { "음성 답장은 원본 작성자에게만 보낼 수 있어요." }
                check(capsule.getString("ownerUid") == uid) { "내가 남긴 캡슐만 공개 범위를 바꿀 수 있어요." }
                transaction.update(ref, mapOf("visibleTo" to selected, "recipients" to selected,
                    "audience" to if (selected.isEmpty()) Audience.SELF.name else Audience.SOME.name))
            }.await()
        }
        Unit
    }

    override suspend fun delete(id: String) {
        runCatching {
            // One batch: both deletes are queued together even if the app dies while offline.
            val batch = db.batch()
            batch.delete(db.collection("capsules").document(id))
            batch.delete(db.collection("audio").document(id))
            withTimeoutOrNull(WRITE_TIMEOUT_MS) { batch.commit().await() }
        }.onFailure { Log.w(TAG, "delete", it) }
    }

    override suspend fun canOpen(id: String): Boolean = runCatching {
        val uid = auth.currentUser?.uid ?: return false
        val capsule = withTimeout(5_000) { db.collection("capsules").document(id).get(com.google.firebase.firestore.Source.SERVER).await() }
        val owner = capsule.getString("ownerUid") ?: return false
        val openAfter = capsule.getLong("openAfter")
        if (openAfter != null && java.time.LocalDate.now().toEpochDay() < openAfter) return false
        val replyMode = capsule.getString("replyMode")
        val availableAt = capsule.getTimestamp("replySentAt")?.toDate()?.time?.plus(app.timecapsule.companion.ReplyPolicy.WAIT_MS)
        if (app.timecapsule.companion.ReplyPolicy.waiting(replyMode, availableAt, System.currentTimeMillis())) return false
        if (replyMode == app.timecapsule.companion.ReplyPolicy.AFTER_THREE_DAYS) {
            // The server rule checks request.time, even if the device clock was advanced.
            withTimeout(5_000) { db.collection("audio").document(id).get(com.google.firebase.firestore.Source.SERVER).await() }
        }
        if (owner == uid) return true
        val friend = withTimeout(5_000) { db.collection("users").document(uid).collection("friends").document(owner).get(com.google.firebase.firestore.Source.SERVER).await() }
        friend.exists() && (capsule.getString("audience") == Audience.DEMO.name ||
            (capsule.get("visibleTo") as? List<*>)?.contains(uid) == true)
    }.getOrDefault(false)

    override suspend fun audio(id: String): ByteArray? = runCatching {
        withTimeout(WRITE_TIMEOUT_MS) { db.collection("audio").document(id).get(com.google.firebase.firestore.Source.SERVER).await() }.getBlob("data")?.toBytes()
    }.onFailure { Log.w(TAG, "audio", it) }.getOrNull()

    override suspend fun switchAccount(profile: Profile?) {
        val n = if (profile == null) (slots.all.values.filterIsInstance<Int>().maxOrNull() ?: 0) + 1
        else slots.getInt("uid:${profile.uid}", -1).takeIf { it >= 0 } ?: return
        useSlot(n)
        accounts.set(profile)
        ensureAuth()
    }

    override suspend fun linkEmail(email: String, password: String): Result<Unit> = runCatching {
        val session = auth
        val user = session.currentUser ?: error("인터넷에 연결되면 다시 해 주세요")
        require(user.isAnonymous) { "이미 이메일에 연결된 계정이에요" }
        require(password.length >= 6) { "비밀번호는 6자 이상이어야 해요" }
        withTimeout(WRITE_TIMEOUT_MS) {
            val token = user.getIdToken(false).await().token ?: error("다시 로그인해 주세요")
            // accounts:update is rejected when email enumeration protection is enabled.
            // signUp + idToken links credentials without replacing the anonymous UID.
            withContext(Dispatchers.IO) {
                val connection = URL("https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${options.apiKey}")
                    .openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 8_000
                    connection.readTimeout = 8_000
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    val payload = JSONObject().put("idToken", token).put("email", email.trim())
                        .put("password", password).put("returnSecureToken", true)
                    connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                    val status = connection.responseCode
                    val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                        ?.bufferedReader()?.use { JSONObject(it.readText()) }
                    if (status !in 200..299) {
                        val code = response?.optJSONObject("error")?.optString("message").orEmpty().substringBefore(" :")
                        error(when (code) {
                            "EMAIL_EXISTS" -> "이미 다른 계정에 연결된 이메일이에요"
                            "INVALID_EMAIL" -> "이메일 주소를 확인해 주세요"
                            "WEAK_PASSWORD" -> "더 안전한 비밀번호를 입력해 주세요"
                            "TOO_MANY_ATTEMPTS_TRY_LATER" -> "잠시 후 다시 시도해 주세요"
                            else -> "이메일 연결을 완료하지 못했어요. 인터넷 연결을 확인하고 다시 시도해 주세요"
                        })
                    }
                    check(response?.optString("localId") == user.uid) { "계정 연결을 확인하지 못했어요" }
                } catch (_: java.io.IOException) {
                    // Do not surface transport errors that may include the API key URL.
                    error("인터넷 연결을 확인하고 다시 시도해 주세요")
                } finally { connection.disconnect() }
            }
            check(auth === session && session.currentUser?.uid == user.uid) { "계정이 바뀌었어요. 다시 확인해 주세요" }
            user.reload().await()
            user.getIdToken(true).await()
        }
        check(auth === session && session.currentUser?.uid == user.uid) { "계정이 바뀌었어요. 다시 확인해 주세요" }
        _email.value = email.trim()
        Unit
    }.recoverCatching { throw IllegalStateException(authMessage(it)) }

    override suspend fun signInEmail(email: String, password: String): Result<Profile> {
        val previousSlot = slot
        val previousProfile = me.value
        return runCatching {
            // Keep an existing account intact until both authentication and profile loading succeed.
            useSlot((slots.all.values.filterIsInstance<Int>().maxOrNull() ?: 0) + 1)
            withTimeout(WRITE_TIMEOUT_MS) { auth.signInWithEmailAndPassword(email.trim(), password).await() }
            val uid = auth.currentUser!!.uid
            val d = withTimeout(WRITE_TIMEOUT_MS) { db.collection("users").document(uid).get(com.google.firebase.firestore.Source.SERVER).await() }
            val p = Profile(uid, d.getString("name") ?: error("이 계정에는 이름이 없어요"), d.getString("code") ?: "")
            slots.edit().putInt("uid:$uid", slot).apply()
            _email.value = email.trim()
            accounts.set(p)
            p
        }.recoverCatching { error ->
            auth.signOut()
            useSlot(previousSlot)
            _email.value = auth.currentUser?.email
            listen(previousProfile)
            if (error is kotlinx.coroutines.CancellationException && error !is kotlinx.coroutines.TimeoutCancellationException) throw error
            throw IllegalStateException(authMessage(error))
        }
    }

    override suspend fun resetPassword(email: String): Result<Unit> = runCatching {
        withTimeout(WRITE_TIMEOUT_MS) { auth.sendPasswordResetEmail(email.trim()).await() }
        Unit
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
        listen(null)
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
        "sourceRemoteId" to c.sourceRemoteId, "originalOwnerUid" to c.originalOwnerUid,
        "originalOwnerName" to c.originalOwnerName,
        "replyToId" to c.replyToId, "replyMode" to c.replyMode,
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
            sourceRemoteId = d.getString("sourceRemoteId"), originalOwnerUid = d.getString("originalOwnerUid"),
            originalOwnerName = d.getString("originalOwnerName"),
            replyToId = d.getString("replyToId"), replyMode = d.getString("replyMode"),
            replyAvailableAt = d.getTimestamp("replySentAt")?.toDate()?.time?.plus(app.timecapsule.companion.ReplyPolicy.WAIT_MS),
        )
    }.getOrNull()

    private companion object {
        const val TAG = "FirebaseBackend"
        const val APP_NAME = "timecapsule"
        const val WRITE_TIMEOUT_MS = 20_000L
        const val KEY_SLOT = "slot"
    }
}
