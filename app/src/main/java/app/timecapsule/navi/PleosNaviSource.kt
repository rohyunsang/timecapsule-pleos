package app.timecapsule.navi

import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteWithWaypointsRequest
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import app.timecapsule.geo.GeoPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * NaviHelper wrapper. Location comes from getCurrentLocationInfo() polled every 2 s, because the
 * Pleos FusedLocation SDK is not released yet (spec F-04 falls back to the navigation app).
 */
class PleosNaviSource(context: Context, private val scope: CoroutineScope) : NaviSource {
    private val helper = NaviHelper(context.applicationContext)
    private val _position = MutableStateFlow<NaviPosition?>(null)
    private val _route = MutableStateFlow<Route?>(null)
    private val _events = MutableSharedFlow<NaviEvent>(extraBufferCapacity = 8)
    private var connected = false
    private var poll: Job? = null

    override val position: StateFlow<NaviPosition?> = _position.asStateFlow()
    override val route: StateFlow<Route?> = _route.asStateFlow()
    override val events: SharedFlow<NaviEvent> = _events.asSharedFlow()

    private val listener = object : NaviHelperEventListener {
        override fun onCurrentLocationInfo(info: CurrentLocationInfo) {
            _position.value = NaviPosition(GeoPoint(info.latitude, info.longitude), info.address, android.os.SystemClock.elapsedRealtime())
        }

        override fun onRouteStarted(info: RouteStartInfo) {
            // Destination and waypoints arrive through their own callbacks; ask, then announce.
            helper.getDestinationInfo()
            helper.getWaypointInfo()
            scope.launch {
                delay(ROUTE_SETTLE_MS)
                _events.emit(NaviEvent.RouteStarted)
            }
        }

        override fun onDestinationInfo(info: DestinationInfo) {
            val dest = NamedPoint(info.name, GeoPoint(info.latitude, info.longitude))
            _route.value = (_route.value ?: Route(dest)).copy(destination = dest)
        }

        override fun onWayPointInfo(list: List<WaypointInfo>) {
            val current = _route.value ?: return
            _route.value = current.copy(waypoints = list.map { NamedPoint(it.name, GeoPoint(it.latitude, it.longitude)) })
        }

        override fun onDestinationArrived(info: DestinationArrivedInfo) {
            _route.value = null
            _events.tryEmit(NaviEvent.Arrived(info.name))
        }

        override fun onRouteCancelled() = ended()
        override fun onRouteEnded() = ended()
    }

    private fun ended() {
        _route.value = null
        _events.tryEmit(NaviEvent.RouteEnded)
    }

    fun start() {
        helper.initialize(
            {
                connected = true
                helper.addListener(listener)
                helper.getDestinationInfo()
                poll = scope.launch {
                    while (isActive) {
                        runCatching { helper.getCurrentLocationInfo() }
                        delay(POLL_MS)
                    }
                }
            },
            { e -> _position.value = null; Log.w(TAG, "NaviHelper unavailable: ${e.message}") },
        )
    }

    fun stop() {
        _position.value = null
        poll?.cancel()
        if (connected) runCatching { helper.removeListener(listener) }
        runCatching { helper.release() }
        connected = false
    }

    override fun drive(destination: NamedPoint, waypoints: List<NamedPoint>): Boolean {
        if (!connected) return false
        val dest = RouteInfo(
            destination.point.lat, destination.point.lng, null, null,
            destination.name, "", "", "", RouteOption.RECOMMENDED, "", false,
        )
        val indexes = listOf(WaypointIndex.FIRST, WaypointIndex.SECOND)
        val stops = waypoints.take(MAX_WAYPOINTS).mapIndexed { i, w ->
            RequestWaypointInfo(w.point.lat, w.point.lng, w.name, indexes[i], "", "", "", "")
        }
        return runCatching {
            if (stops.isEmpty()) helper.requestRoute(dest)
            else helper.requestRouteWithWaypoints(RouteWithWaypointsRequest(dest, stops))
        }.onFailure { Log.w(TAG, "route request failed", it) }.isSuccess
    }

    companion object {
        private const val TAG = "PleosNavi"
        private const val POLL_MS = 2_000L
        private const val ROUTE_SETTLE_MS = 1_500L
        /** WaypointIndex has FIRST and SECOND only, so NaviHelper takes at most two waypoints. */
        const val MAX_WAYPOINTS = 2
    }
}
