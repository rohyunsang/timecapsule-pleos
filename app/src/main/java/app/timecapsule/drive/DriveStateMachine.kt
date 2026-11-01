package app.timecapsule.drive

/**
 * The five in-drive states from spec section 05. Pure reducer: no timers or I/O here,
 * the caller runs the returned [Effect]s.
 */
sealed interface DriveState {
    data object Idle : DriveState
    data object Recording : DriveState
    data class ApproachAlert(val capsuleId: Long) : DriveState
    data class Playing(val capsuleId: Long) : DriveState
    /** Interrupted by a safety signal; [resumeTo] comes back once the signal clears. */
    data class Paused(val resumeTo: DriveState) : DriveState
}

sealed interface DriveEvent {
    data object BuryRequested : DriveEvent          // "묻어줘" or steering button
    data object RecordingEnded : DriveEvent         // "그만", 30 s limit, end button
    data class CapsuleWithinRange(val capsuleId: Long) : DriveEvent // 1.2 km
    data object PlayRequested : DriveEvent          // "들려줘"
    data class PlayCapsule(val capsuleId: Long) : DriveEvent // album / home card, while parked
    data object Later : DriveEvent                  // "다음에" or 10 s silence
    data object PlaybackFinished : DriveEvent       // end of audio or the stop button
    data object SafetySignalOn : DriveEvent
    data object SafetySignalOff : DriveEvent
}

sealed interface Effect {
    data object StartRecording : Effect
    data object SaveRecording : Effect
    data class ShowApproachAlert(val capsuleId: Long) : Effect
    data object HideApproachAlert : Effect
    data class StartPlayback(val capsuleId: Long) : Effect
    data object PausePlayback : Effect
    data object StopPlayback : Effect
    data class RepromptAtNextStop(val capsuleId: Long) : Effect
    /** Send [DriveEvent.SafetySignalOff]-driven resume after this delay (2 s playback, 10 s alert). */
    data class ResumeAfter(val millis: Long, val target: DriveState) : Effect
}

data class Transition(val state: DriveState, val effects: List<Effect> = emptyList())

object DriveStateMachine {
    const val RESUME_PLAYBACK_MS = 2_000L
    const val RESUME_ALERT_MS = 10_000L

    fun reduce(state: DriveState, event: DriveEvent): Transition = when (state) {
        DriveState.Idle -> when (event) {
            DriveEvent.BuryRequested -> Transition(DriveState.Recording, listOf(Effect.StartRecording))
            is DriveEvent.CapsuleWithinRange ->
                Transition(DriveState.ApproachAlert(event.capsuleId), listOf(Effect.ShowApproachAlert(event.capsuleId)))
            is DriveEvent.PlayCapsule ->
                Transition(DriveState.Playing(event.capsuleId), listOf(Effect.StartPlayback(event.capsuleId)))
            else -> Transition(state)
        }

        DriveState.Recording -> when (event) {
            // Safety signal ends the recording and keeps what was captured (spec F-01).
            DriveEvent.RecordingEnded, DriveEvent.SafetySignalOn ->
                Transition(DriveState.Idle, listOf(Effect.SaveRecording))
            else -> Transition(state)
        }

        is DriveState.ApproachAlert -> when (event) {
            DriveEvent.PlayRequested ->
                Transition(DriveState.Playing(state.capsuleId), listOf(Effect.HideApproachAlert, Effect.StartPlayback(state.capsuleId)))
            DriveEvent.Later ->
                Transition(DriveState.Idle, listOf(Effect.HideApproachAlert, Effect.RepromptAtNextStop(state.capsuleId)))
            DriveEvent.SafetySignalOn ->
                Transition(DriveState.Paused(state), listOf(Effect.HideApproachAlert))
            else -> Transition(state)
        }

        is DriveState.Playing -> when (event) {
            DriveEvent.PlaybackFinished -> Transition(DriveState.Idle, listOf(Effect.StopPlayback))
            DriveEvent.SafetySignalOn -> Transition(DriveState.Paused(state), listOf(Effect.PausePlayback))
            else -> Transition(state)
        }

        is DriveState.Paused -> when (event) {
            DriveEvent.SafetySignalOff -> {
                val delay = if (state.resumeTo is DriveState.Playing) RESUME_PLAYBACK_MS else RESUME_ALERT_MS
                Transition(state, listOf(Effect.ResumeAfter(delay, state.resumeTo)))
            }
            // Stopping while paused ends playback; an alert left paused becomes "later".
            DriveEvent.PlaybackFinished -> when (val r = state.resumeTo) {
                is DriveState.Playing -> Transition(DriveState.Idle, listOf(Effect.StopPlayback))
                is DriveState.ApproachAlert -> Transition(DriveState.Idle, listOf(Effect.RepromptAtNextStop(r.capsuleId)))
                else -> Transition(DriveState.Idle)
            }
            else -> Transition(state)
        }
    }

    /** Called by the runner when a [Effect.ResumeAfter] delay elapses with no new safety signal. */
    fun resume(target: DriveState): Transition = when (target) {
        is DriveState.Playing -> Transition(target, listOf(Effect.StartPlayback(target.capsuleId)))
        is DriveState.ApproachAlert -> Transition(target, listOf(Effect.ShowApproachAlert(target.capsuleId)))
        else -> Transition(target)
    }
}
