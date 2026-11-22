package uk.rdfn.h.off.hon

import java.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.rdfn.h.off.hon.HonFixtures.contextWith
import uk.rdfn.h.off.hon.HonFixtures.runningContext
import uk.rdfn.h.off.washer.MachineState
import uk.rdfn.h.off.washer.RawReading
import uk.rdfn.h.off.washer.WashPhase

/** Ported from src/hon/__tests__/parseWasherStatus.test.ts. */
class WasherStatusParserTest {
	private val programNames = mapOf(201 to "Cottons")

	private fun activityNaming(programName: String, programCode: String) = buildJsonObject {
		putJsonObject("attributes") {
			put("programName", programName)
			put("prCode", programCode)
		}
	}

	@Test
	fun readsARunningWash() {
		val status = WasherStatusParser.parse(runningContext, programNames)
		assertEquals(MachineState.RUNNING, status.state)
		assertEquals(WashPhase.RINSE, status.phase)
		assertEquals("Cottons", status.programName)
		assertEquals(83, status.remainingMinutes)
		assertEquals(40.0, status.temperatureCelsius)
		assertEquals(0.42, status.currentCycleEnergyKwh)
		assertEquals(31.0, status.currentCycleWaterLitres)
		assertEquals(true, status.doorLocked)
		assertTrue(status.remoteControlEnabled)
		assertNull(status.errorCode)
	}

	@Test
	fun namesTheRunningProgramFromActivityWhenItsCodeMatches() {
		// prCode 4 is shared by several programs, so the code alone would say "Synthetic and coloured".
		val context = contextWith(mapOf("machMode" to "2", "prCode" to "4"), activity = activityNaming("IOT_WASH_COLOURED", "4"))
		assertEquals("Coloured", WasherStatusParser.parse(context, mapOf(4 to "Synthetic and coloured")).programName)
	}

	@Test
	fun ignoresALeftoverActivityFromADifferentProgram() {
		val context =
			contextWith(mapOf("machMode" to "2", "prCode" to "4"), activity = activityNaming("PROGRAMS.WM_WD.HIGH_DRY", "16"))
		assertEquals(
			"Synthetic and coloured",
			WasherStatusParser.parse(context, mapOf(4 to "Synthetic and coloured")).programName,
		)
	}

	@Test
	fun ignoresTheLeftoverTimeOnceAProgramHasFinished() {
		val status = WasherStatusParser.parse(contextWith(mapOf("machMode" to "7", "remainingTimeMM" to "83")), programNames)
		assertEquals(MachineState.FINISHED, status.state)
		assertEquals(0, status.remainingMinutes)
	}

	@Test
	fun reportsOfflineWhenTheMachineIsDisconnected() {
		val status = WasherStatusParser.parse(contextWith(mapOf("machMode" to "2"), connection = "DISCONNECTED"), programNames)
		assertEquals(MachineState.OFFLINE, status.state)
	}

	@Test
	fun readsDelayedStartsAndErrorCodes() {
		val scheduled = WasherStatusParser.parse(contextWith(mapOf("machMode" to "5", "delayTime" to "120")), programNames)
		assertEquals(MachineState.SCHEDULED, scheduled.state)
		assertEquals(120, scheduled.delayMinutes)

		val broken = WasherStatusParser.parse(contextWith(mapOf("machMode" to "6", "errors" to "E21")), programNames)
		assertEquals(MachineState.ERROR, broken.state)
		assertEquals("E21", broken.errorCode)
	}

	@Test
	fun listsEveryRawReadingSortedByNameWithWhenItLastChanged() {
		val context = contextWith(
			mapOf("prPhase" to "7", "machMode" to "2"),
			lastUpdates = mapOf("machMode" to "2026-11-03T15:58:00Z"),
		)
		assertEquals(
			listOf(
				RawReading("machMode", "2", Instant.parse("2026-11-03T15:58:00Z")),
				RawReading("prPhase", "7", null),
			),
			WasherStatusParser.parse(context, programNames).rawReadings,
		)
	}

	@Test
	fun treatsAProgramStuckOnItsLastMinuteAsFinished() {
		val context = contextWith(
			mapOf("machMode" to "2", "prPhase" to "7", "remainingTimeMM" to "1"),
			lastUpdates = mapOf("remainingTimeMM" to "2026-11-03T21:36:00Z"),
		)
		val soonAfter = WasherStatusParser.parse(context, programNames, Instant.parse("2026-11-03T21:40:00Z"))
		assertEquals(MachineState.RUNNING, soonAfter.state)

		val longAfter = WasherStatusParser.parse(context, programNames, Instant.parse("2026-11-03T21:54:00Z"))
		assertEquals(MachineState.FINISHED, longAfter.state)
		assertEquals(0, longAfter.remainingMinutes)
	}

	@Test
	fun keepsCountingDownNormallyWhenMoreThanAMinuteIsLeft() {
		val context = contextWith(
			mapOf("machMode" to "2", "remainingTimeMM" to "25"),
			lastUpdates = mapOf("remainingTimeMM" to "2026-11-03T20:00:00Z"),
		)
		assertEquals(
			MachineState.RUNNING,
			WasherStatusParser.parse(context, programNames, Instant.parse("2026-11-03T21:00:00Z")).state,
		)
	}

	@Test
	fun leavesTheDoorUnknownWhenTheMachineDoesNotReportIt() {
		assertNull(WasherStatusParser.parse(contextWith(mapOf("machMode" to "2")), programNames).doorLocked)
	}

	@Test
	fun copesWithAnEmptyResponse() {
		val status = WasherStatusParser.parse(JsonObject(emptyMap()), programNames)
		assertEquals(MachineState.IDLE, status.state)
		assertEquals(WashPhase.UNKNOWN, status.phase)
		assertNull(status.programName)
		assertFalse(status.remoteControlEnabled)
	}

	@Test
	fun readsLifetimeEnergyWaterAndWashCount() {
		assertEquals(
			WasherStatusParser.LifetimeTotals(totalEnergyKwh = 33.34, totalWaterLitres = 2571.0, totalCycles = 45.0),
			WasherStatusParser.parseLifetimeTotals(runningContext),
		)
	}
}
