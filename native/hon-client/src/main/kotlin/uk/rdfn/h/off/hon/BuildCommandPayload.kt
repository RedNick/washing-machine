package uk.rdfn.h.off.hon

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import uk.rdfn.h.off.washer.ChosenOptions
import uk.rdfn.h.off.washer.OptionKeys
import uk.rdfn.h.off.washer.ProgramOption

/**
 * Builds the JSON for POST /commands/v1/send, matching what pyhOn (and the
 * official app) send.
 *
 * Ported from src/hon/buildCommandPayload.ts.
 */
object CommandPayload {
	/**
	 * Turns the app's combined options back into h.on's own parameters:
	 * "Extra rinses: 2" switches on extraRinse1 and extraRinse2, a delay start
	 * also switches on delayStatus (ignored by machines without it), and
	 * "Drying: Cupboard dry" becomes dryLevel 2 with dryTime 0.
	 */
	fun expandCombinedOptions(chosenOptions: ChosenOptions): ChosenOptions {
		val expanded = linkedMapOf<String, String>()
		chosenOptions.filterKeys { it != OptionKeys.EXTRA_RINSES && it != OptionKeys.DRYING }.forEach { (key, value) ->
			expanded[key] = value
		}
		chosenOptions[OptionKeys.DRYING]?.let { expanded.putAll(DryingOption.expand(it)) }
		chosenOptions[OptionKeys.EXTRA_RINSES]?.let { extraRinseCountText ->
			val extraRinseCount = extraRinseCountText.toDoubleOrNull()?.toInt() ?: 0
			ProgramCatalogue.EXTRA_RINSE_SWITCH_KEYS.forEachIndexed { index, switchKey ->
				expanded[switchKey] = if (index < extraRinseCount) ProgramOption.TOGGLE_ON else ProgramOption.TOGGLE_OFF
			}
		}
		val delayMinutes = chosenOptions[OptionKeys.DELAY]?.toDoubleOrNull() ?: 0.0
		if (delayMinutes > 0) expanded["delayStatus"] = ProgramOption.TOGGLE_ON
		return expanded
	}

	/** Fills in every parameter, using the user's choice where there is one. */
	private fun resolveParameterValues(definitions: JsonObject?, chosenOptions: ChosenOptions): LinkedHashMap<String, String> {
		val resolvedValues = linkedMapOf<String, String>()
		definitions?.forEach { (key, definition) ->
			resolvedValues[key] = chosenOptions[key] ?: defaultValueOf(definition)
		}
		return resolvedValues
	}

	/** The commands the app sends. */
	enum class CommandName(val honName: String) {
		START("startProgram"),
		STOP("stopProgram"),
		PAUSE("pauseProgram"),
		RESUME("resumeProgram"),
	}

	/** ISO time with milliseconds, like JavaScript's toISOString(): 2026-10-25T09:40:12.123Z. */
	private val TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
		.withZone(java.time.ZoneOffset.UTC)

	/**
	 * Every parameter of the command is included; the user's choices replace
	 * the defaults.
	 *
	 * @param appliance the appliance from GET /commands/v1/appliance.
	 * @param applianceOptions the "options" map from the catalogue's applianceModel.
	 * @param programId only for [CommandName.START]: e.g. "PROGRAMS.WM_WD.COTTONS".
	 * @param mobileId stable random id for this phone, so h.on can tell our requests apart.
	 */
	fun build(
		appliance: JsonElement,
		applianceOptions: JsonElement?,
		commandName: CommandName,
		command: JsonElement,
		mobileId: String,
		programId: String? = null,
		chosenOptions: ChosenOptions = emptyMap(),
		now: Instant = Instant.now(),
	): JsonObject {
		val timestamp = TIMESTAMP_FORMAT.format(now.truncatedTo(ChronoUnit.MILLIS))
		val expandedOptions = expandCombinedOptions(chosenOptions)
		val macAddress = appliance["macAddress"].asText() ?: ""

		val parameters = resolveParameterValues(command["parameters"].asObject(), expandedOptions)
		// "programRules" describes how options affect each other; it isn't sent.
		val ancillaryDefinitions = command["ancillaryParameters"].asObject()?.let { all ->
			JsonObject(all.filterKeys { it != "programRules" })
		}
		val ancillaryParameters = resolveParameterValues(ancillaryDefinitions, emptyMap())

		val programName = programId?.uppercase()?.takeIf { commandName == CommandName.START }
		// Some machines also want the program name inside the parameters.
		if (programName != null && "prStr" in parameters) parameters["prStr"] = programName

		return buildJsonObject {
			put("macAddress", macAddress)
			put("timestamp", timestamp)
			put("commandName", commandName.honName)
			put("transactionId", "${macAddress}_$timestamp")
			put("applianceOptions", applianceOptions.asObject() ?: JsonObject(emptyMap()))
			put(
				"device",
				buildJsonObject {
					put("appVersion", HonConstants.APP_VERSION)
					put("mobileId", mobileId)
					put("mobileOs", HonConstants.OS_NAME)
					put("osVersion", HonConstants.OS_VERSION)
					put("deviceModel", HonConstants.DEVICE_MODEL)
				},
			)
			put(
				"attributes",
				buildJsonObject {
					put("channel", "mobileApp")
					put("origin", "standardProgram")
					put("energyLabel", "0")
				},
			)
			put("ancillaryParameters", ancillaryParameters.toJsonObject())
			put("parameters", parameters.toJsonObject())
			put("applianceType", appliance["applianceTypeName"].asText() ?: "")
			if (programName != null) put("programName", programName)
		}
	}

	private fun Map<String, String>.toJsonObject(): JsonObject = JsonObject(mapValues { JsonPrimitive(it.value) })
}
