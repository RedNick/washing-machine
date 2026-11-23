package uk.rdfn.h.off.washer

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatusSummaryTest {
	private val london = ZoneId.of("Europe/London")
	private val checkedAt = Instant.parse("2026-11-22T13:00:00Z")

	private fun status(state: MachineState, remainingMinutes: Int = 0, delayMinutes: Int = 0) = WasherStatus(
		state = state,
		phase = WashPhase.RINSE,
		programName = "Cottons",
		remainingMinutes = remainingMinutes,
		delayMinutes = delayMinutes,
		temperatureCelsius = null,
		spinSpeedRpm = null,
		currentCycleEnergyKwh = null,
		currentCycleWaterLitres = null,
		doorLocked = null,
		remoteControlEnabled = true,
		errorCode = null,
		fetchedAt = checkedAt,
	)

	@Test
	fun `a running wash shows the time left, the finish time and the phase`() {
		val summary = StatusSummary.of(status(MachineState.RUNNING, remainingMinutes = 65), london)
		assertEquals("1 h 5 min", summary.timeText)
		assertEquals("left · done around 14:05", summary.caption)
		assertEquals("Rinsing", summary.phaseText)
		assertEquals(Instant.parse("2026-11-22T14:05:00Z"), summary.finishAt)
		assertEquals("1 h 5 min left · done around 14:05", summary.shortDetail)
	}

	@Test
	fun `one minute left reads as almost done, with no countdown`() {
		val summary = StatusSummary.of(status(MachineState.RUNNING, remainingMinutes = 1), london)
		assertEquals("Almost done", summary.timeText)
		assertNull(summary.finishAt)
		assertEquals("Almost done", summary.shortDetail)
	}

	@Test
	fun `paused and scheduled washes explain themselves`() {
		assertEquals("left when resumed", StatusSummary.of(status(MachineState.PAUSED, remainingMinutes = 30), london).caption)
		assertEquals("Starts in 2 h", StatusSummary.of(status(MachineState.SCHEDULED, delayMinutes = 120), london).caption)
		assertEquals("Ready to unload.", StatusSummary.of(status(MachineState.FINISHED), london).shortDetail)
	}
}
