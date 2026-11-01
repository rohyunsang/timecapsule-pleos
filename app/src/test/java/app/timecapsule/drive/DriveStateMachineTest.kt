package app.timecapsule.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveStateMachineTest {

    private fun reduce(s: DriveState, e: DriveEvent) = DriveStateMachine.reduce(s, e)

    @Test fun buryThenEndSaves() {
        val t1 = reduce(DriveState.Idle, DriveEvent.BuryRequested)
        assertEquals(DriveState.Recording, t1.state)
        val t2 = reduce(t1.state, DriveEvent.RecordingEnded)
        assertEquals(DriveState.Idle, t2.state)
        assertEquals(listOf(Effect.SaveRecording), t2.effects)
    }

    @Test fun safetySignalWhileRecordingStillSaves() {
        val t = reduce(DriveState.Recording, DriveEvent.SafetySignalOn)
        assertEquals(DriveState.Idle, t.state)
        assertEquals(listOf(Effect.SaveRecording), t.effects)
    }

    @Test fun approachPlayAndFinish() {
        val alert = reduce(DriveState.Idle, DriveEvent.CapsuleWithinRange(7)).state
        assertEquals(DriveState.ApproachAlert(7), alert)
        val play = reduce(alert, DriveEvent.PlayRequested)
        assertEquals(DriveState.Playing(7), play.state)
        assertTrue(Effect.StartPlayback(7) in play.effects)
        assertEquals(DriveState.Idle, reduce(play.state, DriveEvent.PlaybackFinished).state)
    }

    @Test fun laterRepromptsAtNextStop() {
        val t = reduce(DriveState.ApproachAlert(3), DriveEvent.Later)
        assertEquals(DriveState.Idle, t.state)
        assertTrue(Effect.RepromptAtNextStop(3) in t.effects)
    }

    @Test fun playbackPausesAndResumesAfterTwoSeconds() {
        val paused = reduce(DriveState.Playing(5), DriveEvent.SafetySignalOn)
        assertEquals(DriveState.Paused(DriveState.Playing(5)), paused.state)
        assertEquals(listOf(Effect.PausePlayback), paused.effects)

        val off = reduce(paused.state, DriveEvent.SafetySignalOff)
        assertEquals(listOf(Effect.ResumeAfter(2_000, DriveState.Playing(5))), off.effects)

        val resumed = DriveStateMachine.resume(DriveState.Playing(5))
        assertEquals(DriveState.Playing(5), resumed.state)
    }

    @Test fun alertSuppressedThenShownTenSecondsLater() {
        val paused = reduce(DriveState.ApproachAlert(9), DriveEvent.SafetySignalOn)
        assertEquals(DriveState.Paused(DriveState.ApproachAlert(9)), paused.state)
        val off = reduce(paused.state, DriveEvent.SafetySignalOff)
        assertEquals(listOf(Effect.ResumeAfter(10_000, DriveState.ApproachAlert(9))), off.effects)
    }

    @Test fun cannotBuryWhilePlaying() {
        assertEquals(DriveState.Playing(1), reduce(DriveState.Playing(1), DriveEvent.BuryRequested).state)
    }

    @Test fun albumPlaysDirectlyAndStopWhilePausedEnds() {
        val play = reduce(DriveState.Idle, DriveEvent.PlayCapsule(3))
        assertEquals(DriveState.Playing(3), play.state)
        assertEquals(listOf(Effect.StartPlayback(3)), play.effects)

        val paused = reduce(play.state, DriveEvent.SafetySignalOn)
        val stop = reduce(paused.state, DriveEvent.PlaybackFinished)
        assertEquals(DriveState.Idle, stop.state)
        assertEquals(listOf(Effect.StopPlayback), stop.effects)
    }

    @Test fun pausedAlertDismissedBecomesLater() {
        val stop = reduce(DriveState.Paused(DriveState.ApproachAlert(4)), DriveEvent.PlaybackFinished)
        assertEquals(listOf(Effect.RepromptAtNextStop(4)), stop.effects)
    }
}
