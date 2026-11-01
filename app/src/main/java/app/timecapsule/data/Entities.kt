package app.timecapsule.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** Data model from feature-spec section 06. Everything stays on the device (Room + audio files). */
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
)

@Entity(tableName = "letter_lock")
data class LetterLock(
    @PrimaryKey val capsuleId: Long,
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

    @Query("DELETE FROM capsule WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM capsule")
    suspend fun deleteAll()

    @Insert
    suspend fun insertLock(lock: LetterLock)

    @Query("SELECT * FROM letter_lock WHERE capsuleId = :capsuleId")
    suspend fun lockFor(capsuleId: Long): LetterLock?

    @Insert
    suspend fun log(entry: NotifyLog)

    @Query("SELECT COUNT(*) FROM notify_log WHERE capsuleId = :capsuleId AND date = :date")
    suspend fun notifiedOn(capsuleId: Long, date: String): Int
}

@Database(entities = [Capsule::class, LetterLock::class, Trip::class, NotifyLog::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun capsuleDao(): CapsuleDao
}
