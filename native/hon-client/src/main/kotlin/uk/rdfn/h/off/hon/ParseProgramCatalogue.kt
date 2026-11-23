package uk.rdfn.h.off.hon

import java.text.Collator
import java.util.Locale
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import uk.rdfn.h.off.washer.FabricHint
import uk.rdfn.h.off.washer.LeftOutProgram
import uk.rdfn.h.off.washer.OptionKeys
import uk.rdfn.h.off.washer.ProgramOption
import uk.rdfn.h.off.washer.WasherProgram

/**
 * Turns h.on's program catalogue (GET /commands/v1/retrieve) into the
 * app's program list.
 *
 * Ported from src/hon/parseProgramCatalogue.ts.
 */
object ProgramCatalogue {
	private data class KnownOption(val label: String, val unit: String? = null)

	/** Friendly names and units for the options we know about. */
	private val KNOWN_OPTIONS = mapOf(
		"temp" to KnownOption("Temperature", "°C"),
		"spinSpeed" to KnownOption("Spin speed", "rpm"),
		"delayTime" to KnownOption("Delay start", "min"),
		"dirtyLevel" to KnownOption("Soil level"),
		"prewash" to KnownOption("Prewash"),
		"anticrease" to KnownOption("Anti-crease"),
		"nightWashStatus" to KnownOption("Night wash"),
		"goodNight" to KnownOption("Good night (quiet)"),
		"silentMode" to KnownOption("Silent mode"),
		"steamWashStatus" to KnownOption("Steam"),
		"hygiene" to KnownOption("Hygiene"),
		"acquaplus" to KnownOption("Aqua plus"),
		"creaseResistSoakStatus" to KnownOption("Crease-resist soak"),
		"permanentPressStatus" to KnownOption("Keep fresh"),
		"autoDetergentStatus" to KnownOption("Auto-dose detergent"),
		"autoSoftenerStatus" to KnownOption("Auto-dose softener"),
		"antiAllergyStatus" to KnownOption("Anti-allergy"),
		"highWaterLevelStatus" to KnownOption("Extra water"),
		"weight" to KnownOption("Load", "kg"),
	)

	/**
	 * Parameters h.on sends with every program that aren't wash options, so
	 * the app never shows them. "lang" is the language of the machine's
	 * display; "delayStatus" is switched on for you when you choose a delay start.
	 */
	private val NOT_WASH_OPTIONS = setOf(
		"lang", "delayStatus", "prCode", "prStr", "prPosition", "onOffStatus", "remoteActionable",
	)

	/** h.on's separate extra rinse switches, in order. */
	val EXTRA_RINSE_SWITCH_KEYS = listOf("extraRinse1", "extraRinse2", "extraRinse3")

	/** Program variants pyhOn hides because they can't be started normally. */
	private val HIDDEN_PROGRAM_MARKERS = listOf("iot_recipe", "iot_guided")

	/** Sorts names the way a person would ("Bed linen" before "men's trousers"). */
	private val nameCollator: Collator = Collator.getInstance(Locale.UK)

	/** "extraRinse1" → "Extra rinse 1", for options we have no name for. */
	private fun labelFromKey(key: String): String {
		val words = key
			.removeSuffix("Status")
			.replace(Regex("([a-z])([A-Z0-9])"), "$1 $2")
			.lowercase()
		return words.replaceFirstChar { it.uppercase() }
	}

	/**
	 * "PROGRAMS.WM_WD.ECO_40_60_NEW_ENERGY_LABEL" → "Eco 40-60".
	 * "PROGRAMS.WM_WD.IOT_WASH_MEN_S_TROUSERS" → "Men's trousers".
	 */
	fun programDisplayName(programId: String): String {
		val lastSegment = programId.substringAfterLast('.')
		val words = lastSegment
			.replace(Regex("^IOT_(WASH_)?"), "")
			.replace(Regex("_NEW_ENERGY_LABEL$"), "")
			.replace(Regex("(\\d+)_(\\d+)"), "$1-$2")
			.replace("_S_", "'S_")
			.replace('_', ' ')
			.lowercase()
		return words.replaceFirstChar { it.uppercase() }
	}

	/** True for a parameter the user can actually change. */
	private fun isAdjustable(definition: JsonElement): Boolean = when (typologyOf(definition)) {
		"enum" -> enumValuesOf(definition).size > 1
		"range" -> parseHonNumber(definition["maximumValue"]) > parseHonNumber(definition["minimumValue"])
		else -> false
	}

	/** Turns one h.on parameter into an option the UI can show. */
	private fun toProgramOption(key: String, definition: JsonElement): ProgramOption {
		val known = KNOWN_OPTIONS[key]
		val label = known?.label ?: labelFromKey(key)
		val unit = known?.unit
		val defaultValue = defaultValueOf(definition)

		if (typologyOf(definition) == "enum") {
			val choices = enumValuesOf(definition).map(::stripBrackets)
			val isOnOff = choices.size == 2 && "0" in choices && "1" in choices
			if (isOnOff) return ProgramOption.Toggle(key, label, isOnByDefault = defaultValue == "1")
			return ProgramOption.Choice(key, label, choices, defaultValue, unit)
		}

		val minimum = parseHonNumber(definition["minimumValue"])
		val maximum = parseHonNumber(definition["maximumValue"])
		val step = parseHonNumber(definition["incrementValue"]).takeIf { it != 0.0 } ?: 1.0
		if (minimum == 0.0 && maximum == 1.0) {
			return ProgramOption.Toggle(key, label, isOnByDefault = defaultValue == "1")
		}
		return ProgramOption.Range(key, label, minimum, maximum, step, parseHonNumber(defaultValue), unit)
	}

	/** Temperature, spin and extra rinses first, then delay start, then the on/off extras. */
	private fun optionSortOrder(option: ProgramOption): Int = when {
		option is ProgramOption.Toggle -> 2
		option.key == OptionKeys.DELAY -> 1
		else -> 0
	}

	/**
	 * Replaces the extraRinse1/2/3 switches with one "Extra rinses" choice:
	 * None, 1, 2 or 3 (as many as the program has switches for).
	 */
	private fun combineExtraRinses(options: List<ProgramOption>): List<ProgramOption> {
		val rinseSwitches = options.filter { it.key in EXTRA_RINSE_SWITCH_KEYS }
		if (rinseSwitches.isEmpty()) return options

		val switchedOnByDefault = rinseSwitches.count { it.defaultValue == ProgramOption.TOGGLE_ON }
		val extraRinses = ProgramOption.Choice(
			key = OptionKeys.EXTRA_RINSES,
			label = "Extra rinses",
			choices = (0..rinseSwitches.size).map { it.toString() },
			defaultValue = switchedOnByDefault.toString(),
			unit = null,
		)
		return options.filter { it.key !in EXTRA_RINSE_SWITCH_KEYS } + extraRinses
	}

	private fun toWasherProgram(programId: String, definition: JsonElement): WasherProgram {
		val separateOptions = definition["parameters"].asObject().orEmpty()
			.filter { (key, parameter) -> key !in NOT_WASH_OPTIONS && isAdjustable(parameter) }
			.map { (key, parameter) -> toProgramOption(key, parameter) }
		// sortedBy is stable, so options keep h.on's order within each group.
		val options = DryingOption.combine(combineExtraRinses(separateOptions)).sortedBy(::optionSortOrder)

		val estimatedTime = definition["remainingTimes"]["remainingTime"]
		val estimatedMinutes = estimatedTime?.let { parseHonNumber(defaultValueOf(it)).toInt() }?.takeIf { it != 0 }

		val lastSegment = programId.substringAfterLast('.')
		return WasherProgram(
			id = programId,
			displayName = programDisplayName(programId),
			description = definition["description"].asText() ?: "",
			estimatedMinutes = estimatedMinutes,
			isBuiltIn = !lastSegment.uppercase().startsWith("IOT_"),
			fabricHint = fabricHintOf(definition),
			options = options,
		)
	}

	/** h.on's "dryType" ("C" = cotton, "S" = synthetic) as a fabric hint, or null when it gives none. */
	private fun fabricHintOf(definition: JsonElement): FabricHint? {
		val dryType = definition["ancillaryParameters"]["dryType"] ?: return null
		return when (stripBrackets(defaultValueOf(dryType)).trim().uppercase()) {
			"C" -> FabricHint.COTTON
			"S" -> FabricHint.SYNTHETICS
			else -> null
		}
	}

	/**
	 * Why a program isn't offered in the app, or null when it is.
	 *
	 * h.on's remoteVisible flag is deliberately ignored. On a Hoover
	 * H3DPS4866TAMB-80 it is "0" for 46 everyday programs (Cotton, Coloured,
	 * Whites, Rapid 59...), yet the h.on app starts them anyway: a "Colored"
	 * wash (IOT_WASH_COLOURED) was running from h.on while flagged "0".
	 */
	fun whyProgramIsLeftOut(programId: String): String? {
		val lowerCaseId = programId.lowercase()
		if (HIDDEN_PROGRAM_MARKERS.any { it in lowerCaseId }) {
			return "h.on recipe or guided variant, which cannot be started directly"
		}
		return null
	}

	private fun startPrograms(catalogue: JsonElement): JsonObject = catalogue["startProgram"].asObject().orEmpty()

	/** Programs the machine has but the app leaves out, with the reason for each. For "Send a report". */
	fun listLeftOutPrograms(catalogue: JsonElement): List<LeftOutProgram> =
		startPrograms(catalogue).keys.mapNotNull { programId ->
			whyProgramIsLeftOut(programId)?.let { LeftOutProgram(programId, it) }
		}

	/**
	 * Lists the programs a machine supports. Built-in programs come first in
	 * the machine's own order, then the app-only extras alphabetically.
	 */
	fun parsePrograms(catalogue: JsonElement): List<WasherProgram> {
		val programs = startPrograms(catalogue)
			.filter { (programId, _) -> whyProgramIsLeftOut(programId) == null }
			.map { (programId, definition) -> toWasherProgram(programId, definition) }
		val builtIn = programs.filter { it.isBuiltIn }
		val extras = programs.filter { !it.isBuiltIn }.sortedWith { first, second ->
			nameCollator.compare(first.displayName, second.displayName)
		}
		return builtIn + extras
	}

	/**
	 * The machine reports the running program as a number ("prCode"). This
	 * maps those numbers back to readable program names.
	 */
	fun buildProgramNameByCode(catalogue: JsonElement): Map<Int, String> {
		val programNameByCode = linkedMapOf<Int, String>()
		for ((programId, definition) in startPrograms(catalogue)) {
			val programCode = definition["parameters"]["prCode"] ?: continue
			val code = parseHonNumber(defaultValueOf(programCode)).toInt()
			// Several entries can share a code; keep the first (the built-in one).
			if (code > 0 && code !in programNameByCode) programNameByCode[code] = programDisplayName(programId)
		}
		return programNameByCode
	}
}

private fun JsonObject?.orEmpty(): JsonObject = this ?: JsonObject(emptyMap())
