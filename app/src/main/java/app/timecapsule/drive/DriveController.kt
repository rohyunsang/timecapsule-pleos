package app.timecapsule.drive

import android.content.Context
import android.os.SystemClock
import app.timecapsule.Settings
import app.timecapsule.capsule.OpenPolicy
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
import app.timecapsule.tag.MoodTagEngine
import app.timecapsule.tag.Narration
import app.timecapsule.vehicle.SafetySignal
import app.timecapsule.vehicle.VehicleSnapshot
import app.timecapsule.vehicle.VehicleSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
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
    private val restricted: () -> Boolean = { true },
    private val demo: () -> Boolean = { false },
    private val microphoneAllowed: () -> Boolean = { false },
    private val requestPreparation: () -> Boolean = { false },
    private val releasePreparation: () -> Unit = {},
    /** Uploads capsules left for friends; set once the sharing layer exists. */
    var sharing: app.timecapsule.social.CapsuleSharing? = null,
    sky: StateFlow<app.timecapsule.weather.Sky?> = MutableStateFlow(null),
    outsideTempC: StateFlow<Float?> = MutableStateFlow(null),
) {
    private fun today() = LocalDate.now()
    private fun now() = SystemClock.elapsedRealtime()
    private val session = DriveSession()
    data class SaveResult(val capsule: Capsule, val openAfter: Long?)
    data class TripReview(val saved: Int, val collected: Int, val drafts: Int)
    private val _saveResult = MutableStateFlow<SaveResult?>(null)
    val saveResult: StateFlow<SaveResult?> = _saveResult.asStateFlow()
    fun dismissSaveResult() { _saveResult.value = null }
    private val _tripReview = MutableStateFlow<TripReview?>(null)
    val tripReview: StateFlow<TripReview?> = _tripReview.asStateFlow()
    fun dismissTripReview() { _tripReview.value = null }
    private var sessionNumber = 0L
    private var recordingSessionNumber = 0L
    private val sessionSaved = mutableMapOf<Long, Boolean>()
    private val sessionCollected = mutableSetOf<Long>()
    private fun updateTripReview() {
        _tripReview.value = TripReview(sessionSaved.count { !it.value }, sessionCollected.size, sessionSaved.count { it.value })
    }
    private val _quietTrip = MutableStateFlow(false)
    val quietTrip: StateFlow<Boolean> = _quietTrip.asStateFlow()
    private var lastAutomaticAlertAt: Long? = null
    fun setQuietTrip(quiet: Boolean) {
        if (!canEdit() || !_prepared.value || _state.value != DriveState.Idle) return say("주차 중 녹음과 재생을 마친 뒤 바꿔 주세요")
        _quietTrip.value = quiet
        if (quiet) { _arrivalPrompt.value = false; arrivalTimeout?.cancel() }
    }
    private fun allowsAutomaticAlert() = AlertCadence.allows(_quietTrip.value, settings.alertIntervalMinutes.value, lastAutomaticAlertAt, now())
    private val _prepared = MutableStateFlow(false)
    val prepared: StateFlow<Boolean> = _prepared.asStateFlow()
    private val _editable = MutableStateFlow(false)
    val editable: StateFlow<Boolean> = _editable.asStateFlow()
    private val _microphonePermissionNeeded = MutableStateFlow(false)
    val microphonePermissionNeeded: StateFlow<Boolean> = _microphonePermissionNeeded.asStateFlow()
    fun dismissMicrophonePermission() { _microphonePermissionNeeded.value = false }
    private fun requireMicrophonePermission() {
        _microphonePermissionNeeded.value = true
        say("마이크 권한이 필요해요. 주차 후 설정에서 허용해 주세요")
    }
    private var operationEpoch = 0L
    private var saving = false
    var authorizeShared: suspend (Capsule) -> Boolean = { it.isMine }
    var currentRecipients: () -> List<String> = { emptyList() }

    private fun vehicleKnown(): Boolean {
        val s = car.value
        return demo() || (s.gearInPark != null &&
            OpenPolicy.fresh(s.gearAt, now(), OpenPolicy.VEHICLE_MAX_AGE_MS) &&
            OpenPolicy.fresh(s.turnSignalAt, now(), OpenPolicy.VEHICLE_MAX_AGE_MS))
    }
    fun canEdit() = car.value.gearInPark == true && vehicleKnown() && !restricted()
    private fun positionFresh(): Boolean = position.value?.let {
        OpenPolicy.valid(it.point) && (demo() || OpenPolicy.fresh(it.receivedAt, now(), OpenPolicy.POSITION_MAX_AGE_MS))
    } == true
    fun prepareDrive() {
        if (!canEdit()) return say("주차 후 차량 상태를 확인해 주세요")
        if (!microphoneAllowed()) return requireMicrophonePermission()
        if (!requestPreparation()) say("기록 준비를 시작하지 못했어요. 앱 화면에서 다시 시도해 주세요")
    }
    internal fun completePreparation(): Boolean {
        if ((!canEdit() && !byVoice) || !microphoneAllowed() || _prepared.value) return false
        session.start(now()); _prepared.value = true
        sessionNumber++; sessionSaved.clear(); sessionCollected.clear(); _tripReview.value = null; _saveResult.value = null
        _quietTrip.value = false; lastAutomaticAlertAt = null
        if (!byVoice) say("이번 주행을 준비했어요. 요청할 때만 녹음해요")
        return true
    }

    /** Set while a Gleo voice request prepares the drive itself (the driver asked out loud). */
    private var byVoice = false

    /**
     * Gleo AI "녹음해 줘": record right away, even mid-drive. Asking out loud is the explicit
     * request, so the parked "주행 준비" step is done here without the screen; the app is in front
     * (Gleo opened it), which is what lets the microphone service start. True once recording.
     */
    suspend fun recordByVoice(): Boolean {
        if (_state.value == DriveState.Recording) return true
        if (!microphoneAllowed()) { requireMicrophonePermission(); return false }
        if (!_prepared.value || !session.active(now())) {
            byVoice = true
            try {
                if (!requestPreparation()) return false
                kotlinx.coroutines.withTimeoutOrNull(3_000) { _prepared.first { it } } ?: return false
            } finally { byVoice = false }
        }
        if (_state.value is DriveState.Playing || _state.value is DriveState.Paused) stopPlayback()
        if (_state.value is DriveState.ApproachAlert) later()
        bury()
        return kotlinx.coroutines.withTimeoutOrNull(2_000) { _state.first { it == DriveState.Recording } } != null
    }
    fun endDrive() {
        val wasPrepared = _prepared.value
        operationEpoch++
        if (_state.value == DriveState.Recording) dispatch(DriveEvent.SafetySignalOn)
        resumeJob?.cancel(); alertTimeout?.cancel(); arrivalTimeout?.cancel()
        stopAudio(); _state.value = DriveState.Idle; _playing.value = null; _alert.value = null
        _answerWindow.value = null; _reprompt.value = null; _arrivalPrompt.value = false
        _userPaused.value = false; session.end(); _prepared.value = false
        _quietTrip.value = false; lastAutomaticAlertAt = null
        if (wasPrepared) { _saveResult.value = null; updateTripReview() }
        releasePreparation()
    }
    private fun maintainSession() {
        _editable.value = canEdit()
        if (!_prepared.value) {
            if (!canEdit() && _playing.value != null) stopPlayback()
            return
        }
        if (!microphoneAllowed() || session.expired(now(), canEdit())) { endDrive(); return }
        if (!vehicleKnown() || (!positionFresh() && _state.value != DriveState.Recording && _playing.value?.capsule?.canReplayAnywhere != true)) {
            when (_state.value) {
                // No car signal will ever clear a lost GPS fix, so a pending alert must not wait
                // in Paused: it becomes "later" and is offered again at the next stop.
                is DriveState.ApproachAlert -> dispatch(DriveEvent.Later)
                DriveState.Recording, is DriveState.Playing -> dispatch(DriveEvent.SafetySignalOn)
                else -> {}
            }
        }
        // An alert paused by a signal that has cleared, while the resume check failed: let it go.
        val st = _state.value
        if (st is DriveState.Paused && st.resumeTo is DriveState.ApproachAlert && interrupting.value.isEmpty() &&
            resumeJob?.isActive != true) dispatch(DriveEvent.PlaybackFinished)

    }
    fun openingReason(c: Capsule, at: app.timecapsule.navi.NaviPosition? = position.value): String? = OpenPolicy.reason(c, locks.value[c.id], today(), at?.point,
        if (demo()) now() else at?.receivedAt ?: 0L, now())
    fun confirmDraft(c: Capsule) {
        if (!canEdit() || !c.isMine) return
        if (c.locationUncertain) return say("녹음 장소를 확인하지 못해 캡슐로 저장할 수 없어요. 음성은 임시보관해요")
        scope.launch { stopDraftPreview(); repo.confirmDraft(c.id); say("나만 듣는 캡슐로 저장했어요") }
    }
    private val _previewDraftId = MutableStateFlow<Long?>(null)
    val previewDraftId: StateFlow<Long?> = _previewDraftId.asStateFlow()
    private fun stopAudio() { _previewDraftId.value = null; player.stop() }
    fun stopDraftPreview() { if (_previewDraftId.value != null) stopAudio() }
    fun previewDraft(c: Capsule) {
        if (!canEdit() || !c.isMine || !c.draft || _state.value != DriveState.Idle) return
        // Private recovery preview is parked-only; it never grants a location opening.
        if (_previewDraftId.value == c.id) { stopDraftPreview(); return }
        stopAudio()
        _previewDraftId.value = c.id
        player.play("임시보관한 녹음이에요", c.audioPath) { _previewDraftId.value = null }
    }

    /** Car state plus the place fields that come from the navigation app (spec F-02). */
    val car: StateFlow<VehicleSnapshot> = combine(vehicle.snapshot, navi.position, navi.route, sky, outsideTempC) { s, pos, route, sk, temp ->
        s.copy(
            sky = s.sky ?: sk?.name,
            // Real outside air from the weather service; the fake car's own value only in demos.
            outsideTempC = temp ?: s.outsideTempC,
            placeName = s.placeName ?: PlaceName.shorten(pos?.address) ?: route?.destination?.name,
            onHighway = s.onHighway ?: pos?.address?.let(PlaceName::isHighway),
        )
    }.stateIn(scope, SharingStarted.Eagerly, vehicle.snapshot.value)

    val signals: StateFlow<Set<SafetySignal>> =
        combine(vehicle.activeSafetySignals, player.navigationSpeaking) { set, naviSpeaking ->
            if (naviSpeaking) set + SafetySignal.NAVI_VOICE else set
        }.stateIn(scope, SharingStarted.Eagerly, emptySet())

    /**
     * Signals that cut off a recording or pause a capsule already playing. A brake press only
     * holds off starting something new: the SDK reports the pedal, not deceleration, and ending
     * every recording at the first brake tap in city traffic would turn most of them into drafts.
     */
    private val interrupting: StateFlow<Set<SafetySignal>> =
        signals.map { it - SafetySignal.HARD_BRAKE }.stateIn(scope, SharingStarted.Eagerly, emptySet())

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
        recorder.onInterrupted = { dispatch(DriveEvent.SafetySignalOn) }
        // A capsule removed from this car (deleted, unfriended) must not keep its "아까 지나친" prompt.
        scope.launch {
            repo.capsules.collect { list ->
                val r = _reprompt.value ?: return@collect
                if (list.none { it.id == r.id }) _reprompt.value = null
            }
        }
        scope.launch {
            interrupting.map { it.isNotEmpty() }.distinctUntilChanged().collect { on ->
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
                maintainSession()
                if (!canEdit() && _state.value == DriveState.Idle) stopAudio()
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
        if (_state.value != DriveState.Idle || saving) return
        if (!microphoneAllowed()) return requireMicrophonePermission()
        if (!_prepared.value || !session.active(now())) return say("출발 전에 이번 주행을 준비해 주세요")
        if (!vehicleKnown() || signals.value.isNotEmpty()) return say("지금은 녹음을 시작할 수 없어요")
        stopAudio()
        pendingOpenOn = openOn
        pendingAudience = audience ?: settings.defaultAudience.value
        pendingRecipients = if (pendingAudience == "FRIENDS") currentRecipients() else recipients
        dispatch(DriveEvent.BuryRequested)
    }

    fun stopRecording() = dispatch(DriveEvent.RecordingEnded)

    /** Throws the current recording away (pressed by mistake, or said something wrong). */
    fun cancelRecording() {
        if (_state.value != DriveState.Recording) return
        discardRecording = true
        dispatch(DriveEvent.RecordingEnded)
    }
    /** The alert whose spoken line has finished: a voice answer is welcome until it closes. */
    private val _answerWindow = MutableStateFlow<Long?>(null)
    val answerWindow: StateFlow<Long?> = _answerWindow.asStateFlow()

    /** When the last playback ended; no new alert right after one (one at a time, then quiet). */
    private var lastPlaybackEndAt = 0L

    /** Mic loudness while recording, 0..1. */
    fun micLevel(): Float = recorder.lastLevel()
    private var silenceJob: Job? = null
    /** Set when the quiet ended a recording in which nobody spoke: thrown away, not saved. */
    private var silentOnly = false

    fun listen() = dispatch(DriveEvent.PlayRequested)
    fun later() = dispatch(DriveEvent.Later)
    fun stopPlayback() {
        operationEpoch++
        dispatch(DriveEvent.PlaybackFinished)
    }

    /** Explicit parked-only action; re-read the collection before changing navigation. */
    fun driveToMobileDestination(destination: app.timecapsule.companion.CarDestination): Boolean {
        val point = GeoPoint(destination.lat, destination.lng)
        if (!canEdit() || !OpenPolicy.valid(point)) return false
        val accepted = navi.drive(NamedPoint(destination.title, point), emptyList())
        say(if (accepted) "모바일에서 보낸 장소로 길 안내를 요청했어요" else "내비게이션에 연결하지 못했어요. 다시 시도해 주세요")
        return accepted
    }

    fun driveToCollected(id: Long) = driveToCapsule(id, collectedOnly = true)

    fun driveToCapsule(id: Long, collectedOnly: Boolean = false) {
        if (!canEdit()) return
        scope.launch {
            val c = repo.get(id) ?: return@launch
            if (!canEdit() || !app.timecapsule.capsule.AlbumData.canNavigate(c) ||
                (collectedOnly && !app.timecapsule.map.CollectedMapData.eligible(c))) return@launch
            val destination = NamedPoint(c.placeName ?: "캡슐 지점", GeoPoint(c.lat, c.lng))
            if (navi.drive(destination, emptyList())) say("Pleos 내비게이션에 길 안내를 요청했어요")
            else say("내비게이션에 연결하지 못했어요")
        }
    }

    fun playCollected(id: Long) {
        if (!canEdit()) return
        scope.launch {
            val c = repo.get(id) ?: return@launch
            if (canEdit() && c.collectedAt != null) playCapsule(id)
        }
    }

    fun playCapsule(id: Long) {
        if (saving) return
        stopDraftPreview()
        if (_state.value == DriveState.Idle) dispatch(DriveEvent.PlayCapsule(id))
        else say("지금은 재생할 수 없어요")
    }

    fun togglePause() {
        val s = _state.value
        if (s is DriveState.Paused && s.resumeTo is DriveState.Playing) {
            if (signals.value.isEmpty() && vehicleKnown()) startPlayback(s.resumeTo.capsuleId)
        } else if (s is DriveState.Playing) {
            if (_userPaused.value) startPlayback(s.capsuleId)
            else { player.pause(); _userPaused.value = true }
        }
    }

    fun restart() {
        val c = _playing.value?.capsule ?: return
        if (signals.value.isNotEmpty()) return
        stopAudio()
        startPlayback(c.id)
    }

    fun dismissReprompt() { _reprompt.value = null }

    fun answerArrival(leaveOne: Boolean) {
        arrivalTimeout?.cancel()
        _arrivalPrompt.value = false
        if (leaveOne) bury()
    }

    fun delete(capsule: Capsule) = scope.launch {
        if (!canEdit()) return@launch
        if (_playing.value?.capsule?.id == capsule.id) stopPlayback()
        stopAudio()
        repo.delete(capsule)
        settings.setListenLater(capsule.id, false)
        sharing?.onDeleted(capsule)
    }

    fun setShared(capsule: Capsule, shared: Boolean) { if (canEdit() && !capsule.draft && !capsule.locationUncertain) sharing?.setShared(capsule, shared) }

    /** Voice position and length in ms while the voice plays; null during the narration. */
    fun progress(): Pair<Int, Int>? = player.progress()

    fun deleteEverything(context: Context) = scope.launch {
        endDrive()
        repo.deleteEverything(context)
        settings.clearListenLater()
        _saveResult.value = null; _tripReview.value = null; sessionSaved.clear(); sessionCollected.clear()
        _reprompt.value = null
        _forecast.value = null
        say("차량에 저장된 캡슐을 모두 지웠어요")
    }

    /** Dev panel: make [id] 18 years old for the PT demo narration. */
    fun backdate(id: Long, years: Long) = scope.launch { repo.backdate(id, years) }

    /** Dev panel (demo mode only): play [id] without the server share check, to show its weather. */
    private var devPreviewId: Long? = null
    fun devPreview(id: Long) {
        if (!demo()) return
        devPreviewId = id
        playCapsule(id)
    }

    /** Dev panel: the next recording is cut off by a navigation voice [afterMs] after it starts. */
    fun simulateRecordingInterruption(afterMs: Long) = scope.launch {
        _state.first { it == DriveState.Recording }
        delay(afterMs)
        if (_state.value == DriveState.Recording) dispatch(DriveEvent.SafetySignalOn)
    }

    /** Dev panel: ask "도착 한마디 남길까요?" now, without door, place or daily-limit checks. */
    fun simulateArrivalPrompt() {
        if (_state.value != DriveState.Idle) return
        _arrivalPrompt.value = true
        lastAutomaticAlertAt = now()
        say("도착 한마디 남길까요?")
        arrivalTimeout?.cancel()
        arrivalTimeout = scope.launch { delay(Rules.ALERT_TIMEOUT_MS * 3); _arrivalPrompt.value = false }
    }

    /** Dev panel: pretend the car is 1.2 km from [id]. */
    fun simulateApproach(id: Long) {
        if (demo() && _prepared.value && _state.value == DriveState.Idle) dispatch(DriveEvent.CapsuleWithinRange(id))
    }

    // ---- state machine plumbing --------------------------------------------------------

    fun dispatch(event: DriveEvent) {
        if (event == DriveEvent.SafetySignalOn) operationEpoch++
        apply(DriveStateMachine.reduce(_state.value, event))
    }

    private fun apply(t: Transition) {
        _state.value = t.state
        if (t.state !is DriveState.ApproachAlert) _answerWindow.value = null
        t.effects.forEach(::run)
    }

    private fun run(effect: Effect) {
        when (effect) {
            Effect.StartRecording -> startRecording()
            Effect.SaveRecording -> saveRecording()
            Effect.SaveDraft -> saveRecording(interrupted = true)
            is Effect.ShowApproachAlert -> showAlert(effect.capsuleId)
            Effect.HideApproachAlert -> {
                alertTimeout?.cancel()
                _alert.value = null
            }
            is Effect.StartPlayback -> startPlayback(effect.capsuleId)
            Effect.PausePlayback -> player.pause()
            Effect.StopPlayback -> {
                lastPlaybackEndAt = SystemClock.elapsedRealtime()
                stopAudio()
                _playing.value = null
                _userPaused.value = false
            }
            is Effect.RepromptAtNextStop -> scope.launch {
                _reprompt.value = repo.get(effect.capsuleId)
                if (_reprompt.value != null) settings.setListenLater(effect.capsuleId, true)
                repo.logNotify(effect.capsuleId, today(), "later")
            }
            is Effect.ResumeAfter -> {
                resumeJob?.cancel()
                resumeJob = scope.launch {
                    delay(effect.millis)
                    if (_state.value !is DriveState.Paused) return@launch
                    if (_prepared.value && vehicleKnown() && positionFresh() && signals.value.isEmpty()) {
                        apply(DriveStateMachine.resume(effect.target))
                    } else if (effect.target is DriveState.ApproachAlert && interrupting.value.isEmpty()) {
                        // Nothing else will resume it (stale GPS or car data): offer it again later.
                        dispatch(DriveEvent.PlaybackFinished)
                    }
                }
            }
        }
    }

    private fun startRecording() {
        recordingSessionNumber = sessionNumber
        _saveResult.value = null
        // Spec F-02: tags describe the moment recording starts.
        recordSnapshot = car.value
        recordPosition = navi.position.value?.point?.takeIf { positionFresh() }
        val ok = recorder.start(settings.maxRecordSec.value) { scope.launch { if (_state.value == DriveState.Recording) dispatch(DriveEvent.RecordingEnded) } }
        if (ok) {
            _recordingSince.value = SystemClock.elapsedRealtime()
            watchSilence()
        } else {
            micFailed = true
            say("마이크를 쓸 수 없어요. 마이크 권한을 확인해 주세요")
            scope.launch { dispatch(DriveEvent.RecordingEnded) }
        }
    }

    /** Ends the recording by itself after [Settings.silenceSec] seconds without talking. */
    private fun watchSilence() {
        silenceJob?.cancel()
        val sec = settings.silenceSec.value
        val watch = app.timecapsule.audio.SilenceWatch(sec * 1000L, SystemClock.elapsedRealtime())
        silenceJob = scope.launch {
            while (_state.value == DriveState.Recording) {
                val quiet = watch.feed(recorder.level(), SystemClock.elapsedRealtime())
                if (quiet) {
                    if (!watch.heardVoice) { silentOnly = true; discardRecording = true }
                    android.util.Log.i("DriveController", "quiet for ${sec}s, voice heard: ${watch.heardVoice}")
                    dispatch(DriveEvent.RecordingEnded)
                    break
                }
                kotlinx.coroutines.delay(100)
            }
        }
    }

    private fun saveRecording(interrupted: Boolean = false) {
        silenceJob?.cancel()
        _recordingSince.value = null
        if (discardRecording) {
            discardRecording = false
            recorder.cancel()
            pendingOpenOn = null
            say(if (silentOnly) "말소리가 없어서 저장하지 않았어요" else "녹음을 지웠어요")
            silentOnly = false
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
        val savedSession = recordingSessionNumber
        val audience = pendingAudience
        val recipients = pendingRecipients.toList()
        val at = recordPosition
        saving = true
        scope.launch {
          try {
            val tags = MoodTagEngine.tagsFor(snap)
            val c = repo.bury(rec, snap, tags, at, null, openOn, audience, recipients, draft = interrupted || at == null)
            if (savedSession == sessionNumber) {
                sessionSaved[c.id] = c.draft
                if (_prepared.value) _saveResult.value = SaveResult(c, openOn?.toEpochDay())
                else if (_tripReview.value != null) updateTripReview()
            }
            val name = c.placeName ?: tags.firstOrNull()?.label ?: "오늘의"
            say(if (c.draft) "녹음을 비공개로 임시보관했어요. 주차 후 확인해 주세요" else if (openOn != null) "편지 캡슐을 묻었어요. ${openOn.year}년 ${openOn.monthValue}월 ${openOn.dayOfMonth}일에 열려요" else "$name 캡슐을 묻었어요")
            if (!c.draft) sharing?.onBuried(c)
          } catch (_: Exception) {
              say("녹음을 목록에 저장하지 못했어요. 저장 공간을 확인해 주세요")
          } finally { saving = false }
        }
    }

    private fun showAlert(id: Long) {
        scope.launch {
            val c = repo.get(id) ?: return@launch dispatch(DriveEvent.Later)
            if (!_prepared.value || _state.value != DriveState.ApproachAlert(id) || c.draft || c.locationUncertain) return@launch
            val here = navi.position.value?.point
            val dist = here?.let { app.timecapsule.geo.Geo.distanceM(it, GeoPoint(c.lat, c.lng)) } ?: Rules.APPROACH_ALERT_M
            val elapsed = Narration.elapsed(dateOf(c), today())
            // A friend's capsule says who left it first: that is what makes it worth hearing.
            val line = if (c.ownerName != null) "${c.ownerName}님이 남긴 캡슐이 있어요"
            else "$elapsed, ${c.placeName ?: "이 길"} 캡슐이 있어요"
            lastAutomaticAlertAt = now()
            _alert.value = AlertUi(c, line, dist)
            repo.logNotify(id, today(), "shown")
            beeper.alert()
            // Once the line has been read out, the driver may answer by voice (VoiceAnswer).
            player.say(line) { if (_state.value == DriveState.ApproachAlert(id)) _answerWindow.value = id }
            alertTimeout?.cancel()
            alertTimeout = scope.launch {
                delay(Rules.ALERT_TIMEOUT_MS)
                if (_state.value == DriveState.ApproachAlert(id)) dispatch(DriveEvent.Later)
            }
        }
    }

    private fun startPlayback(id: Long) {
        val epoch = ++operationEpoch
        scope.launch {
            val c = repo.get(id)
            if (epoch != operationEpoch) return@launch
            if (c == null) return@launch stopPlayback()
            if ((!_prepared.value || !session.active(now())) && !canEdit()) {
                stopPlayback(); say("출발 전에 이번 주행을 준비해 주세요"); return@launch
            }
            val reason = OpenPolicy.reason(c, repo.lockFor(id), today(), position.value?.point,
                if (demo()) now() else position.value?.receivedAt ?: 0L, now())
            if (reason != null || !vehicleKnown() || signals.value.isNotEmpty()) {
                stopPlayback(); say(reason ?: "차량 상태를 확인한 후 다시 요청해 주세요"); return@launch
            }
            // Collection removes only the location requirement. A new shared replay must
            // still verify its recipient; only the current paused playback can resume offline.
            val resuming = _playing.value?.capsule?.id == id && player.isActive
            if (!resuming && !(demo() && devPreviewId == c.id) && !authorizeShared(c)) {
                if (epoch == operationEpoch) { stopPlayback(); say("공유 권한을 확인하지 못했어요. 연결 후 다시 시도해 주세요") }
                return@launch
            }
            if (epoch != operationEpoch || (!_prepared.value && !canEdit())) return@launch
            val fresh = repo.get(id)
            if (epoch != operationEpoch) return@launch
            if (fresh == null) return@launch stopPlayback()
            val blocked = OpenPolicy.reason(fresh, repo.lockFor(id), today(), position.value?.point,
                if (demo()) now() else position.value?.receivedAt ?: 0L, now())
            if (blocked != null || !vehicleKnown() || signals.value.isNotEmpty()) {
                stopPlayback(); say(blocked ?: "차량 상태 확인 중이에요"); return@launch
            }
            _state.value = DriveState.Playing(id)
            _userPaused.value = false
            if (_playing.value?.capsule?.id == id && player.isActive) { player.resume(); return@launch }
            val narration = Narration.build(dateOf(fresh), today(), MoodTagEngine.fromStored(fresh.tags), from = fresh.originalOwnerName ?: fresh.ownerName)
            _playing.value = PlayingUi(fresh, narration)
            if (_reprompt.value?.id == id) _reprompt.value = null
            val spoken = listOfNotNull(narration, app.timecapsule.tag.TheDay.spoken(fresh.rawVehicleState)).joinToString(" ")
            player.play(spoken, fresh.audioPath, onStarted = {
                settings.setListenLater(id, false)
                val timedReply = app.timecapsule.companion.ReplyPolicy.remoteOpen(fresh.replyMode, fresh.replyAvailableAt, System.currentTimeMillis())
                val collected = if (timedReply) fresh else fresh.copy(collectedAt = fresh.collectedAt ?: System.currentTimeMillis())
                _playing.value = PlayingUi(collected, narration)
                val collectedInSession = sessionNumber
                val duringTrip = _prepared.value
                if (!fresh.isForwarded && !timedReply) scope.launch {
                    repo.collect(id, collected.collectedAt!!)
                    if (duringTrip && collectedInSession == sessionNumber && fresh.collectedAt == null) {
                        sessionCollected.add(id)
                        if (!_prepared.value && _tripReview.value != null) updateTripReview()
                    }
                }
            }, interrupted = {
                // Cut off by another app or a broken file: not logged as heard.
                scope.launch { if (epoch == operationEpoch || _playing.value?.capsule?.id == id) dispatch(DriveEvent.PlaybackFinished) }
            }) {
                scope.launch {
                    repo.logNotify(id, today(), "played")
                    dispatch(DriveEvent.PlaybackFinished)
                }
            }
        }
    }

    // ---- triggers ----------------------------------------------------------------------

    /** F-04, checked every few seconds; suppressed during safety signals and 10 s after. */
    private suspend fun checkApproach() {
        if (!allowsAutomaticAlert()) return
        if (!_prepared.value || !session.active(now()) || !vehicleKnown() || !positionFresh() || saving) return
        if (_state.value != DriveState.Idle || signals.value.isNotEmpty()) return
        if (SystemClock.elapsedRealtime() - lastSignalClearAt < DriveStateMachine.RESUME_ALERT_MS) return
        if (SystemClock.elapsedRealtime() - lastPlaybackEndAt < DriveStateMachine.QUIET_AFTER_PLAYBACK_MS) return
        val here = navi.position.value?.point ?: return
        val hit = ApproachDetector.next(here, capsules.value.filter { c -> !app.timecapsule.companion.ReplyPolicy.waiting(c.replyMode, c.replyAvailableAt, System.currentTimeMillis()) && app.timecapsule.geo.Geo.distanceM(here, GeoPoint(c.lat, c.lng)) <= c.radiusM }, locks.value, repo.notifiedToday(today()), today()) ?: return
        if (_state.value == DriveState.Idle && allowsAutomaticAlert()) dispatch(DriveEvent.CapsuleWithinRange(hit.capsule.id))
    }

    private suspend fun onNavi(e: NaviEvent) {
        when (e) {
            NaviEvent.RouteStarted -> {
                val r = navi.route.value
                val path = listOfNotNull(navi.position.value?.point) +
                    (r?.waypoints.orEmpty() + listOfNotNull(r?.destination)).map { it.point }
                val f = RouteForecaster.forecast(path, capsules.value, locks.value, today())
                _forecast.value = f
                if (_prepared.value && !_quietTrip.value) f.message()?.let(::say)
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
        if (!allowsAutomaticAlert()) return
        if (!_prepared.value || !canEdit() || !positionFresh()) return
        val since = arrivedAt ?: return
        if (SystemClock.elapsedRealtime() - since > ARRIVAL_WINDOW_MS) { arrivedAt = null; return }
        if (s.gearInPark != true || s.anyDoorOpen != true || _state.value != DriveState.Idle) return
        arrivedAt = null
        if (!settings.arrivalPrompt.value) return
        val here = navi.position.value?.point ?: return
        if (ApproachDetector.hasCapsuleAt(here, capsules.value)) return
        if (!settings.takeArrivalPrompt(today(), Rules.ARRIVAL_PROMPTS_PER_DAY)) return
        _arrivalPrompt.value = true
        lastAutomaticAlertAt = now()
        say("도착 한마디 남길까요?")
        arrivalTimeout?.cancel()
        arrivalTimeout = scope.launch {
            delay(Rules.ALERT_TIMEOUT_MS)
            _arrivalPrompt.value = false
        }
    }

    private fun say(text: String) {
        _messages.tryEmit(text)
        if (_state.value != DriveState.Recording && signals.value.isEmpty()) player.say(text)
    }

    private companion object {
        const val APPROACH_CHECK_MS = 3_000L
        const val ARRIVAL_WINDOW_MS = 10 * 60_000L

        fun dateOf(c: Capsule): LocalDate =
            Instant.ofEpochMilli(c.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
    }
}
