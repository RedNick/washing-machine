package uk.rdfn.h.off.washer

import java.time.Instant
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A pretend washing machine that behaves like a real one, for trying the app
 * (and developing it, and for Play's reviewers) without an h.on account.
 * Nothing leaves the phone.
 *
 * In the demo, one real second counts as one machine minute, so a whole
 * 2-hour wash (and its "finished" alert) plays out in about 2 minutes.
 *
 * Ported from src/washer/demoWasher.ts.
 *
 * @param currentTimeMillis lets tests control the clock.
 */
class DemoWasher(private val currentTimeMillis: () -> Long = System::currentTimeMillis) : WasherService {
	override val isDemo = true

	/** Where the demo wash is, in machine minutes. */
	private class DemoCycle(
		val program: WasherProgram,
		val totalMinutes: Double,
		/** Machine minutes run before the latest resume. */
		var minutesRunBeforeResume: Double,
		/** Real time of the latest start/resume, or null while paused. */
		var resumedAtMillis: Long?,
		val temperatureCelsius: Double,
		val spinSpeedRpm: Double,
	)

	private val lock = Mutex()
	private var cycle: DemoCycle? = null
	private var finishedProgramName: String? = null
	private var totalEnergyKwh = 33.34
	private var totalWaterLitres = 2571.0
	private var totalCycles = 45.0
	private val pastCommands = ArrayDeque<PastCommand>()

	override suspend fun getMachineInfo() = MachineInfo(displayName = "Demo washer", modelName = "Demo model", brand = "Demo")

	override suspend fun getStatus(): WasherStatus = lock.withLock {
		finishCycleIfDone()
		val fetchedAt = Instant.ofEpochMilli(currentTimeMillis())
		val runningCycle = cycle
		if (runningCycle == null) {
			return@withLock WasherStatus(
				state = if (finishedProgramName != null) MachineState.FINISHED else MachineState.IDLE,
				phase = WashPhase.READY,
				programName = finishedProgramName,
				remainingMinutes = 0,
				delayMinutes = 0,
				temperatureCelsius = null,
				spinSpeedRpm = null,
				currentCycleEnergyKwh = null,
				currentCycleWaterLitres = null,
				doorLocked = false,
				remoteControlEnabled = true,
				errorCode = null,
				fetchedAt = fetchedAt,
			)
		}

		val minutesRun = minutesRun(runningCycle)
		val progress = minutesRun / runningCycle.totalMinutes
		WasherStatus(
			state = if (runningCycle.resumedAtMillis == null) MachineState.PAUSED else MachineState.RUNNING,
			phase = phaseForProgress(progress),
			programName = runningCycle.program.displayName,
			remainingMinutes = max(0, ceil(runningCycle.totalMinutes - minutesRun).toInt()),
			delayMinutes = 0,
			temperatureCelsius = runningCycle.temperatureCelsius,
			spinSpeedRpm = if (progress >= 0.85) runningCycle.spinSpeedRpm else 0.0,
			currentCycleEnergyKwh = (progress * 0.9 * 100).roundToInt() / 100.0,
			currentCycleWaterLitres = (progress * 55).roundToInt().toDouble(),
			doorLocked = true,
			remoteControlEnabled = true,
			errorCode = null,
			fetchedAt = fetchedAt,
		)
	}

	override suspend fun getPrograms(): List<WasherProgram> = DEMO_PROGRAMS

	override suspend fun startProgram(programId: String, chosenOptions: ChosenOptions) = lock.withLock {
		finishCycleIfDone()
		if (cycle != null) throw WasherCommandException("A program is already running. Stop it first.")
		val program = DEMO_PROGRAMS.find { it.id == programId }
			?: throw WasherCommandException("Unknown program \"$programId\".")
		cycle = DemoCycle(
			program = program,
			totalMinutes = (program.estimatedMinutes ?: 60).toDouble(),
			minutesRunBeforeResume = 0.0,
			resumedAtMillis = currentTimeMillis(),
			temperatureCelsius = chosenOptions[OptionKeys.TEMPERATURE]?.toDoubleOrNull() ?: 40.0,
			spinSpeedRpm = chosenOptions[OptionKeys.SPIN_SPEED]?.toDoubleOrNull() ?: 1200.0,
		)
		finishedProgramName = null
		recordCommand("startProgram", program.displayName)
	}

	override suspend fun pause() = lock.withLock {
		val runningCycle = requireRunningCycle()
		if (runningCycle.resumedAtMillis == null) return@withLock
		runningCycle.minutesRunBeforeResume = minutesRun(runningCycle)
		runningCycle.resumedAtMillis = null
		recordCommand("pauseProgram", runningCycle.program.displayName)
	}

	override suspend fun resume() = lock.withLock {
		val runningCycle = requireRunningCycle()
		if (runningCycle.resumedAtMillis != null) return@withLock
		runningCycle.resumedAtMillis = currentTimeMillis()
		recordCommand("resumeProgram", runningCycle.program.displayName)
	}

	override suspend fun stop() = lock.withLock {
		val runningCycle = requireRunningCycle()
		cycle = null
		finishedProgramName = null
		recordCommand("stopProgram", runningCycle.program.displayName)
	}

	override suspend fun getUsageSummary(): UsageSummary = lock.withLock {
		finishCycleIfDone()
		UsageSummary(
			totalEnergyKwh = (totalEnergyKwh * 100).roundToInt() / 100.0,
			totalWaterLitres = totalWaterLitres,
			totalCycles = totalCycles,
			temperatureBands = listOf(
				TemperatureBand("0–30 °C", 16),
				TemperatureBand("40 °C", 18),
				TemperatureBand("60–90 °C", 11),
			),
		)
	}

	override suspend fun getRecentCommands(): List<PastCommand> = lock.withLock { pastCommands.toList() }

	private fun minutesRun(demoCycle: DemoCycle): Double {
		val resumedAt = demoCycle.resumedAtMillis ?: return demoCycle.minutesRunBeforeResume
		val realSecondsSinceResume = (currentTimeMillis() - resumedAt) / 1000.0
		return min(
			demoCycle.totalMinutes,
			demoCycle.minutesRunBeforeResume + realSecondsSinceResume * DEMO_MINUTES_PER_REAL_SECOND,
		)
	}

	/** Moves a completed demo wash into the totals. */
	private fun finishCycleIfDone() {
		val runningCycle = cycle ?: return
		if (minutesRun(runningCycle) < runningCycle.totalMinutes) return
		totalEnergyKwh += 0.9
		totalWaterLitres += 55
		totalCycles += 1
		finishedProgramName = runningCycle.program.displayName
		cycle = null
	}

	private fun requireRunningCycle(): DemoCycle {
		finishCycleIfDone()
		return cycle ?: throw WasherCommandException("Nothing is running.")
	}

	private fun recordCommand(commandName: String, programName: String) {
		pastCommands.addFirst(PastCommand(commandName, programName, Instant.ofEpochMilli(currentTimeMillis())))
	}

	companion object {
		const val DEMO_MINUTES_PER_REAL_SECOND = 1.0

		private fun phaseForProgress(progress: Double): WashPhase = when {
			progress < 0.05 -> WashPhase.WEIGHING
			progress < 0.15 -> WashPhase.HEATING
			progress < 0.6 -> WashPhase.WASHING
			progress < 0.85 -> WashPhase.RINSE
			else -> WashPhase.SPIN
		}

		private val TEMPERATURE_CHOICES = listOf("0", "20", "30", "40", "60", "90")
		private val SPIN_CHOICES = listOf("0", "400", "800", "1000", "1200", "1400")

		private fun delayStart() = ProgramOption.Range(OptionKeys.DELAY, "Delay start", 0.0, 1410.0, 30.0, 0.0, "min")

		/** A few typical programs, shaped like what h.on returns for real machines. */
		val DEMO_PROGRAMS: List<WasherProgram> = listOf(
			WasherProgram(
				id = "PROGRAMS.WM_WD.COTTONS",
				displayName = "Cottons",
				description = "Maximum washing for cotton garments, even when fully loaded.",
				estimatedMinutes = 163,
				isBuiltIn = true,
				options = listOf(
					ProgramOption.Choice(OptionKeys.TEMPERATURE, "Temperature", TEMPERATURE_CHOICES, "40", "°C"),
					ProgramOption.Choice(OptionKeys.SPIN_SPEED, "Spin speed", SPIN_CHOICES, "1400", "rpm"),
					delayStart(),
					ProgramOption.Toggle("prewash", "Prewash", isOnByDefault = false),
					ProgramOption.Choice(OptionKeys.EXTRA_RINSES, "Extra rinses", listOf("0", "1", "2", "3"), "0", null),
				),
			),
			WasherProgram(
				id = "PROGRAMS.WM_WD.ECO_40_60_NEW_ENERGY_LABEL",
				displayName = "Eco 40-60",
				description = "The most efficient program for normally soiled cotton.",
				estimatedMinutes = 228,
				isBuiltIn = true,
				options = listOf(
					ProgramOption.Choice(OptionKeys.SPIN_SPEED, "Spin speed", SPIN_CHOICES, "1200", "rpm"),
					delayStart(),
				),
			),
			WasherProgram(
				id = "PROGRAMS.WM_WD.SYNTHETIC_AND_COLOURED",
				displayName = "Synthetic and coloured",
				description = "For synthetic and mixed fabrics.",
				estimatedMinutes = 109,
				isBuiltIn = true,
				options = listOf(
					ProgramOption.Choice(OptionKeys.TEMPERATURE, "Temperature", listOf("0", "20", "30", "40", "60"), "40", "°C"),
					ProgramOption.Choice(OptionKeys.SPIN_SPEED, "Spin speed", SPIN_CHOICES.take(5), "1000", "rpm"),
					ProgramOption.Toggle("anticrease", "Anti-crease", isOnByDefault = false),
				),
			),
			WasherProgram(
				id = "PROGRAMS.WM_WD.CARE_14",
				displayName = "Care 14",
				description = "A quick refresh for lightly soiled clothes.",
				estimatedMinutes = 14,
				isBuiltIn = true,
				options = listOf(
					ProgramOption.Choice(OptionKeys.TEMPERATURE, "Temperature", listOf("0", "20", "30"), "30", "°C"),
				),
			),
			WasherProgram(
				id = "PROGRAMS.WM_WD.WASH_DRY_MIX",
				displayName = "Wash and dry mix",
				description = "Washes, then dries by level or for a set time.",
				estimatedMinutes = 180,
				isBuiltIn = true,
				options = listOf(
					ProgramOption.Choice(OptionKeys.TEMPERATURE, "Temperature", listOf("20", "30", "40"), "40", "°C"),
					ProgramOption.Choice(
						OptionKeys.DRYING,
						"Drying",
						listOf("none", "level:1", "level:2", "level:3", "time:1", "time:2", "time:3", "time:4"),
						"level:2",
						null,
					),
				),
			),
			WasherProgram(
				id = "PROGRAMS.WM_WD.RINSE",
				displayName = "Rinse",
				description = "Rinse and spin only.",
				estimatedMinutes = 25,
				isBuiltIn = true,
				options = emptyList(),
			),
			WasherProgram(
				id = "PROGRAMS.WM_WD.IOT_WASH_BED_LINEN",
				displayName = "Bed linen",
				description = "An app-only program for sheets and duvet covers.",
				estimatedMinutes = 120,
				isBuiltIn = false,
				options = listOf(
					ProgramOption.Choice(OptionKeys.TEMPERATURE, "Temperature", listOf("40", "60"), "60", "°C"),
				),
			),
		)
	}
}
