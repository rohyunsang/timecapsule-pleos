package app.timecapsule.mobile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import app.timecapsule.companion.CollectionPolicy
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

class PhoneLocation(private val context: Context) {
    fun permitted() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    @Suppress("MissingPermission", "DEPRECATION")
    suspend fun current(): CollectionPolicy.Fix {
        check(permitted()) { "정확한 위치 권한을 허용해 주세요." }
        val manager = context.getSystemService(LocationManager::class.java)
        check(manager.isLocationEnabled) { "휴대전화 설정에서 위치 서비스를 켜 주세요." }
        return withTimeout(20_000) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        val fix = CollectionPolicy.Fix(location.latitude, location.longitude,
                            if (location.hasAccuracy()) location.accuracy.toDouble() else Double.POSITIVE_INFINITY,
                            location.elapsedRealtimeNanos / 1_000_000, location.isFromMockProvider)
                        if (continuation.isActive) { manager.removeUpdates(this); continuation.resume(fix) }
                    }
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                }
                continuation.invokeOnCancellation { manager.removeUpdates(listener) }
                val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { manager.isProviderEnabled(it) }
                if (providers.isEmpty()) { continuation.cancel(IllegalStateException("위치 제공자를 찾지 못했어요.")); return@suspendCancellableCoroutine }
                try { providers.forEach { manager.requestLocationUpdates(it, 0L, 0f, listener, Looper.getMainLooper()) } }
                catch (e: Exception) { manager.removeUpdates(listener); continuation.cancel(e) }
            }
        }
    }
}
