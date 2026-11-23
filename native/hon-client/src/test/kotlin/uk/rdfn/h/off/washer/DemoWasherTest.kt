package uk.rdfn.h.off.washer

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/** Ported from src/washer/__tests__/demoWasher.test.ts. */
class DemoWasherTest {
	/** A clock the test can move forward. */
	private class TestClock {
		var currentMillis = 1_793_000_000_000L
		fun advanceSeconds(seconds: Int) {
			currentMillis += seconds * 1000L
		}
	}

	@Test
	fun runsAProgramFromStartToFinishOneMachineMinutePerRealSecond() = runTest {
		val clock = TestClock()
		val washer = DemoWasher { clock.currentMillis }

		washer.startProgram("PROGRAMS.WM_WD.CARE_14", mapOf("temp" to "30"))
		var status = washer.getStatus()
		assertEquals(MachineState.RUNNING, status.state)
		assertEquals("Care 14", status.programName)
		assertEquals(14, status.remainingMinutes)
		assertEquals(30.0, status.temperatureCelsius)

		clock.advanceSeconds(10)
		assertEquals(4, washer.getStatus().remainingMinutes)

		clock.advanceSeconds(5)
		status = washer.getStatus()
		assertEquals(MachineState.FINISHED, status.state)
		assertEquals("Care 14", status.programName)
		assertEquals(0, status.remainingMinutes)

		assertEquals(46.0, washer.getUsageSummary().totalCycles)
	}

	@Test
	fun standsStillWhilePaused() = runTest {
		val clock = TestClock()
		val washer = DemoWasher { clock.currentMillis }
		washer.startProgram("PROGRAMS.WM_WD.RINSE", emptyMap())
		clock.advanceSeconds(5)
		washer.pause()
		clock.advanceSeconds(100)
		val paused = washer.getStatus()
		assertEquals(MachineState.PAUSED, paused.state)
		assertEquals(20, paused.remainingMinutes)

		washer.resume()
		clock.advanceSeconds(5)
		assertEquals(15, washer.getStatus().remainingMinutes)
	}

	@Test
	fun refusesASecondProgramWhileOneIsRunning() = runTest {
		val washer = DemoWasher { 0L }
		washer.startProgram("PROGRAMS.WM_WD.RINSE", emptyMap())
		try {
			washer.startProgram("PROGRAMS.WM_WD.COTTONS", emptyMap())
			fail("Expected the second program to be refused")
		} catch (expected: WasherCommandException) {
			// Only one wash at a time.
		}
	}

	@Test
	fun recordsTheCommandsItWasSentNewestFirst() = runTest {
		val washer = DemoWasher { 0L }
		washer.startProgram("PROGRAMS.WM_WD.RINSE", emptyMap())
		washer.stop()
		assertEquals(listOf("stopProgram", "startProgram"), washer.getRecentCommands().map { it.commandName })
		assertEquals(MachineState.IDLE, washer.getStatus().state)
	}
}
