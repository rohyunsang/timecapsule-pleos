package app.timecapsule.map

import app.timecapsule.data.Capsule
import app.timecapsule.geo.GeoPoint
import app.timecapsule.navi.NaviPosition
import org.junit.Assert.*
import org.junit.Test

class CollectedMapDataTest {
    private fun capsule(id: Long = 1) = Capsule(id=id, createdAt=1, lat=37.5, lng=127.0,
        placeName="장소", audioPath="local.m4a", durationSec=5, tags="", rawVehicleState="{}",
        carModel=null, odometerKm=null, collectedAt=2)

    @Test fun onlyValidCollectedLocationsBecomeMapDestinations() {
        val valid = capsule()
        val excluded = listOf(valid.copy(id=2,collectedAt=null),valid.copy(id=3,draft=true),
            valid.copy(id=4,locationUncertain=true),valid.copy(id=5,lat=Double.NaN),
            valid.copy(id=6,lng=181.0))
        assertEquals(listOf(listOf(valid)), CollectedMapData.groups(excluded + valid))
    }

    @Test fun colocatedCollectionsRemainIndividuallySelectable() {
        val first=capsule(1); val second=capsule(2); val other=capsule(3).copy(lat=37.6)
        assertEquals(listOf(listOf(first,second),listOf(other)),CollectedMapData.groups(listOf(first,second,other)))
        assertEquals(listOf(listOf(second),listOf(other)),CollectedMapData.groups(listOf(second,other)))
    }

    @Test fun staleFutureAndInvalidVehiclePositionsAreHidden() {
        val fresh=NaviPosition(GeoPoint(37.5,127.0),null,10_000)
        assertEquals(fresh.point,CollectedMapData.currentPoint(fresh,20_000))
        assertNull(CollectedMapData.currentPoint(fresh,20_001))
        assertNull(CollectedMapData.currentPoint(fresh,9_999))
        assertNull(CollectedMapData.currentPoint(fresh.copy(receivedAt=0),1))
        assertNull(CollectedMapData.currentPoint(fresh.copy(point=GeoPoint(91.0,127.0)),10_000))
        assertNull(CollectedMapData.currentPoint(null,10_000))
    }
}
