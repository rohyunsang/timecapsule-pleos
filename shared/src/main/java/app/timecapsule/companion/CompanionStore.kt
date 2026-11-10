package app.timecapsule.companion

import kotlinx.coroutines.flow.Flow

/** All companion records live under the authenticated account, separate from voice ownership. */
data class CapsuleMemory(val capsuleId: String, val title: String = "", val note: String = "", val favorite: Boolean = false, val visitLater: Boolean = false)
data class CollectionReceipt(val capsuleId: String, val collectedAt: Long, val device: String)
data class MemoryAlbum(val id: String, val title: String, val capsuleIds: List<String>, val members: List<String> = emptyList(), val ownerUid: String = "")
data class CarDestination(val id: String, val capsuleId: String, val title: String, val lat: Double, val lng: Double, val createdAt: Long, val status: String = "pending")
data class CompanionSnapshot(
    val memories: List<CapsuleMemory> = emptyList(),
    val collections: List<CollectionReceipt> = emptyList(),
    val albums: List<MemoryAlbum> = emptyList(),
    val destinations: List<CarDestination> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)
interface CompanionStore {
    fun observe(uid: String): Flow<CompanionSnapshot>
    suspend fun saveMemory(memory: CapsuleMemory)
    suspend fun collect(capsuleId: String, device: String)
    suspend fun saveAlbum(album: MemoryAlbum): String
    suspend fun deleteAlbum(id: String)
    suspend fun sendDestination(capsuleId: String, title: String, lat: Double, lng: Double): String
    suspend fun acknowledgeDestination(id: String, status: String)
    suspend fun savePhoto(capsuleId: String, jpeg: ByteArray)
    suspend fun photo(capsuleId: String): ByteArray?
    suspend fun deletePhoto(capsuleId: String)
}
