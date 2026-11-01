package app.timecapsule.capsule

import android.content.Context
import app.timecapsule.audio.Recording
import app.timecapsule.data.AppDatabase
import app.timecapsule.data.Capsule
import app.timecapsule.data.LetterLock
import app.timecapsule.data.NotifyLog
import app.timecapsule.data.Trip
import app.timecapsule.geo.GeoPoint
import app.timecapsule.tag.MoodTag
import app.timecapsule.tag.MoodTagEngine
import app.timecapsule.vehicle.VehicleSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.time.LocalDate

/** All capsule data stays on the device: Room for records, app-private files for audio (spec 06). */
class CapsuleRepository(private val db: AppDatabase) {
    private val dao = db.capsuleDao()

    val capsules: Flow<List<Capsule>> = dao.observeAll()
    val locks: Flow<Map<Long, LetterLock>> = dao.observeLocks().map { list -> list.associateBy { it.capsuleId } }
    val trips: Flow<List<Trip>> = dao.observeTrips()

    suspend fun get(id: Long) = dao.get(id)

    suspend fun bury(
        recording: Recording,
        snapshot: VehicleSnapshot,
        tags: List<MoodTag>,
        at: GeoPoint?,
        lastKnown: GeoPoint?,
        openOn: LocalDate?,
    ): Capsule {
        val where = at ?: lastKnown
        val trip = dao.openTrip()
        val capsule = Capsule(
            createdAt = System.currentTimeMillis(),
            lat = where?.lat ?: 0.0,
            lng = where?.lng ?: 0.0,
            radiusM = Rules.CAPSULE_RADIUS_M.toInt(),
            placeName = snapshot.placeName,
            audioPath = recording.file.absolutePath,
            durationSec = recording.durationSec,
            tags = MoodTagEngine.toStored(tags),
            rawVehicleState = snapshot.toJson(),
            tripId = trip?.id,
            carModel = snapshot.carModel,
            odometerKm = snapshot.odometerKm,
            // Spec F-01: no fix at all -> keep the last one and flag it.
            locationUncertain = at == null,
        )
        val id = dao.insert(capsule)
        if (openOn != null) dao.insertLock(LetterLock(id, openOn.toEpochDay()))
        trip?.let { t -> snapshot.occupiedSeats?.let { dao.raiseOccupants(t.id, it) } }
        return capsule.copy(id = id)
    }

    suspend fun backdate(id: Long, years: Long) {
        val at = java.time.ZonedDateTime.now().minusYears(years).toInstant().toEpochMilli()
        dao.setCreatedAt(id, at)
    }

    /** F-13: delete one capsule with its audio. */
    suspend fun delete(capsule: Capsule) {
        File(capsule.audioPath).delete()
        dao.deleteLock(capsule.id)
        dao.delete(capsule.id)
    }

    /** F-13 "차량에서 모두 지우기". */
    suspend fun deleteEverything(context: Context) {
        File(context.filesDir, "capsules").deleteRecursively()
        dao.deleteAll()
        dao.deleteAllLocks()
        dao.deleteAllLogs()
        dao.deleteAllTrips()
    }

    suspend fun notifiedToday(today: LocalDate): Set<Long> = dao.notifiedIdsOn(today.toString()).toSet()

    suspend fun logNotify(capsuleId: Long, today: LocalDate, result: String) =
        dao.log(NotifyLog(capsuleId, today.toString(), result))

    /** F-08 (P1): a trip runs from route start until arrival. */
    suspend fun startTrip(odometerKm: Double?, occupants: Int?) {
        if (dao.openTrip() == null) dao.insertTrip(Trip(startAt = System.currentTimeMillis(), startOdometer = odometerKm, occupants = occupants))
    }

    suspend fun endTrip(odometerKm: Double?) {
        dao.openTrip()?.let { dao.endTrip(it.id, System.currentTimeMillis(), odometerKm) }
    }
}
