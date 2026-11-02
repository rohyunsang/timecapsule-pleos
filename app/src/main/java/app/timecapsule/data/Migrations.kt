package app.timecapsule.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {
    val FROM_3 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE capsule ADD COLUMN collectedAt INTEGER")
        }
    }
    val FROM_1 = object : Migration(1, 3) { override fun migrate(db: SupportSQLiteDatabase) = upgrade(db, false) }
    val FROM_2 = object : Migration(2, 3) { override fun migrate(db: SupportSQLiteDatabase) = upgrade(db, true) }
    private fun upgrade(db: SupportSQLiteDatabase, social: Boolean) {
        db.execSQL("""CREATE TABLE capsule_new (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, createdAt INTEGER NOT NULL,
            lat REAL NOT NULL, lng REAL NOT NULL, radiusM INTEGER NOT NULL, placeName TEXT,
            audioPath TEXT NOT NULL, durationSec INTEGER NOT NULL, transcript TEXT,
            tags TEXT NOT NULL, rawVehicleState TEXT NOT NULL, tripId INTEGER,
            carModel TEXT, odometerKm REAL, locationUncertain INTEGER NOT NULL,
            ownerUid TEXT, ownerName TEXT, audience TEXT NOT NULL, recipients TEXT NOT NULL,
            remoteId TEXT, draft INTEGER NOT NULL DEFAULT 0)""")
        val original = "id,createdAt,lat,lng,radiusM,placeName,audioPath,durationSec,transcript,tags,rawVehicleState,tripId,carModel,odometerKm,locationUncertain"
        val sharing = if (social) "ownerUid,ownerName,audience,recipients,remoteId" else "NULL,NULL,'SELF','',NULL"
        db.execSQL("INSERT INTO capsule_new SELECT $original,$sharing,0 FROM capsule")
        db.execSQL("DROP TABLE capsule")
        db.execSQL("ALTER TABLE capsule_new RENAME TO capsule")
    }
}
