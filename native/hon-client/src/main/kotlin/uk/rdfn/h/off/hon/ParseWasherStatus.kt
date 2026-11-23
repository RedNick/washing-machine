package uk.rdfn.h.off.hon

import java.time.Duration
import java.time.Instant
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import uk.rdfn.h.off.washer.MachineState
import uk.rdfn.h.off.washer.RawReading
import uk.rdfn.h.off.washer.WashPhase
import uk.rdfn.h.off.washer.WasherStatus

/**
 * Turns h.on's live state (GET /commands/v1/context) into the app's
 * [WasherStatus].
 *
 * Ported from src/hon/parseWasherStatus.ts.
 */
object WasherStatusParser {
	/**
	 * h.on reports the machine's mode as a number ("machMode"). Meanings come
	 * from the Home Assistant hon integration.
	 */
	private val MACHINE_STATE_BY_MODE = mapOf(
		0 to MachineState.IDLE, // no state
		1 to MachineState.IDLE, // choosing a program on the machine
		2 to MachineState.RUNNING,
		3 to MachineState.PAUSED,
		4 to MachineState.SCHEDULED, // delayed start being set up
		5 to MachineState.SCHEDULED, // delayed start counting down
		6 to MachineState.ERROR,
		7 to MachineState.FINISHED,
		8 to MachineState.IDLE, // factory test mode
		9 to MachineState.FINISHED, // stopping
	)

	/** h.on's program phase numbers ("prPhase") for washing machines. */
	private val WASH_PHASE_BY_CODE = mapOf(
		0 to WashPhase.READY,
		1 to WashPhase.WASHING,
		2 to WashPhase.WASHING,
		3 to WashPhase.SPIN,
		4 to WashPhase.RINSE,
		5 to WashPhase.RINSE,
		6 to WashPhase.RINSE,
		7 to WashPhase.DRYING,
		8 to WashPhase.DRYING,
		9 to WashPhase.STEAM,
		10 to WashPhase.READY,
		11 to WashPhase.SPIN,
		12 to WashPhase.WEIGHING,
		13 to WashPhase.WEIGHING,
		14 to WashPhase.WASHING,
		15 to WashPhase.WASHING,
		16 to WashPhase.WASHING,
		17 to WashPhase.RINSE,
		18 to WashPhase.RINSE,
		19 to WashPhase.SCHEDULED,
		20 to WashPhase.TUMBLING,
		24 to WashPhase.REFRESH,
		25 to WashPhase.WASHING,
		26 to WashPhase.HEATING,
		27 to WashPhase.WASHING,
	)

	/**
	 * Some machines (Nicolas's washer dryer) never report the end of a
	 * program: h.on keeps saying "running, 1 minute left" long after the wash
	 * is done. If the time left has sat at 1 minute or less for this long,
	 * treat the program as finished.
	 */
	const val STUCK_AT_END_MINUTES = 10L

	/** Reads a number from h.on's string values, or null if it isn't one. */
	private fun toNumberOrNull(rawValue: String?): Double? {
		if (rawValue.isNullOrEmpty()) return null
		return rawValue.replace(',', '.').trim().toDoubleOrNull()?.takeIf { it.isFinite() }
	}

	private fun parseInstant(text: String?): Instant? =
		text?.let { runCatching { Instant.parse(it) }.getOrNull() }

	/**
	 * The name of the program that was started, from h.on's "activity" record.
	 *
	 * Several programs share one machine program code (prCode 4 is both
	 * "Synthetic and coloured" and "Colored"), so the code alone can give the
	 * wrong name. The activity names the exact program, but it lingers after
	 * a cycle, so it's only trusted when its code matches the one running now.
	 */
	private fun programNameFromActivity(context: JsonElement, runningProgramCode: Double?): String? {
		val attributes = context["activity"]["attributes"].asObject() ?: return null
		val programName = attributes["programName"].asText()
		if (programName.isNullOrBlank()) return null
		if (runningProgramCode == null || toNumberOrNull(attributes["prCode"].asText()) != runningProgramCode) return null
		return ProgramCatalogue.programDisplayName(programName)
	}

	/**
	 * @param programNameByCode maps the machine's numeric program code to a
	 *   readable name, built from the program catalogue.
	 */
	fun parse(
		context: JsonElement,
		programNameByCode: Map<Int, String>,
		fetchedAt: Instant = Instant.now(),
	): WasherStatus {
		val rawParameters = context["shadow"]["parameters"].asObject() ?: JsonObject(emptyMap())
		fun readValue(name: String): String? = rawParameters[name]["parNewVal"].asText()

		val isConnected = context["lastConnEvent"]["category"].asText() != "DISCONNECTED"
		val machineMode = toNumberOrNull(readValue("machMode"))?.toInt() ?: 0
		val reportedState =
			if (isConnected) MACHINE_STATE_BY_MODE[machineMode] ?: MachineState.IDLE else MachineState.OFFLINE
		val state =
			if (hasStalledAtTheEnd(reportedState, rawParameters["remainingTimeMM"], fetchedAt)) MachineState.FINISHED
			else reportedState

		val phaseCode = toNumberOrNull(readValue("prPhase"))?.toInt()
		val phase = if (phaseCode == null) WashPhase.UNKNOWN else WASH_PHASE_BY_CODE[phaseCode] ?: WashPhase.UNKNOWN

		val programCode = toNumberOrNull(readValue("prCode"))
		val programName = programNameFromActivity(context, programCode)
			?: programCode?.takeIf { it > 0 }?.let { programNameByCode[it.toInt()] }

		// The machine keeps the last remaining time after a program ends, so
		// only trust it while something is actually running or paused.
		val remainingMinutes =
			if (state.isMidCycle) toNumberOrNull(readValue("remainingTimeMM"))?.toInt() ?: 0 else 0
		val delayMinutes =
			if (state == MachineState.SCHEDULED) toNumberOrNull(readValue("delayTime"))?.toInt() ?: 0 else 0

		// "00" (or "0") means no error.
		val rawErrorCode = readValue("errors") ?: ""
		val errorCode = if (rawErrorCode.all { it == '0' }) null else rawErrorCode

		return WasherStatus(
			state = state,
			phase = phase,
			programName = programName,
			remainingMinutes = remainingMinutes,
			delayMinutes = delayMinutes,
			temperatureCelsius = toNumberOrNull(readValue("temp")),
			spinSpeedRpm = toNumberOrNull(readValue("spinSpeed")),
			currentCycleEnergyKwh = toNumberOrNull(readValue("currentElectricityUsed")),
			currentCycleWaterLitres = toNumberOrNull(readValue("currentWaterUsed")),
			doorLocked = readDoorLocked(readValue("doorLockStatus")),
			remoteControlEnabled = readValue("remoteCtrValid") == "1",
			errorCode = errorCode,
			fetchedAt = fetchedAt,
			rawReadings = listRawReadings(rawParameters),
		)
	}

	/** True when a "running" program's last minute has lasted far too long. */
	private fun hasStalledAtTheEnd(reportedState: MachineState, remainingTime: JsonElement?, fetchedAt: Instant): Boolean {
		if (reportedState != MachineState.RUNNING) return false
		val lastChanged = parseInstant(remainingTime["lastUpdate"].asText()) ?: return false
		val minutesLeft = toNumberOrNull(remainingTime["parNewVal"].asText()) ?: return false
		if (minutesLeft > 1) return false
		return Duration.between(lastChanged, fetchedAt) >= Duration.ofMinutes(STUCK_AT_END_MINUTES)
	}

	/**
	 * "doorLockStatus" 1 means locked (as in the Home Assistant hon
	 * integration). Machines that don't send it get null rather than a guess.
	 */
	private fun readDoorLocked(doorLockStatus: String?): Boolean? = doorLockStatus?.let { it == "1" }

	/** All of h.on's parameters as name/value pairs, sorted by name. */
	private fun listRawReadings(rawParameters: JsonObject): List<RawReading> =
		rawParameters.map { (name, parameter) ->
			RawReading(
				name = name,
				value = parameter["parNewVal"].asText() ?: "",
				lastUpdated = parseInstant(parameter["lastUpdate"].asText()),
			)
		}.sortedBy { it.name.lowercase() }

	/** Lifetime totals the machine keeps in its live state. */
	data class LifetimeTotals(val totalEnergyKwh: Double?, val totalWaterLitres: Double?, val totalCycles: Double?)

	fun parseLifetimeTotals(context: JsonElement): LifetimeTotals {
		val rawParameters = context["shadow"]["parameters"]
		return LifetimeTotals(
			totalEnergyKwh = toNumberOrNull(rawParameters["totalElectricityUsed"]["parNewVal"].asText()),
			totalWaterLitres = toNumberOrNull(rawParameters["totalWaterUsed"]["parNewVal"].asText()),
			totalCycles = toNumberOrNull(rawParameters["totalWashCycle"]["parNewVal"].asText()),
		)
	}
}
