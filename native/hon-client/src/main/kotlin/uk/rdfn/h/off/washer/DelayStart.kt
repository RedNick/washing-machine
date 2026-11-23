package uk.rdfn.h.off.washer

import java.time.Instant
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Delay start, worked out as clock times. The machine counts its delay
 * from now until the wash starts, and only in fixed steps (usually 30
 * minutes), so − and + move the time by one step and the times shown are
 * the exact ones the machine will use. Ported from
 * src/components/DelayStartControl.tsx.
 */
object DelayStart {
	/** Start straight away, pick when the wash starts, or pick when it should be done. */
	enum class Mode(val label: String) { NOW("Start now"), START_AT("Start at"), FINISH_BY("Finish by") }

	/** "Finish by" needs to know how long the program takes. */
	fun availableModes(estimatedMinutes: Int?): List<Mode> =
		if (estimatedMinutes != null && estimatedMinutes > 0) Mode.entries else listOf(Mode.NOW, Mode.START_AT)

	/** The shortest delay the machine accepts, once you've asked for one. */
	fun smallestDelay(option: ProgramOption.Range): Int = max(option.minimum, option.step).roundToInt()

	/** The delay to use after switching to [mode]. */
	fun delayForMode(mode: Mode, currentDelay: Int, option: ProgramOption.Range): Int = when {
		mode == Mode.NOW -> 0
		currentDelay == 0 -> smallestDelay(option)
		else -> currentDelay
	}

	/** One step later (+1) or earlier (−1), kept within what the machine allows. */
	fun stepBy(direction: Int, currentDelay: Int, option: ProgramOption.Range): Int {
		val nextDelay = currentDelay + direction * option.step.roundToInt()
		return min(option.maximum.roundToInt(), max(smallestDelay(option), nextDelay))
	}

	/** The clock time shown between − and +: the start time, or for "Finish by" the end time. */
	fun pickedTime(mode: Mode, delayMinutes: Int, estimatedMinutes: Int?, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String =
		if (mode == Mode.FINISH_BY && estimatedMinutes != null && estimatedMinutes > 0) {
			Formatters.formatClockTimeFromNow(delayMinutes + estimatedMinutes, now, zone)
		} else {
			Formatters.formatClockTimeFromNow(delayMinutes, now, zone)
		}

	/** What happens, in a sentence under the controls. */
	fun summary(mode: Mode, delayMinutes: Int, estimatedMinutes: Int?, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
		val startTime = Formatters.formatClockTimeFromNow(delayMinutes, now, zone)
		val finishTime = estimatedMinutes?.takeIf { it > 0 }?.let { Formatters.formatClockTimeFromNow(delayMinutes + it, now, zone) }
		return when (mode) {
			Mode.NOW -> if (finishTime != null) "Starts straight away and finishes about $finishTime." else "Starts straight away."
			Mode.START_AT -> "Starts in ${Formatters.formatDuration(delayMinutes)}" + (finishTime?.let { ", finishes about $it" } ?: "") + "."
			Mode.FINISH_BY -> "Starts at $startTime (in ${Formatters.formatDuration(delayMinutes)}) so it's done by $finishTime."
		}
	}
}
