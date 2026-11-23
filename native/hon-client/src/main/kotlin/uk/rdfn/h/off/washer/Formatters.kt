package uk.rdfn.h.off.washer

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import uk.rdfn.h.off.hon.DryingOption

/**
 * Turning numbers and states into the words the screens show.
 *
 * Ported from src/washer/formatters.ts. Times are written the UK way (24-hour, "14:05").
 */
object Formatters {
	private val CLOCK_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)
	private val DAY_AND_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.UK)

	/** 83 → "1 h 23 min", 45 → "45 min", 0 → "0 min". */
	fun formatDuration(totalMinutes: Number): String {
		val roundedMinutes = max(0, totalMinutes.toDouble().roundToInt())
		val hours = roundedMinutes / 60
		val minutes = roundedMinutes % 60
		return when {
			hours == 0 -> "$minutes min"
			minutes == 0 -> "$hours h"
			else -> "$hours h $minutes min"
		}
	}

	/** The clock time a wash will end, e.g. "14:05". */
	fun formatFinishTime(remainingMinutes: Int, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String =
		CLOCK_TIME.format(now.plusSeconds(remainingMinutes * 60L).atZone(zone))

	/**
	 * The clock time a number of minutes from now: "14:30", or "tomorrow
	 * 07:00" when it falls on the next day.
	 */
	fun formatClockTimeFromNow(minutesFromNow: Int, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
		val moment = now.plusSeconds(minutesFromNow * 60L).atZone(zone)
		val time = CLOCK_TIME.format(moment)
		return if (moment.toLocalDate() == now.atZone(zone).toLocalDate()) time else "tomorrow $time"
	}

	/** A clock time, e.g. "14:05". */
	fun formatClockTime(moment: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
		CLOCK_TIME.format(moment.atZone(zone))

	/** "Today 14:05", "Yesterday 09:30" or "12 Oct 18:20". */
	fun formatPastDate(date: Instant, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
		val moment = date.atZone(zone)
		val time = CLOCK_TIME.format(moment)
		val today = now.atZone(zone).toLocalDate()
		return when (moment.toLocalDate()) {
			today -> "Today $time"
			today.minusDays(1) -> "Yesterday $time"
			else -> "${DAY_AND_MONTH.format(moment)} $time"
		}
	}

	/** The big headline on the status screen. */
	fun describeState(state: MachineState): String = when (state) {
		MachineState.OFFLINE -> "Machine offline"
		MachineState.IDLE -> "Ready"
		MachineState.SCHEDULED -> "Delayed start"
		MachineState.RUNNING -> "Washing"
		MachineState.PAUSED -> "Paused"
		MachineState.FINISHED -> "Finished"
		MachineState.ERROR -> "Needs attention"
	}

	fun describePhase(phase: WashPhase): String = when (phase) {
		WashPhase.READY -> "Ready"
		WashPhase.WEIGHING -> "Weighing the load"
		WashPhase.HEATING -> "Heating water"
		WashPhase.WASHING -> "Washing"
		WashPhase.RINSE -> "Rinsing"
		WashPhase.SPIN -> "Spinning"
		WashPhase.DRYING -> "Drying"
		WashPhase.STEAM -> "Steaming"
		WashPhase.TUMBLING -> "Tumbling"
		WashPhase.REFRESH -> "Refreshing"
		WashPhase.SCHEDULED -> "Waiting to start"
		WashPhase.UNKNOWN -> ""
	}

	/** "40" + "°C" → "40 °C"; "0" for temperature means a cold wash. */
	fun formatOptionValue(key: String, value: String, unit: String?): String {
		val number = value.toDoubleOrNull()
		return when {
			key == OptionKeys.TEMPERATURE && number == 0.0 -> "Cold"
			key == OptionKeys.SPIN_SPEED && number == 0.0 -> "No spin"
			key == OptionKeys.DELAY -> if ((number ?: 0.0) == 0.0) "Off" else formatDuration(number ?: 0.0)
			key == OptionKeys.EXTRA_RINSES -> if (number == 0.0) "None" else value
			key == OptionKeys.DRYING -> when (val drying = DryingOption.describe(value)) {
				is DryingOption.Meaning.Level -> drying.label
				is DryingOption.Meaning.Minutes -> formatDuration(drying.minutes)
			}
			unit != null -> "$value $unit"
			else -> value
		}
	}

	/** "0.42" kWh, "31" L: numbers without needless decimals. */
	fun formatAmount(value: Double?, unit: String, decimals: Int = 1): String {
		if (value == null) return "–"
		val rounded = String.format(Locale.UK, "%.${decimals}f", value).trimEnd('0').trimEnd('.')
		return "$rounded $unit"
	}
}
