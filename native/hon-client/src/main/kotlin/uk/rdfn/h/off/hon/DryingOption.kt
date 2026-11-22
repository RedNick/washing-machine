package uk.rdfn.h.off.hon

import uk.rdfn.h.off.washer.OptionKeys
import uk.rdfn.h.off.washer.ProgramOption

/**
 * Washer dryers have two drying settings in h.on: a dryness level
 * ("dryLevel") and a drying time ("dryTime"). The machine only accepts one
 * or the other, so the app shows them as one "Drying" choice and splits it
 * back into the two settings when it starts a program.
 *
 * Choice values look like "none", "level:2" or "time:3".
 *
 * Ported from src/hon/dryingOption.ts.
 */
object DryingOption {
	/** h.on's drying setting names. */
	const val DRY_LEVEL_KEY = "dryLevel"
	const val DRY_TIME_KEY = "dryTime"

	const val NO_DRYING = "none"
	private const val LEVEL_PREFIX = "level:"
	private const val TIME_PREFIX = "time:"

	/** What each dryness level is called on the machine (confirmed by Nicolas). */
	private val DRY_LEVEL_NAMES = mapOf(
		0 to "No drying",
		1 to "Iron dry",
		2 to "Cupboard dry",
		3 to "Extra dry",
	)

	/** Each step of h.on's drying time is half an hour (1 = 30 min, 4 = 2 h). */
	const val MINUTES_PER_DRY_TIME_STEP = 30

	/** All the values an option allows, as whole numbers. */
	private fun allowedNumbers(option: ProgramOption): List<Int> = when (option) {
		is ProgramOption.Choice -> option.choices.mapNotNull { it.toDoubleOrNull()?.toInt() }
		is ProgramOption.Range -> option.allowedValues().map { it.toInt() }
		is ProgramOption.Toggle -> listOf(0, 1)
	}

	private fun defaultNumber(option: ProgramOption?): Int = option?.defaultValue?.toDoubleOrNull()?.toInt() ?: 0

	/**
	 * Replaces a program's dryLevel and dryTime options with one "Drying"
	 * choice. Programs without either are returned unchanged.
	 */
	fun combine(options: List<ProgramOption>): List<ProgramOption> {
		val levelOption = options.find { it.key == DRY_LEVEL_KEY }
		val timeOption = options.find { it.key == DRY_TIME_KEY }
		if (levelOption == null && timeOption == null) return options

		val levels = levelOption?.let(::allowedNumbers) ?: emptyList()
		val times = timeOption?.let(::allowedNumbers) ?: emptyList()
		val canSkipDrying = 0 in levels || 0 in times

		val choices = buildList {
			if (canSkipDrying) add(NO_DRYING)
			levels.filter { it > 0 }.forEach { add("$LEVEL_PREFIX$it") }
			times.filter { it > 0 }.forEach { add("$TIME_PREFIX$it") }
		}

		// The program's own default: a level if it has one, otherwise a time.
		val defaultLevel = defaultNumber(levelOption)
		val defaultTime = defaultNumber(timeOption)
		val preferredDefault = when {
			defaultLevel > 0 -> "$LEVEL_PREFIX$defaultLevel"
			defaultTime > 0 -> "$TIME_PREFIX$defaultTime"
			else -> choices.firstOrNull() ?: NO_DRYING
		}

		val drying = ProgramOption.Choice(
			key = OptionKeys.DRYING,
			label = "Drying",
			choices = choices,
			defaultValue = if (preferredDefault in choices) preferredDefault else choices.firstOrNull() ?: NO_DRYING,
			unit = null,
		)
		return options.filter { it.key != DRY_LEVEL_KEY && it.key != DRY_TIME_KEY } + drying
	}

	/** The dryLevel and dryTime values to send for a "Drying" choice. Only one is ever above 0. */
	fun expand(choice: String): Map<String, String> = when {
		choice.startsWith(LEVEL_PREFIX) -> mapOf(DRY_LEVEL_KEY to choice.removePrefix(LEVEL_PREFIX), DRY_TIME_KEY to "0")
		choice.startsWith(TIME_PREFIX) -> mapOf(DRY_LEVEL_KEY to "0", DRY_TIME_KEY to choice.removePrefix(TIME_PREFIX))
		else -> mapOf(DRY_LEVEL_KEY to "0", DRY_TIME_KEY to "0")
	}

	/** What a drying choice means: a named level, or a number of minutes. */
	sealed interface Meaning {
		data class Level(val label: String) : Meaning
		data class Minutes(val minutes: Int) : Meaning
	}

	/** "level:2" → Cupboard dry, "time:3" → 90 minutes, "none" → No drying. */
	fun describe(choice: String): Meaning = when {
		choice.startsWith(LEVEL_PREFIX) -> {
			val level = choice.removePrefix(LEVEL_PREFIX).toIntOrNull() ?: 0
			Meaning.Level(DRY_LEVEL_NAMES[level] ?: "Level $level")
		}
		choice.startsWith(TIME_PREFIX) ->
			Meaning.Minutes((choice.removePrefix(TIME_PREFIX).toIntOrNull() ?: 0) * MINUTES_PER_DRY_TIME_STEP)
		else -> Meaning.Level(DRY_LEVEL_NAMES.getValue(0))
	}
}
