package uk.rdfn.h.off.alerts

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.rdfn.h.off.washer.MachineState
import uk.rdfn.h.off.washer.WashPhase
import uk.rdfn.h.off.washer.WasherStatus

/** Ported from src/notifications/__tests__/planFinishAlert.test.ts. */
class FinishAlertPlannerTest {
	private val minute = 60_000L
	private val now = Instant.parse("2026-10-25T10:00:00Z").toEpochMilli()

	private fun statusWith(
		state: MachineState,
		remainingMinutes: Int = 0,
		programName: String? = "Cottons",
		errorCode: String? = null,
	) = WasherStatus(
		state = state,
		phase = WashPhase.READY,
		programName = programName,
		remainingMinutes = remainingMinutes,
		delayMinutes = 0,
		temperatureCelsius = null,
		spinSpeedRpm = null,
		currentCycleEnergyKwh = null,
		currentCycleWaterLitres = null,
		doorLocked = false,
		remoteControlEnabled = true,
		errorCode = errorCode,
		fetchedAt = Instant.ofEpochMilli(now),
	)

	/** Memory after an alert was scheduled to go off at [fireAtMillis]. */
	private fun scheduledFor(fireAtMillis: Long) = FinishAlertMemory(
		scheduledNotificationId = "alert-1",
		scheduledForMillis = fireAtMillis,
		previousState = MachineState.RUNNING,
		lastKnownProgramName = "Cottons",
	)

	@Test
	fun schedulesAnAlertForWhenARunningWashWillEnd() {
		val plan = FinishAlertPlanner.plan(FinishAlertMemory(), statusWith(MachineState.RUNNING, 45), now)
		assertEquals(listOf(FinishAlertStep.Schedule(now + 45 * minute, "Cottons")), plan.steps)
		assertEquals(now + 45 * minute, plan.nextMemory.scheduledForMillis)
		assertEquals(MachineState.RUNNING, plan.nextMemory.previousState)
	}

	@Test
	fun leavesTheAlertAloneForSmallChangesInTheEstimate() {
		assertTrue(FinishAlertPlanner.plan(scheduledFor(now + 45 * minute), statusWith(MachineState.RUNNING, 46), now).steps.isEmpty())
	}

	@Test
	fun movesTheAlertWhenTheEstimateChangesALot() {
		assertEquals(
			listOf(
				FinishAlertStep.CancelScheduled("alert-1"),
				FinishAlertStep.Schedule(now + 60 * minute, "Cottons"),
			),
			FinishAlertPlanner.plan(scheduledFor(now + 45 * minute), statusWith(MachineState.RUNNING, 60), now).steps,
		)
	}

	@Test
	fun cancelsTheAlertWhilePaused() {
		val plan = FinishAlertPlanner.plan(scheduledFor(now + 45 * minute), statusWith(MachineState.PAUSED), now)
		assertEquals(listOf(FinishAlertStep.CancelScheduled("alert-1")), plan.steps)
		assertNull(plan.nextMemory.scheduledForMillis)
	}

	@Test
	fun alertsStraightAwayWhenTheWashFinishedBeforeTheScheduledAlert() {
		assertEquals(
			listOf(
				FinishAlertStep.CancelScheduled("alert-1"),
				FinishAlertStep.ShowNow(FinishAlertStep.Reason.FINISHED, "Cottons", null),
			),
			FinishAlertPlanner.plan(scheduledFor(now + 10 * minute), statusWith(MachineState.FINISHED), now).steps,
		)
	}

	@Test
	fun doesNotAlertTwiceWhenTheScheduledAlertAlreadyWentOff() {
		val steps = FinishAlertPlanner.plan(scheduledFor(now - 2 * minute), statusWith(MachineState.FINISHED), now).steps
		assertFalse(steps.any { it is FinishAlertStep.ShowNow })
	}

	@Test
	fun doesNotAlertForAWashThatFinishedBeforeTheAppWasWatching() {
		assertTrue(FinishAlertPlanner.plan(FinishAlertMemory(), statusWith(MachineState.FINISHED), now).steps.isEmpty())
	}

	@Test
	fun keepsTheAlertAndTheLastStateWhenTheMachineDropsOffline() {
		val memory = scheduledFor(now + 30 * minute)
		val plan = FinishAlertPlanner.plan(memory, statusWith(MachineState.OFFLINE), now)
		assertTrue(plan.steps.isEmpty())
		assertEquals(memory, plan.nextMemory)
	}

	@Test
	fun usesTheRememberedProgramNameWhenTheStatusHasNone() {
		val steps = FinishAlertPlanner.plan(scheduledFor(now + 10 * minute), statusWith(MachineState.FINISHED, programName = null), now).steps
		assertTrue(steps.contains(FinishAlertStep.ShowNow(FinishAlertStep.Reason.FINISHED, "Cottons", null)))
	}

	@Test
	fun warnsOnceAboutAnError() {
		val first = FinishAlertPlanner.plan(scheduledFor(now + 10 * minute), statusWith(MachineState.ERROR, errorCode = "E21"), now)
		assertTrue(first.steps.contains(FinishAlertStep.ShowNow(FinishAlertStep.Reason.ERROR, "Cottons", "E21")))
		val second = FinishAlertPlanner.plan(first.nextMemory, statusWith(MachineState.ERROR, errorCode = "E21"), now + minute)
		assertTrue(second.steps.isEmpty())
	}
}
