package app.timecapsule.companion

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/** Bound through providers because signing into another account can change Firebase slots. */
class FirebaseCompanionStore(
    private val database: () -> FirebaseFirestore,
    private val authentication: () -> FirebaseAuth,
) : CompanionStore {
    private fun uid() = authentication().currentUser?.uid ?: error("먼저 로그인해 주세요")
    private fun user() = database().collection("users").document(uid())
    private fun id(value: String): String = value.also { require(it.isNotBlank() && '/' !in it && it.length <= 256) }
    private suspend fun <T> request(block: suspend () -> T): T = withTimeout(20_000) { block() }

    override fun observe(uid: String): Flow<CompanionSnapshot> = callbackFlow {
        require(uid == uid()) { "계정이 바뀌었어요. 다시 로그인해 주세요." }
        val db = database()
        val user = db.collection("users").document(uid)
        var state = CompanionSnapshot()
        val loaded = mutableSetOf<String>()
        val failures = mutableSetOf<String>()
        fun publish() {
            trySend(state.copy(loading = loaded.size < 4, error = if (failures.isEmpty()) null else "일부 기록을 동기화하지 못했어요. 연결과 계정을 확인해 주세요."))
        }
        fun listen(key: String, query: Query, update: (List<DocumentSnapshot>) -> Unit): ListenerRegistration =
            query.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) { failures.add(key); publish() }
                else if (snapshot != null) {
                    failures.remove(key)
                    if (!snapshot.metadata.isFromCache && !snapshot.metadata.hasPendingWrites()) loaded.add(key)
                    update(snapshot.documents)
                    publish()
                }
            }
        val listeners = listOf(
            listen("memories", user.collection("memories")) { docs ->
                state = state.copy(memories = docs.map { CapsuleMemory(it.id, it.getString("title").orEmpty(), it.getString("note").orEmpty(), it.getBoolean("favorite") == true, it.getBoolean("visitLater") == true) })
            },
            listen("collections", user.collection("collections")) { docs ->
                state = state.copy(collections = docs.mapNotNull { d -> d.getTimestamp("collectedAt")?.let { CollectionReceipt(d.id, it.toDate().time, d.getString("device").orEmpty()) } })
            },
            listen("albums", db.collection("memoryAlbums").whereArrayContains("members", uid)) { docs ->
                state = state.copy(albums = docs.map { MemoryAlbum(it.id, it.getString("title").orEmpty(), strings(it, "capsuleIds"), strings(it, "members"), it.getString("ownerUid").orEmpty()) })
            },
            listen("destinations", user.collection("destinations")) { docs ->
                state = state.copy(destinations = docs.mapNotNull { d ->
                    val lat = d.getDouble("lat") ?: return@mapNotNull null
                    val lng = d.getDouble("lng") ?: return@mapNotNull null
                    CarDestination(d.id, d.getString("capsuleId").orEmpty(), d.getString("title").orEmpty(), lat, lng,
                        d.getTimestamp("createdAt")?.toDate()?.time ?: 0, d.getString("status") ?: "pending")
                }.sortedByDescending { it.createdAt })
            },
        )
        publish()
        awaitClose { listeners.forEach { it.remove() } }
    }

    override suspend fun saveMemory(memory: CapsuleMemory) = request {
        require(memory.title.length <= 120 && memory.note.length <= 4000)
        user().collection("memories").document(id(memory.capsuleId)).set(mapOf(
            "title" to memory.title.trim(), "note" to memory.note, "favorite" to memory.favorite,
            "visitLater" to memory.visitLater, "updatedAt" to FieldValue.serverTimestamp(),
        )).await(); Unit
    }
    override suspend fun collect(capsuleId: String, device: String) = request {
        require(device in setOf("vehicle", "mobile"))
        val db = database()
        val ref = user().collection("collections").document(id(capsuleId))
        db.runTransaction { transaction ->
            if (!transaction.get(ref).exists()) transaction.set(ref, mapOf("collectedAt" to FieldValue.serverTimestamp(), "device" to device))
        }.await(); Unit
    }
    override suspend fun saveAlbum(album: MemoryAlbum): String = request {
        require(album.title.isNotBlank() && album.title.length <= 100 && album.capsuleIds.size <= 200 && album.members.size <= 30)
        val me = uid()
        val albums = database().collection("memoryAlbums")
        val ref = if (album.id.isBlank()) albums.document() else albums.document(id(album.id))
        ref.set(mapOf("title" to album.title.trim(), "capsuleIds" to album.capsuleIds.distinct().map(::id),
            "ownerUid" to me, "members" to (album.members + me).distinct(), "updatedAt" to FieldValue.serverTimestamp())).await()
        ref.id
    }
    override suspend fun deleteAlbum(id: String) = request { database().collection("memoryAlbums").document(id(id)).delete().await(); Unit }
    override suspend fun sendDestination(capsuleId: String, title: String, lat: Double, lng: Double): String = request {
        require(CollectionPolicy.valid(lat, lng) && title.length <= 120)
        val ref = user().collection("destinations").document()
        ref.set(mapOf("capsuleId" to id(capsuleId), "title" to title, "lat" to lat, "lng" to lng,
            "createdAt" to FieldValue.serverTimestamp(), "status" to "pending")).await()
        ref.id
    }
    override suspend fun acknowledgeDestination(id: String, status: String) = request {
        require(status in setOf("accepted", "dismissed", "failed"))
        val db = database()
        val ref = user().collection("destinations").document(id(id))
        db.runTransaction { tx ->
            val current = tx.get(ref)
            if (current.exists() && current.getString("status") == "pending") {
                tx.update(ref, mapOf("status" to status, "handledAt" to FieldValue.serverTimestamp()))
            }
        }.await(); Unit
    }
    override suspend fun savePhoto(capsuleId: String, jpeg: ByteArray) = request {
        require(jpeg.size in 1..800_000) { "사진 크기를 줄인 뒤 다시 시도해 주세요." }
        user().collection("photos").document(id(capsuleId)).set(mapOf("image" to Blob.fromBytes(jpeg), "updatedAt" to FieldValue.serverTimestamp())).await(); Unit
    }
    override suspend fun photo(capsuleId: String): ByteArray? = request {
        user().collection("photos").document(id(capsuleId)).get(Source.SERVER).await().getBlob("image")?.toBytes()
    }
    override suspend fun deletePhoto(capsuleId: String) = request { user().collection("photos").document(id(capsuleId)).delete().await(); Unit }
    private fun strings(d: DocumentSnapshot, key: String) = (d.get(key) as? List<*>)?.filterIsInstance<String>().orEmpty()
}
