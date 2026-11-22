package uk.rdfn.h.off.history

import java.time.Instant
import kotlinx.serialization.Serializable
import uk.rdfn.h.off.washer.MachineState
import uk.rdfn.h.off.washer.WasherStatus

/**
 * h.on only keeps lifetime totals, so we keep our own list of finished
 * washes with what each one used. It lives only on this phone.
 */
@Serializable
data class FinishedCycle(
	val programName: String?,
	/** ISO date, e.g. 2026-11-03T21:54:00Z. */
	val finishedAt: String,
	val energyKwh: Double?,
	val waterLitres: Double?,
)

/** What we saw at the previous check, so we can spot a wash ending. */
@Serializable
data class LastSeenCycle(
	val state: MachineState,
	val programName: String?,
	val energyKwh: Double?,
	val waterLitres: Double?,
)

/** Everything the wash log keeps. */
@Serializable
data class CycleLogState(
	val entries: List<FinishedCycle> = emptyList(),
	val lastSeen: LastSeenCycle? = null,
)

/**
 * Spotting the end of a wash. Ported from src/storage/cycleLog.ts; the
 * saving itself is done by the app.
 */
object CycleLog {
	const val MAX_ENTRIES = 100

	data class Update(val nextState: CycleLogState, val justFinishedCycle: FinishedCycle?)

	/**
	 * Call with every new status. When a wash has just ended, it is added to
	 * the log using the last usage figures seen while it was running (some
	 * machines reset them to zero at the end), and returned.
	 */
	fun record(current: CycleLogState, status: WasherStatus, now: Instant = Instant.now()): Update {
		if (status.state == MachineState.OFFLINE) return Update(current, null)

		val lastSeen = current.lastSeen
		val wasRunning = lastSeen?.state == MachineState.RUNNING || lastSeen?.state == MachineState.PAUSED
		var entries = current.entries
		var justFinishedCycle: FinishedCycle? = null
		if (wasRunning && status.state == MachineState.FINISHED) {
			justFinishedCycle = FinishedCycle(
				programName = status.programName ?: lastSeen?.programName,
				finishedAt = now.toString(),
				energyKwh = status.currentCycleEnergyKwh?.takeIf { it != 0.0 } ?: lastSeen?.energyKwh,
				waterLitres = status.currentCycleWaterLitres?.takeIf { it != 0.0 } ?: lastSeen?.waterLitres,
			)
			entries = (listOf(justFinishedCycle) + entries).take(MAX_ENTRIES)
		}

		val nextLastSeen = LastSeenCycle(
			state = status.state,
			programName = status.programName,
			energyKwh = status.currentCycleEnergyKwh,
			waterLitres = status.currentCycleWaterLitres,
		)
		return Update(CycleLogState(entries, nextLastSeen), justFinishedCycle)
	}
}
