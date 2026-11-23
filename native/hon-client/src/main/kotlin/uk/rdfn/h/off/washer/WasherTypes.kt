package uk.rdfn.h.off.washer

import java.time.Instant
import kotlinx.serialization.json.JsonElement

/*
 * The app's own view of a washing machine.
 *
 * Screens only ever use these types. The real h.on client and the demo
 * machine both translate their data into them, so the UI does not need to
 * know which one it is talking to.
 */

/** What the machine is doing, simplified from h.on's "machMode" numbers. */
enum class MachineState {
	/** The machine is not connected to the cloud. */
	OFFLINE,

	/** Switched on, waiting for a program. */
	IDLE,

	/** Delayed start is counting down. */
	SCHEDULED,
	RUNNING,
	PAUSED,

	/** The last program has ended. */
	FINISHED,
	ERROR;

	/** Running, paused or waiting for a delayed start. */
	val isMidCycle: Boolean get() = this == RUNNING || this == PAUSED || this == SCHEDULED
}

/** The part of the wash the machine is in, e.g. rinse or spin. */
enum class WashPhase {
	READY, WEIGHING, HEATING, WASHING, RINSE, SPIN, DRYING, STEAM, TUMBLING, REFRESH, SCHEDULED, UNKNOWN
}

data class MachineInfo(
	/** The name you gave the machine in h.on, or its model name. */
	val displayName: String,
	val modelName: String,
	val brand: String,
)

/** One value straight from h.on, e.g. machMode = "2". */
data class RawReading(
	val name: String,
	val value: String,
	/** When the machine last changed this value, if h.on says. */
	val lastUpdated: Instant?,
)

data class WasherStatus(
	val state: MachineState,
	val phase: WashPhase,
	/** Name of the running (or last) program, if known. */
	val programName: String?,
	/** Minutes until the program ends. Zero when nothing is running. */
	val remainingMinutes: Int,
	/** Minutes until a delayed start begins. Zero when not scheduled. */
	val delayMinutes: Int,
	val temperatureCelsius: Double?,
	val spinSpeedRpm: Double?,
	/** Energy and water used by the current program so far. */
	val currentCycleEnergyKwh: Double?,
	val currentCycleWaterLitres: Double?,
	/**
	 * Null when the machine doesn't report its door lock at all (Nicolas's
	 * washer dryer sends no door values), so the app doesn't show a guess.
	 */
	val doorLocked: Boolean?,
	/**
	 * The machine only accepts commands from the app when "remote control"
	 * is switched on at the machine itself.
	 */
	val remoteControlEnabled: Boolean,
	/** Error code reported by the machine, or null when everything is fine. */
	val errorCode: String?,
	/** When this status was fetched. */
	val fetchedAt: Instant,
	/**
	 * Every value the machine reported, unprocessed. Shown under "Machine
	 * readings" so a model that reports things differently can be diagnosed.
	 */
	val rawReadings: List<RawReading> = emptyList(),
)

/** Keys of the options the app combines or treats specially. */
object OptionKeys {
	/** Delay start, in minutes from when you press Start until the wash starts. */
	const val DELAY = "delayTime"

	/**
	 * h.on has a separate on/off switch for each extra rinse (extraRinse1, 2
	 * and 3). The app shows them as one "how many" choice under this key, and
	 * turns it back into the switches when it sends the command.
	 */
	const val EXTRA_RINSES = "extraRinses"

	/**
	 * Washer dryers: one "Drying" choice that stands for h.on's dryLevel and
	 * dryTime settings, which can't both be used at once. See DryingOption.kt.
	 */
	const val DRYING = "drying"
	const val TEMPERATURE = "temp"
	const val SPIN_SPEED = "spinSpeed"
}

/**
 * One adjustable setting of a program, e.g. temperature or extra rinse.
 *
 * Every value is kept as the string the machine expects: toggles are "1" or
 * "0" and numbers are written without a decimal point when they are whole.
 */
sealed interface ProgramOption {
	val key: String
	val label: String
	val defaultValue: String

	data class Choice(
		override val key: String,
		override val label: String,
		/** Allowed values, already in the format the machine expects. */
		val choices: List<String>,
		override val defaultValue: String,
		val unit: String?,
	) : ProgramOption

	data class Toggle(
		override val key: String,
		override val label: String,
		val isOnByDefault: Boolean,
	) : ProgramOption {
		override val defaultValue: String get() = if (isOnByDefault) TOGGLE_ON else TOGGLE_OFF
	}

	data class Range(
		override val key: String,
		override val label: String,
		val minimum: Double,
		val maximum: Double,
		val step: Double,
		val defaultNumber: Double,
		val unit: String?,
	) : ProgramOption {
		override val defaultValue: String get() = formatHonNumber(defaultNumber)

		/** Every value the range allows, in order. */
		fun allowedValues(): List<Double> {
			val values = mutableListOf<Double>()
			var value = minimum
			while (value <= maximum + 1e-9) {
				values += value
				value += if (step > 0) step else 1.0
			}
			return values
		}
	}

	companion object {
		const val TOGGLE_ON = "1"
		const val TOGGLE_OFF = "0"
	}
}

/** h.on's hint about the fabric, from its "dryType" (C = cotton, S = synthetic). */
enum class FabricHint { COTTON, SYNTHETICS }

data class WasherProgram(
	/** h.on's own program id, e.g. "PROGRAMS.WM_WD.COTTONS". */
	val id: String,
	val displayName: String,
	val description: String,
	/** The machine's estimate of how long the program takes, in minutes. */
	val estimatedMinutes: Int?,
	/**
	 * Built-in programs are the ones on the machine's dial. The others are
	 * extra "app only" programs that h.on downloads to the machine.
	 */
	val isBuiltIn: Boolean,
	/** Only used to sort programs named after a colour, such as Whites, under a fabric chip. */
	val fabricHint: FabricHint? = null,
	val options: List<ProgramOption>,
)

/** Values the user picked for a program's options, keyed by option key, as the machine's strings. */
typealias ChosenOptions = Map<String, String>

data class TemperatureBand(val label: String, val count: Int)

data class UsageSummary(
	val totalEnergyKwh: Double?,
	val totalWaterLitres: Double?,
	val totalCycles: Double?,
	/** How often each temperature band has been used, e.g. "0–30 °C": 16. */
	val temperatureBands: List<TemperatureBand>,
)

/** One command from the h.on command history, e.g. startProgram. */
data class PastCommand(
	val commandName: String,
	val programName: String?,
	val sentAt: Instant?,
)

/** A titled block of raw JSON from h.on. */
data class DiagnosticBlock(
	val title: String,
	/** Pretty-printed for the screen, and cut short if it's long. */
	val json: String,
	/** The same data uncut, for "Send a report" (which removes private details first). */
	val data: JsonElement,
)

/** A program the machine has but the app doesn't offer, and why. */
data class LeftOutProgram(val id: String, val reason: String)

/**
 * Everything the screens can ask of a washing machine. Implemented by
 * HonCloudWasher (the real one) and DemoWasher (a fake one for trying the
 * app without an account).
 */
interface WasherService {
	val isDemo: Boolean
	suspend fun getMachineInfo(): MachineInfo
	suspend fun getStatus(): WasherStatus
	suspend fun getPrograms(): List<WasherProgram>
	suspend fun startProgram(programId: String, chosenOptions: ChosenOptions)
	suspend fun pause()
	suspend fun resume()
	suspend fun stop()
	suspend fun getUsageSummary(): UsageSummary
	suspend fun getRecentCommands(): List<PastCommand>

	/**
	 * Raw h.on data that the app doesn't use yet, shown under "Machine
	 * readings" to look for a reliable "wash finished" signal. Empty for the demo.
	 */
	suspend fun getDiagnostics(): List<DiagnosticBlock> = emptyList()

	/** Programs the machine has but the app doesn't offer, and why. Empty for the demo. */
	suspend fun getLeftOutPrograms(): List<LeftOutProgram> = emptyList()
}

/** Thrown when the machine refuses or cannot receive a command. */
class WasherCommandException(message: String) : Exception(message)

/**
 * Writes a number the way h.on (and JavaScript's String()) does: whole
 * numbers without a decimal point ("40", not "40.0").
 */
fun formatHonNumber(value: Double): String =
	if (value == Math.floor(value) && !value.isInfinite() && kotlin.math.abs(value) < 1e15) {
		value.toLong().toString()
	} else {
		value.toString()
	}
