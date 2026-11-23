package uk.rdfn.h.off.history

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uk.rdfn.h.off.washer.MachineState
import uk.rdfn.h.off.washer.WashPhase
import uk.rdfn.h.off.washer.WasherStatus

class CycleLogTest {
	private val finishTime = Instant.parse("2026-11-03T21:54:00Z")

	private fun statusWith(state: MachineState, energyKwh: Double? = null, waterLitres: Double? = null, programName: String? = "Cottons") =
		WasherStatus(
			state = state,
			phase = WashPhase.READY,
			programName = programName,
			remainingMinutes = 0,
			delayMinutes = 0,
			temperatureCelsius = null,
			spinSpeedRpm = null,
			currentCycleEnergyKwh = energyKwh,
			currentCycleWaterLitres = waterLitres,
			doorLocked = null,
			remoteControlEnabled = true,
			errorCode = null,
			fetchedAt = finishTime,
		)

	@Test
	fun logsAWashWhenItGoesFromRunningToFinishedUsingTheLastFiguresSeenWhileRunning() {
		val running = CycleLog.record(CycleLogState(), statusWith(MachineState.RUNNING, 0.8, 50.0)).nextState
		// Some machines reset the figures to zero at the end.
		val finished = CycleLog.record(running, statusWith(MachineState.FINISHED, 0.0, 0.0, programName = null), finishTime)
		assertEquals(FinishedCycle("Cottons", "2026-11-03T21:54:00Z", 0.8, 50.0), finished.justFinishedCycle)
		assertEquals(1, finished.nextState.entries.size)
	}

	@Test
	fun doesNotLogTheSameFinishTwice() {
		val running = CycleLog.record(CycleLogState(), statusWith(MachineState.RUNNING)).nextState
		val finished = CycleLog.record(running, statusWith(MachineState.FINISHED)).nextState
		val stillFinished = CycleLog.record(finished, statusWith(MachineState.FINISHED))
		assertNull(stillFinished.justFinishedCycle)
		assertEquals(1, stillFinished.nextState.entries.size)
	}

	@Test
	fun ignoresGoingOffline() {
		val running = CycleLog.record(CycleLogState(), statusWith(MachineState.RUNNING)).nextState
		assertEquals(running, CycleLog.record(running, statusWith(MachineState.OFFLINE)).nextState)
	}

	@Test
	fun keepsOnlyTheNewestHundredWashes() {
		val full = CycleLogState(
			entries = List(CycleLog.MAX_ENTRIES) { FinishedCycle("Old $it", "2026-01-01T00:00:00Z", null, null) },
			lastSeen = LastSeenCycle(MachineState.RUNNING, "Cottons", null, null),
		)
		val next = CycleLog.record(full, statusWith(MachineState.FINISHED)).nextState
		assertEquals(CycleLog.MAX_ENTRIES, next.entries.size)
		assertEquals("Cottons", next.entries.first().programName)
	}
}
