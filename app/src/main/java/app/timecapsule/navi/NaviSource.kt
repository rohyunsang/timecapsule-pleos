package app.timecapsule.navi

import app.timecapsule.geo.GeoPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class NaviPosition(val point: GeoPoint, val address: String?)

data class NamedPoint(val name: String, val point: GeoPoint)

data class Route(val destination: NamedPoint, val waypoints: List<NamedPoint> = emptyList())

sealed interface NaviEvent {
    /** F-03 trigger. */
    data object RouteStarted : NaviEvent
    /** F-09 trigger. */
    data class Arrived(val name: String?) : NaviEvent
    data object RouteEnded : NaviEvent
}

/** Everything the app needs from the navigation app. */
interface NaviSource {
    val position: StateFlow<NaviPosition?>
    /** Active route, or null when free driving. */
    val route: StateFlow<Route?>
    val events: SharedFlow<NaviEvent>

    /**
     * F-07: start guidance through [waypoints] (oldest first) to [destination].
     * Returns false when the navigation app refused or is not connected.
     */
    fun drive(destination: NamedPoint, waypoints: List<NamedPoint>): Boolean
}

/** Dev/test navigation: position and route are set by hand. */
class FakeNaviSource(start: NaviPosition? = null) : NaviSource {
    private val _position = MutableStateFlow(start)
    private val _route = MutableStateFlow<Route?>(null)
    private val _events = MutableSharedFlow<NaviEvent>(extraBufferCapacity = 8)

    override val position: StateFlow<NaviPosition?> = _position.asStateFlow()
    override val route: StateFlow<Route?> = _route.asStateFlow()
    override val events: SharedFlow<NaviEvent> = _events.asSharedFlow()

    fun moveTo(p: NaviPosition) = _position.update { p }

    override fun drive(destination: NamedPoint, waypoints: List<NamedPoint>): Boolean {
        _route.value = Route(destination, waypoints)
        _events.tryEmit(NaviEvent.RouteStarted)
        return true
    }

    fun arrive() {
        val name = _route.value?.destination?.name
        _route.value = null
        _events.tryEmit(NaviEvent.Arrived(name))
    }
}
