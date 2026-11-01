package app.timecapsule.drive

import android.content.Context
import android.os.SystemClock
import app.timecapsule.Settings
import app.timecapsule.audio.Beeper
import app.timecapsule.audio.Player
import app.timecapsule.audio.Recorder
import app.timecapsule.capsule.ApproachDetector
import app.timecapsule.capsule.CapsuleRepository
import app.timecapsule.capsule.RouteForecast
import app.timecapsule.capsule.RouteForecaster
import app.timecapsule.capsule.Rules
import app.timecapsule.data.Capsule
import app.timecapsule.data.LetterLock
import app.timecapsule.geo.GeoPoint
import app.timecapsule.geo.PlaceName
import app.timecapsule.navi.NamedPoint
import app.timecapsule.navi.NaviEvent
import app.timecapsule.navi.NaviSource
import app.timecapsule.navi.PleosNaviSource
import app.timecapsule.tag.MoodTagEngine
import app.timecapsule.tag.Narration
import app.timecapsule.vehicle.SafetySignal
import app.timecapsule.vehicle.VehicleSnapshot
import app.timecapsule.vehicle.VehicleSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class AlertUi(
    val capsule: Capsule,
    val line: String,
    val distanceM: Double,
    /** elapsedRealtime when shown, so the card can show how long until it closes itself. */
    val shownAt: Long = SystemClock.elapsedRealtime(),
)
data class PlayingUi(val capsule: Capsule, val narration: String)

/**
 * Runs the drive state machine against the real world: car signals in, effects out
 * (recording, alerts, playback). Everything runs on the main thread via [scope].
 */
class DriveController(
    private val scope: CoroutineScope,
    vehicle: VehicleSource,
    private val navi: NaviSource,
    private val repo: CapsuleRepository,
    private val recorder: Recorder,
    private val player: Player,
    private val beeper: Beeper,
    private val settings: Settings,
    /** Uploads capsules left for friends; set once the sharing layer exists. */
    var sharing: app.timecapsule.social.CapsuleSharing? = null,
) {
    private fun today() = LocalDate.now()

    /** Car state plus the place fields that come from the navigation app (spec F-02). */
    val car: StateFlow<VehicleSnapshot> = combine(vehicle.snapshot, navi.position, navi.route) { s, pos, route ->
        s.copy(
            placeName = s.placeName ?: PlaceName.shorten(pos?.address) ?: route?.destination?.name,
            onHighway = s.onHighway ?: pos?.address?.let(PlaceName::isHighway),
        )
    }.stateIn(scope, SharingStarted.Eagerly, vehicle.snapshot.value)

    val signals: StateFlow<Set<SafetySignal>> =
        combine(vehicle.activeSafetySignals, player.navigationSpeaking) { set, naviSpeaking ->
            if (naviSpeaking) set + SafetySignal.NAVI_VOICE else set
        }.stateIn(scope, SharingStarted.Eagerly, emptySet())

    val capsules: StateFlow<List<Capsule>> = repo.capsules.stateIn(scope, SharingStarted.Eagerly, emptyList())
    val locks: StateFlow<Map<Long, LetterLock>> = repo.locks.stateIn(scope, SharingStarted.Eagerly, emptyMap())
    val trips = repo.trips.stateIn(scope, SharingStarted.Eagerly, emptyList())
    val position = navi.position
    /** Who a capsule is for when nobody picks (shown on the main bar). */
    val defaultAudience = settings.defaultAudience
    val route = navi.route

    private val _state = MutableStateFlow<DriveState>(DriveState.Idle)
    val state: StateFlow<DriveState> = _state.asStateFlow()

    private val _alert = MutableStateFlow<AlertUi?>(null)
    val alert: StateFlow<AlertUi?> = _alert.asStateFlow()

    private val _playing = MutableStateFlow<PlayingUi?>(null)
    val playing: StateFlow<PlayingUi?> = _playing.asStateFlow()

    private val _userPaused = MutableStateFlow(false)
    val userPaused: StateFlow<Boolean> = _userPaused.asStateFlow()

    private val _forecast = MutableStateFlow<RouteForecast?>(null)
    val forecast: StateFlow<RouteForecast?> = _forecast.asStateFlow()

    private val _reprompt = MutableStateFlow<Capsule?>(null)
    val reprompt: StateFlow<Capsule?> = _reprompt.asStateFlow()

    private val _arrivalPrompt = MutableStateFlow(false)
    val arrivalPrompt: StateFlow<Boolean> = _arrivalPrompt.asStateFlow()

    /** elapsedRealtime when recording started, null when not recording. */
    private var discardRecording = false

    private val _recordingSince = MutableStateFlow<Long?>(null)
    val recordingSince: StateFlow<Long?> = _recordingSince.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    private var pendingOpenOn: LocalDate? = null
    private var pendingAudience = "FRIENDS"
    private var pendingRecipients = emptyList<String>()
    private var recordSnapshot: VehicleSnapshot? = null
    private var recordPosition: GeoPoint? = null
    private var micFailed = false
    private var alertTimeout: Job? = null
    private var resumeJob: Job? = null
    private var arrivalTimeout: Job? = null
    private var lastSignalClearAt = 0L
    private var arrivedAt: Long? = null

    init {
        scope.launch {
            signals.map { it.isNotEmpty() }.distinctUntilChanged().collect { on ->
                if (on) {
                    resumeJob?.cancel()
                    dispatch(DriveEvent.SafetySignalOn)
                } else {
                    lastSignalClearAt = SystemClock.elapsedRealtime()
                    dispatch(DriveEvent.SafetySignalOff)
                }
            }
        }
        scope.launch {
            while (true) {
                delay(APPROACH_CHECK_MS)
                checkApproach()
            }
        }
        scope.launch { navi.events.collect(::onNavi) }
        scope.launch { car.collect(::checkArrival) }
    }

    // ---- user actions -------------------------------------------------------------------

    /**
     * F-01 (and F-06 when [openOn] is set: a letter capsule). [audience] null = the default
     * from settings (all friends, or only me); while driving there is no picking.
     */
    fun bury(openOn: LocalDate? = null, audience: String? = null, recipients: List<String> = emptyList()) {
        if (_state.value != DriveState.Idle) return
        pendingOpenOn = openOn
        pendingAudience = audience ?: settings.defaultAudience.value
        pendingRecipients = recipients
        dispatch(DriveEvent.BuryRequested)
    }

    fun stopRecording() = dispatch(DriveEvent.RecordingEnded)

    /** Throws the current recording away (pressed by mistake, or said something wrong). */
    fun cancelRecording() {
        if (_state.value != DriveState.Recording) return
        discardRecording = true
        dispatch(DriveEvent.RecordingEnded)
    }
    fun listen() = dispatch(DriveEvent.PlayRequested)
    fun later() = dispatch(DriveEvent.Later)
    fun stopPlayback() = dispatch(DriveEvent.PlaybackFinished)

    fun playCapsule(id: Long) {
        if (_state.value == DriveState.Idle) dispatch(DriveEvent.PlayCapsule(id))
        else say("지금은 재생할 수 없어요")
    }

    fun togglePause() {
        if (_state.value !is DriveState.Playing) return
        if (_userPaused.value) player.resume() else player.pause()
        _userPaused.value = !_userPaused.value
    }

    fun restart() {
        _userPaused.value = false
        player.restart()
    }

    fun dismissReprompt() { _reprompt.value = null }

    fun answerArrival(leaveOne: Boolean) {
        arrivalTimeout?.cancel()
        _arrivalPrompt.value = false
        if (leaveOne) bury()
    }

    /**
     * F-07: drive through the album's capsule spots in time order. NaviHelper takes two
     * waypoints, so the first two become waypoints and the last one the destination.
     */
    fun driveMemoryRoute(album: List<Capsule>) {
        val spots = album.filterNot { it.locationUncertain }.sortedBy { it.createdAt }
        if (spots.isEmpty()) return say("위치가 기록된 캡슐이 없어요")
        val named = spots.map { NamedPoint(it.placeName ?: "캡슐 지점", GeoPoint(it.lat, it.lng)) }
        val dest = named.last()
        val via = named.dropLast(1).take(PleosNaviSource.MAX_WAYPOINTS)
        if (!navi.drive(dest, via)) return say("내비게이션에 연결하지 못했어요")
        val skipped = named.size - 1 - via.size
        say(if (skipped > 0) "그때 그 길로 안내할게요. 경유지는 두 곳까지라 ${skipped}곳은 빼고 넣었어요" else "그때 그 길로 안내할게요")
    }

    fun delete(capsule: Capsule) = scope.launch {
        repo.delete(capsule)
        sharing?.onDeleted(capsule)
    }

    fun deleteEverything(context: Context) = scope.launch {
        player.stop()
        repo.deleteEverything(context)
        _reprompt.value = null
        _forecast.value = null
        say("차량에 저장된 캡슐을 모두 지웠어요")
    }

    /** Dev panel: make [id] 18 years old for the PT demo narration. */
    fun backdate(id: Long, years: Long) = scope.launch { repo.backdate(id, years) }

    /** Dev panel: pretend the car is 1.2 km from [id]. */
    fun simulateApproach(id: Long) {
        if (_state.value == DriveState.Idle) dispatch(DriveEvent.CapsuleWithinRange(id))
    }

    // ---- state machine plumbing --------------------------------------------------------

    fun dispatch(event: DriveEvent) = apply(DriveStateMachine.reduce(_state.value, event))

    private fun apply(t: Transition) {
        _state.value = t.state
        t.effects.forEach(::run)
    }

    private fun run(effect: Effect) {
        when (effect) {
            Effect.StartRecording -> startRecording()
            Effect.SaveRecording -> saveRecording()
            is Effect.ShowApproachAlert -> showAlert(effect.capsuleId)
            Effect.HideApproachAlert -> {
                alertTimeout?.cancel()
                _alert.value = null
            }
            is Effect.StartPlayback -> startPlayback(effect.capsuleId)
            Effect.PausePlayback -> player.pause()
            Effect.StopPlayback -> {
                player.stop()
                _playing.value = null
                _userPaused.value = false
            }
            is Effect.RepromptAtNextStop -> scope.launch {
                _reprompt.value = repo.get(effect.capsuleId)
                repo.logNotify(effect.capsuleId, today(), "later")
            }
            is Effect.ResumeAfter -> {
                resumeJob?.cancel()
                resumeJob = scope.launch {
                    delay(effect.millis)
                    if (signals.value.isEmpty() && _state.value is DriveState.Paused) {
                        apply(DriveStateMachine.resume(effect.target))
                    }
                }
            }
        }
    }

    private fun startRecording() {
        // Spec F-02: tags describe the moment recording starts.
        recordSnapshot = car.value
        recordPosition = navi.position.value?.point
        val ok = recorder.start { scope.launch { if (_state.value == DriveState.Recording) dispatch(DriveEvent.RecordingEnded) } }
        if (ok) {
            _recordingSince.value = SystemClock.elapsedRealtime()
        } else {
            micFailed = true
            say("마이크를 쓸 수 없어요. 마이크 권한을 확인해 주세요")
            scope.launch { dispatch(DriveEvent.RecordingEnded) }
        }
    }

    private fun saveRecording() {
        _recordingSince.value = null
        if (discardRecording) {
            discardRecording = false
            recorder.cancel()
            pendingOpenOn = null
            say("녹음을 지웠어요")
            return
        }
        val rec = recorder.stop()
        val snap = recordSnapshot ?: car.value
        val openOn = pendingOpenOn
        pendingOpenOn = null
        if (rec == null) {
            if (!micFailed) say("3초보다 짧아서 저장하지 않았어요")
            micFailed = false
            return
        }
        scope.launch {
            val tags = MoodTagEngine.tagsFor(snap)
            val c = repo.bury(rec, snap, tags, recordPosition, navi.position.value?.point, openOn, pendingAudience, pendingRecipients)
            val name = c.placeName ?: tags.firstOrNull()?.label ?: "오늘의"
            say(if (openOn != null) "편지 캡슐을 묻었어요. ${openOn.year}년 ${openOn.monthValue}월 ${openOn.dayOfMonth}일에 열려요" else "$name 캡슐을 묻었어요")
            sharing?.onBuried(c)
        }
    }

    private fun showAlert(id: Long) {
        scope.launch {
            val c = repo.get(id) ?: return@launch dispatch(DriveEvent.Later)
            val here = navi.position.value?.point
            val dist = here?.let { app.timecapsule.geo.Geo.distanceM(it, GeoPoint(c.lat, c.lng)) } ?: Rules.APPROACH_ALERT_M
            val elapsed = Narration.elapsed(dateOf(c), today())
            // A friend's capsule says who left it first: that is what makes it worth hearing.
            val line = if (c.ownerName != null) "${c.ownerName}님이 남긴 캡슐이 있어요"
            else "$elapsed, ${c.placeName ?: "이 길"} 캡슐이 있어요"
            _alert.value = AlertUi(c, line, dist)
            repo.logNotify(id, today(), "shown")
            beeper.alert()
            player.say(line)
            alertTimeout?.cancel()
            alertTimeout = scope.launch {
                delay(Rules.ALERT_TIMEOUT_MS)
                if (_state.value == DriveState.ApproachAlert(id)) dispatch(DriveEvent.Later)
            }
        }
    }

    private fun startPlayback(id: Long) {
        if (_playing.value?.capsule?.id == id && player.isActive) {
            if (!_userPaused.value) player.resume()
            return
        }
        scope.launch {
            val c = repo.get(id) ?: return@launch dispatch(DriveEvent.PlaybackFinished)
            val narration = Narration.build(dateOf(c), today(), MoodTagEngine.fromStored(c.tags), from = c.ownerName)
            _playing.value = PlayingUi(c, narration)
            _userPaused.value = false
            if (_reprompt.value?.id == id) _reprompt.value = null
            repo.logNotify(id, today(), "played")
            player.play(narration, c.audioPath) { scope.launch { dispatch(DriveEvent.PlaybackFinished) } }
        }
    }

    // ---- triggers ----------------------------------------------------------------------

    /** F-04, checked every few seconds; suppressed during safety signals and 10 s after. */
    private suspend fun checkApproach() {
        if (_state.value != DriveState.Idle || signals.value.isNotEmpty()) return
        if (SystemClock.elapsedRealtime() - lastSignalClearAt < DriveStateMachine.RESUME_ALERT_MS) return
        val here = navi.position.value?.point ?: return
        val hit = ApproachDetector.next(here, capsules.value, locks.value, repo.notifiedToday(today()), today()) ?: return
        if (_state.value == DriveState.Idle) dispatch(DriveEvent.CapsuleWithinRange(hit.capsule.id))
    }

    private suspend fun onNavi(e: NaviEvent) {
        when (e) {
            NaviEvent.RouteStarted -> {
                val r = navi.route.value
                val path = listOfNotNull(navi.position.value?.point) +
                    (r?.waypoints.orEmpty() + listOfNotNull(r?.destination)).map { it.point }
                val f = RouteForecaster.forecast(path, capsules.value, locks.value, today())
                _forecast.value = f
                f.message()?.let(::say)
                repo.startTrip(car.value.odometerKm, car.value.occupiedSeats)
            }
            is NaviEvent.Arrived -> {
                _forecast.value = null
                repo.endTrip(car.value.odometerKm)
                arrivedAt = SystemClock.elapsedRealtime()
                checkArrival(car.value)
            }
            NaviEvent.RouteEnded -> _forecast.value = null
        }
    }

    /** F-09: after arriving, P + door open at a spot with no capsule -> offer one line. */
    private fun checkArrival(s: VehicleSnapshot) {
        val since = arrivedAt ?: return
        if (SystemClock.elapsedRealtime() - since > ARRIVAL_WINDOW_MS) { arrivedAt = null; return }
        if (s.gearInPark != true || s.anyDoorOpen != true || _state.value != DriveState.Idle) return
        arrivedAt = null
        if (!settings.arrivalPrompt.value) return
        val here = navi.position.value?.point ?: return
        if (ApproachDetector.hasCapsuleAt(here, capsules.value)) return
        if (!settings.takeArrivalPrompt(today(), Rules.ARRIVAL_PROMPTS_PER_DAY)) return
        _arrivalPrompt.value = true
        say("도착 한마디 남길까요?")
        arrivalTimeout?.cancel()
        arrivalTimeout = scope.launch {
            delay(Rules.ALERT_TIMEOUT_MS)
            _arrivalPrompt.value = false
        }
    }

    private fun say(text: String) {
        _messages.tryEmit(text)
        player.say(text)
    }

    private companion object {
        const val APPROACH_CHECK_MS = 3_000L
        const val ARRIVAL_WINDOW_MS = 10 * 60_000L

        fun dateOf(c: Capsule): LocalDate =
            Instant.ofEpochMilli(c.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
    }
}
