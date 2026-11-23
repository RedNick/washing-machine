package uk.rdfn.h.off.washer

import java.time.Instant
import java.time.ZoneId

/**
 * The words for a status, shared by the Now screen, the countdown
 * notification, the home-screen widget and the Quick Settings tile, so
 * they always agree.
 */
data class StatusSummary(
	/** e.g. "Washing", "Finished". */
	val headline: String,
	/** The big line: "1 h 5 min" or "Almost done". Null when there's no time to show. */
	val timeText: String?,
	/** The line under it, e.g. "left · done around 14:05" or "Ready to unload.". */
	val caption: String?,
	/** What the machine is doing right now, e.g. "Rinsing". Null when not useful. */
	val phaseText: String?,
	/** When the wash should end. Null unless it is running and counting down. */
	val finishAt: Instant?,
) {
	/** One line for small spaces: "42 min left · done around 14:05". */
	val shortDetail: String
		get() = when {
			timeText != null && caption != null && caption.startsWith("left") -> "$timeText $caption"
			timeText != null -> timeText
			caption != null -> caption
			else -> ""
		}

	companion object {
		/**
		 * Machines can sit at "1 minute left" for a while at the end of a wash
		 * while they cool down and unlock, so a countdown that isn't moving is
		 * shown as "Almost done" instead.
		 */
		const val ALMOST_DONE_MINUTES = 1

		fun of(status: WasherStatus, zone: ZoneId = ZoneId.systemDefault()): StatusSummary {
			val isFinishingUp = status.state == MachineState.RUNNING && status.remainingMinutes <= ALMOST_DONE_MINUTES
			val isCounting = status.state == MachineState.RUNNING || status.state == MachineState.PAUSED
			val phaseText = Formatters.describePhase(status.phase)
				.takeIf { it.isNotEmpty() && status.state == MachineState.RUNNING && !isFinishingUp }
			return when {
				isFinishingUp -> StatusSummary(
					headline = Formatters.describeState(status.state),
					timeText = "Almost done",
					caption = "The machine is finishing the program. It can take a few minutes before it says it's done.",
					phaseText = null,
					finishAt = null,
				)
				isCounting -> StatusSummary(
					headline = Formatters.describeState(status.state),
					timeText = Formatters.formatDuration(status.remainingMinutes),
					caption = if (status.state == MachineState.PAUSED) {
						"left when resumed"
					} else {
						"left · done around ${Formatters.formatFinishTime(status.remainingMinutes, status.fetchedAt, zone)}"
					},
					phaseText = phaseText,
					finishAt = if (status.state == MachineState.RUNNING) {
						status.fetchedAt.plusSeconds(status.remainingMinutes * 60L)
					} else {
						null
					},
				)
				else -> StatusSummary(
					headline = Formatters.describeState(status.state),
					timeText = null,
					caption = when (status.state) {
						MachineState.SCHEDULED -> "Starts in ${Formatters.formatDuration(status.delayMinutes)}"
						MachineState.FINISHED -> "Ready to unload."
						MachineState.OFFLINE -> "The machine isn't connected to h.on. Check it's switched on and in Wi-Fi range."
						MachineState.ERROR -> status.errorCode?.let { "Error code $it" }
						else -> null
					},
					phaseText = null,
					finishAt = null,
				)
			}
		}
	}
}
