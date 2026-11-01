package app.timecapsule.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Data model from feature-spec section 06, plus sharing: a capsule is left either for
 * yourself (stays in this car), for all friends, or for chosen friends (letter capsule
 * recipients). Shared capsules also live on the server; friends' capsules are cached here so
 * approach alerts and playback work offline.
 */
@Entity(tableName = "capsule")
data class Capsule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val lat: Double,
    val lng: Double,
    val radiusM: Int = 300,
    val placeName: String?,
    val audioPath: String,
    val durationSec: Int,
    val transcript: String? = null,
    /** Tag labels joined with '|', already in narration order. */
    val tags: String,
    /** Raw vehicle values as JSON so tags can be recomputed when rules change. */
    val rawVehicleState: String,
    val tripId: Long? = null,
    val carModel: String?,
    val odometerKm: Double?,
    val locationUncertain: Boolean = false,
    /** Who left it. null = me (also before signing in). */
    val ownerUid: String? = null,
    val ownerName: String? = null,
    /** Audience.name: SELF / FRIENDS / SOME. */
    val audience: String = "FRIENDS",
    /** For SOME: recipient uids joined with ','. */
    val recipients: String = "",
    /** Server document id once uploaded (or for friends' capsules, the source id). */
    val remoteId: String? = null,
) {
    val isMine get() = ownerUid == null
}

@Entity(tableName = "letter_lock")
data class LetterLock(
    @PrimaryKey val capsuleId: Long,
    /** First day the capsule may open, as LocalDate.toEpochDay(). */
    val openAfter: Long,
    val recipientProfileId: Long? = null,
)

@Entity(tableName = "trip")
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startAt: Long,
    val endAt: Long? = null,
    val startOdometer: Double?,
    val endOdometer: Double? = null,
    val occupants: Int?,
    val routePolyline: String? = null,
)

@Entity(tableName = "notify_log", primaryKeys = ["capsuleId", "date"])
data class NotifyLog(
    val capsuleId: Long,
    /** yyyy-MM-dd, used for the once-a-day limit. */
    val date: String,
    /** played / later / ignored */
    val result: String,
)

@Dao
interface CapsuleDao {
    @Insert
    suspend fun insert(capsule: Capsule): Long

    @Query("SELECT * FROM capsule ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Capsule>>

    @Query("SELECT * FROM capsule WHERE id = :id")
    suspend fun get(id: Long): Capsule?

    @Query("SELECT * FROM capsule WHERE remoteId = :remoteId LIMIT 1")
    suspend fun byRemoteId(remoteId: String): Capsule?

    @Query("SELECT * FROM capsule")
    suspend fun all(): List<Capsule>

    @Query("UPDATE capsule SET remoteId = :remoteId WHERE id = :id")
    suspend fun setRemoteId(id: Long, remoteId: String)

    /** Demo only: move a capsule back in time so the narration has years to count. */
    @Query("UPDATE capsule SET createdAt = :createdAt WHERE id = :id")
    suspend fun setCreatedAt(id: Long, createdAt: Long)

    @Query("DELETE FROM capsule WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM capsule")
    suspend fun deleteAll()

    @Insert
    suspend fun insertLock(lock: LetterLock)

    @Query("SELECT * FROM letter_lock WHERE capsuleId = :capsuleId")
    suspend fun lockFor(capsuleId: Long): LetterLock?

    @Query("SELECT * FROM letter_lock")
    fun observeLocks(): Flow<List<LetterLock>>

    @Query("DELETE FROM letter_lock WHERE capsuleId = :capsuleId")
    suspend fun deleteLock(capsuleId: Long)

    @Query("DELETE FROM letter_lock")
    suspend fun deleteAllLocks()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun log(entry: NotifyLog)

    @Query("SELECT capsuleId FROM notify_log WHERE date = :date")
    suspend fun notifiedIdsOn(date: String): List<Long>

    @Query("DELETE FROM notify_log")
    suspend fun deleteAllLogs()

    @Insert
    suspend fun insertTrip(trip: Trip): Long

    @Query("SELECT * FROM trip WHERE endAt IS NULL ORDER BY startAt DESC LIMIT 1")
    suspend fun openTrip(): Trip?

    @Query("UPDATE trip SET endAt = :endAt, endOdometer = :endOdometer WHERE id = :id")
    suspend fun endTrip(id: Long, endAt: Long, endOdometer: Double?)

    @Query("UPDATE trip SET occupants = :occupants WHERE id = :id AND (occupants IS NULL OR occupants < :occupants)")
    suspend fun raiseOccupants(id: Long, occupants: Int)

    @Query("SELECT * FROM trip ORDER BY startAt DESC")
    fun observeTrips(): Flow<List<Trip>>

    @Query("DELETE FROM trip")
    suspend fun deleteAllTrips()
}

@Database(entities = [Capsule::class, LetterLock::class, Trip::class, NotifyLog::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun capsuleDao(): CapsuleDao
}
