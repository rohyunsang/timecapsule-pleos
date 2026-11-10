package app.timecapsule.companion

import app.timecapsule.social.RemoteCapsule
import kotlin.math.*

/** Physical collection is distinct from replay. A receipt never bypasses date or access. */
object CollectionPolicy {
    const val MAX_LOCATION_AGE_MS = 10_000L
    const val MAX_ACCURACY_M = 100.0
    data class Fix(val lat: Double, val lng: Double, val accuracyM: Double, val elapsedMs: Long, val mocked: Boolean = false)
    enum class Block { NO_ACCESS, WAITING_DATE, INVALID_PLACE, LOCATION_REQUIRED, STALE_LOCATION, INACCURATE_LOCATION, MOCK_LOCATION, OUT_OF_RANGE }
    fun valid(lat: Double, lng: Double) = lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0
    fun reason(c: RemoteCapsule, authorized: Boolean, collected: Boolean, today: Long, fix: Fix?, nowElapsedMs: Long, nowWallMs: Long = System.currentTimeMillis()): Block? {
        if (!authorized) return Block.NO_ACCESS
        if (c.openAfter?.let { today < it } == true) return Block.WAITING_DATE
        if (ReplyPolicy.waiting(c.replyMode, c.replyAvailableAt, nowWallMs)) return Block.WAITING_DATE
        if (!valid(c.lat, c.lng) || c.radiusM <= 0) return Block.INVALID_PLACE
        if (collected || c.sourceRemoteId != null || ReplyPolicy.remoteOpen(c.replyMode, c.replyAvailableAt, nowWallMs)) return null
        if (fix == null || !valid(fix.lat, fix.lng)) return Block.LOCATION_REQUIRED
        if (fix.mocked) return Block.MOCK_LOCATION
        if (fix.elapsedMs <= 0 || nowElapsedMs < fix.elapsedMs || nowElapsedMs - fix.elapsedMs > MAX_LOCATION_AGE_MS) return Block.STALE_LOCATION
        if (!fix.accuracyM.isFinite() || fix.accuracyM < 0 || fix.accuracyM > MAX_ACCURACY_M) return Block.INACCURATE_LOCATION
        // The entire reported accuracy circle must fit inside the collection radius.
        if (distanceM(fix.lat, fix.lng, c.lat, c.lng) + fix.accuracyM > c.radiusM) return Block.OUT_OF_RANGE
        return null
    }
    fun distanceM(lat: Double, lng: Double, otherLat: Double, otherLng: Double): Double {
        val a = sin(Math.toRadians(otherLat-lat)/2).pow(2) + cos(Math.toRadians(lat))*cos(Math.toRadians(otherLat))*sin(Math.toRadians(otherLng-lng)/2).pow(2)
        return 6_371_000 * 2 * asin(sqrt(a.coerceIn(0.0,1.0)))
    }
    fun message(block: Block) = when(block) {
        Block.NO_ACCESS -> "공유 권한을 확인하지 못했어요. 인터넷 연결과 공개 대상을 확인해 주세요."
        Block.WAITING_DATE -> "아직 개봉일이 되지 않았어요."
        Block.INVALID_PLACE -> "이 캡슐의 장소를 확인할 수 없어요."
        Block.LOCATION_REQUIRED -> "위치 권한을 허용하고 현재 위치를 확인해 주세요."
        Block.STALE_LOCATION -> "최신 위치를 다시 확인해 주세요."
        Block.INACCURATE_LOCATION -> "위치가 정확하지 않아요. 탁 트인 곳에서 다시 시도해 주세요."
        Block.MOCK_LOCATION -> "실제 위치에서만 수집할 수 있어요."
        Block.OUT_OF_RANGE -> "캡슐이 있는 장소에 조금 더 가까이 가 주세요."
    }
}
