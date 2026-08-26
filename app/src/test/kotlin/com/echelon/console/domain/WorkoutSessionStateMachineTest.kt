package com.echelon.console.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutSessionStateMachineTest {
    @Test
    fun `new timeline is ready and start exposes current next and countdown`() {
        val notStarted = createState(timeline())

        val result = WorkoutSessionStateMachine.start(notStarted)

        val running = assertRunning(result)
        assertEquals(0, running.progress.elapsedSeconds)
        assertEquals(180, running.progress.remainingSeconds)
        assertEquals(0, running.progress.currentSegmentIndex)
        assertEquals("Warm Up", running.progress.currentSegment.name)
        assertEquals("Build", running.progress.nextSegment?.name)
        assertEquals(60, running.progress.secondsUntilNextSegment)
        assertEquals(WorkoutSessionTargetMode.PROFILE, running.progress.target.mode)
    }

    @Test
    fun `positive elapsed within a segment updates remaining and countdown`() {
        val result = WorkoutSessionStateMachine.advance(runningState(), 30)

        val advanced = assertRunning(result)
        assertEquals(30, advanced.progress.elapsedSeconds)
        assertEquals(150, advanced.progress.remainingSeconds)
        assertEquals("Warm Up", advanced.progress.currentSegment.name)
        assertEquals("Build", advanced.progress.nextSegment?.name)
        assertEquals(30, advanced.progress.secondsUntilNextSegment)
    }

    @Test
    fun `exact segment boundary selects the next segment`() {
        val result = WorkoutSessionStateMachine.advance(runningState(), 60)

        val advanced = assertRunning(result)
        assertEquals(60, advanced.progress.elapsedSeconds)
        assertEquals(120, advanced.progress.remainingSeconds)
        assertEquals(1, advanced.progress.currentSegmentIndex)
        assertEquals("Build", advanced.progress.currentSegment.name)
        assertEquals("Finish", advanced.progress.nextSegment?.name)
        assertEquals(60, advanced.progress.secondsUntilNextSegment)
    }

    @Test
    fun `overshoot crosses multiple boundaries and clamps at completed`() {
        val result = WorkoutSessionStateMachine.advance(runningState(), 200)

        val completed = assertCompleted(result)
        assertEquals(180, completed.elapsedSeconds)
        assertEquals(0, completed.remainingSeconds)
    }

    @Test
    fun `pause preserves progress and elapsed events stay frozen until resume`() {
        val running = assertRunning(WorkoutSessionStateMachine.advance(runningState(), 30))

        val paused = assertPaused(WorkoutSessionStateMachine.pause(running))
        val frozen = assertPaused(WorkoutSessionStateMachine.advance(paused, 30))

        assertEquals(paused, frozen)

        val resumed = assertRunning(WorkoutSessionStateMachine.resume(paused))
        assertEquals(30, resumed.progress.elapsedSeconds)
        assertEquals("Warm Up", resumed.progress.currentSegment.name)
        assertEquals(30, resumed.progress.secondsUntilNextSegment)
    }

    @Test
    fun `manual override remains for current segment and clears at next boundary`() {
        val running = assertRunning(WorkoutSessionStateMachine.advance(runningState(), 30))

        val overridden = assertRunning(
            WorkoutSessionStateMachine.applyManualOverride(
                state = running,
                speed = SpeedTenths(45),
                incline = InclineTenths(25),
            ),
        )
        assertEquals(45, overridden.progress.target.speed.value)
        assertEquals(25, overridden.progress.target.incline.value)
        assertEquals(WorkoutSessionTargetMode.MANUAL, overridden.progress.target.mode)
        assertEquals(running.timeline, overridden.timeline)

        val retained = assertRunning(WorkoutSessionStateMachine.advance(overridden, 20))
        assertEquals(WorkoutSessionTarget(SpeedTenths(45), InclineTenths(25), WorkoutSessionTargetMode.MANUAL), retained.progress.target)

        val nextSegment = assertRunning(WorkoutSessionStateMachine.advance(overridden, 30))
        assertEquals(1, nextSegment.progress.currentSegmentIndex)
        assertEquals(WorkoutSessionTarget(SpeedTenths(40), InclineTenths(20), WorkoutSessionTargetMode.PROFILE), nextSegment.progress.target)
    }

    @Test
    fun `stop is terminal and completed or stopped states reject restart advance and resume`() {
        val stopped = assertStopped(WorkoutSessionStateMachine.stop(runningState()))
        assertEquals(0, stopped.elapsedSeconds)
        assertInvalid(WorkoutSessionStateMachine.start(stopped), WorkoutSessionAction.START)
        assertInvalid(WorkoutSessionStateMachine.advance(stopped, 1), WorkoutSessionAction.ADVANCE)
        assertInvalid(WorkoutSessionStateMachine.resume(stopped), WorkoutSessionAction.RESUME)

        val completed = assertCompleted(WorkoutSessionStateMachine.advance(runningState(), 180))
        assertInvalid(WorkoutSessionStateMachine.start(completed), WorkoutSessionAction.START)
        assertInvalid(WorkoutSessionStateMachine.advance(completed, 1), WorkoutSessionAction.ADVANCE)
        assertInvalid(WorkoutSessionStateMachine.resume(completed), WorkoutSessionAction.RESUME)
    }

    @Test
    fun `invalid transitions and non-positive elapsed return explicit errors`() {
        val notStarted = createState(timeline())

        assertInvalid(
            WorkoutSessionStateMachine.advance(notStarted, 1),
            WorkoutSessionAction.ADVANCE,
        )
        assertInvalid(
            WorkoutSessionStateMachine.advance(runningState(), 0),
            WorkoutSessionError.NonPositiveElapsed(0),
        )
        assertInvalid(
            WorkoutSessionStateMachine.advance(runningState(), -1),
            WorkoutSessionError.NonPositiveElapsed(-1),
        )
        assertInvalid(
            WorkoutSessionStateMachine.pause(notStarted),
            WorkoutSessionAction.PAUSE,
        )
    }

    @Test
    fun `empty timeline cannot create a session`() {
        val empty = WorkoutTimeline(
            programId = ProgramId("EMPTY"),
            totalDurationSeconds = 0,
            segments = emptyList(),
        )

        val result = WorkoutSessionStateMachine.create(empty)

        assertEquals(
            WorkoutSessionResult.Invalid(WorkoutSessionError.EmptyTimeline),
            result,
        )
    }

    private fun timeline(): WorkoutTimeline = WorkoutTimeline(
        programId = ProgramId("TEST"),
        totalDurationSeconds = 180,
        segments = listOf(
            WorkoutTimelineSegment("Warm Up", 0, 60, SpeedTenths(30), InclineTenths(10)),
            WorkoutTimelineSegment("Build", 60, 120, SpeedTenths(40), InclineTenths(20)),
            WorkoutTimelineSegment("Finish", 120, 180, SpeedTenths(50), InclineTenths(30)),
        ),
    )

    private fun startState(): WorkoutSessionState.NotStarted = createState(timeline())

    private fun runningState(): WorkoutSessionState.Running = assertRunning(
        WorkoutSessionStateMachine.start(startState()),
    )

    private fun createState(timeline: WorkoutTimeline): WorkoutSessionState.NotStarted {
        val result = WorkoutSessionStateMachine.create(timeline)
        return when (result) {
            is WorkoutSessionResult.Valid -> result.state as WorkoutSessionState.NotStarted
            is WorkoutSessionResult.Invalid -> error("Expected a ready session, got $result")
        }
    }

    private fun assertRunning(result: WorkoutSessionResult): WorkoutSessionState.Running = when (result) {
        is WorkoutSessionResult.Valid -> result.state as WorkoutSessionState.Running
        is WorkoutSessionResult.Invalid -> error("Expected running state, got $result")
    }

    private fun assertPaused(result: WorkoutSessionResult): WorkoutSessionState.Paused = when (result) {
        is WorkoutSessionResult.Valid -> result.state as WorkoutSessionState.Paused
        is WorkoutSessionResult.Invalid -> error("Expected paused state, got $result")
    }

    private fun assertCompleted(result: WorkoutSessionResult): WorkoutSessionState.Completed = when (result) {
        is WorkoutSessionResult.Valid -> result.state as WorkoutSessionState.Completed
        is WorkoutSessionResult.Invalid -> error("Expected completed state, got $result")
    }

    private fun assertStopped(result: WorkoutSessionResult): WorkoutSessionState.Stopped = when (result) {
        is WorkoutSessionResult.Valid -> result.state as WorkoutSessionState.Stopped
        is WorkoutSessionResult.Invalid -> error("Expected stopped state, got $result")
    }

    private fun assertInvalid(
        result: WorkoutSessionResult,
        expectedAction: WorkoutSessionAction,
    ) {
        val invalid = result as? WorkoutSessionResult.Invalid
            ?: error("Expected invalid result, got $result")
        assertTrue(invalid.error is WorkoutSessionError.InvalidTransition)
        assertEquals(expectedAction, (invalid.error as WorkoutSessionError.InvalidTransition).action)
    }

    private fun assertInvalid(
        result: WorkoutSessionResult,
        expectedError: WorkoutSessionError,
    ) {
        assertEquals(WorkoutSessionResult.Invalid(expectedError), result)
    }
}
