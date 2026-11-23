package uk.rdfn.h.off.hon

import java.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import uk.rdfn.h.off.washer.ChosenOptions
import uk.rdfn.h.off.washer.DiagnosticBlock
import uk.rdfn.h.off.washer.LeftOutProgram
import uk.rdfn.h.off.washer.MachineInfo
import uk.rdfn.h.off.washer.PastCommand
import uk.rdfn.h.off.washer.TemperatureBand
import uk.rdfn.h.off.washer.UsageSummary
import uk.rdfn.h.off.washer.WasherCommandException
import uk.rdfn.h.off.washer.WasherProgram
import uk.rdfn.h.off.washer.WasherService
import uk.rdfn.h.off.washer.WasherStatus

/** Thrown when the signed-in account has no washing machine. */
class NoWasherFoundException : Exception("No washing machine was found on this h.on account.")

/**
 * The real washing machine, reached through Haier's h.on cloud.
 * Follows the same requests as the pyhOn library.
 *
 * Ported from src/hon/honCloudWasher.ts.
 */
class HonCloudWasher private constructor(
	private val session: HonSession,
	/** The appliance as h.on describes it (GET /commands/v1/appliance). */
	val appliance: JsonElement,
	private val httpSender: HttpSender,
) : WasherService {
	override val isDemo = false

	/** The command catalogue is large (around 1 MB) and rarely changes, so keep it. */
	private var cachedCatalogue: JsonObject? = null
	private val catalogueLock = Mutex()

	private val macAddress: String = appliance["macAddress"].asText() ?: ""
	private val applianceType: String = appliance["applianceTypeName"].asText() ?: ""

	companion object {
		/** Appliance types we treat as a washing machine (WD = washer-dryer). */
		private val WASHER_TYPE_NAMES = setOf("WM", "WD")

		/** Readable names for the temperature bands in h.on's statistics. */
		private val TEMPERATURE_BAND_LABELS = mapOf(
			"temp0to30" to "0–30 °C",
			"temp40" to "40 °C",
			"temp60to90" to "60–90 °C",
		)

		/** How many history entries the diagnostics show; enough to see the latest wash. */
		private const val HISTORY_ENTRIES_TO_SHOW = 5

		/** Longest JSON shown per block, so a huge answer can't swamp the screen. */
		private const val MAX_DIAGNOSTIC_CHARACTERS = 6000

		private val readableJson = Json { prettyPrint = true; prettyPrintIndent = " " }

		/** Finds the washing machine on the signed-in account. */
		suspend fun connect(session: HonSession, httpSender: HttpSender): HonCloudWasher {
			val response = sendHonApiRequest(session, httpSender, HonApiRequest("GET", "/commands/v1/appliance"))
			val washer = response["payload"]["appliances"].asArray()?.firstOrNull { appliance ->
				appliance["applianceTypeName"].asText() in WASHER_TYPE_NAMES
			} ?: throw NoWasherFoundException()
			return HonCloudWasher(session, washer, httpSender)
		}

		/** Indented JSON, cut short if it's very long. */
		private fun toReadableJson(value: JsonElement): String {
			val json = readableJson.encodeToString(JsonElement.serializer(), value)
			return if (json.length > MAX_DIAGNOSTIC_CHARACTERS) {
				json.take(MAX_DIAGNOSTIC_CHARACTERS) + "\n… (cut short)"
			} else {
				json
			}
		}
	}

	override suspend fun getMachineInfo(): MachineInfo {
		val modelName = appliance["modelName"].asText() ?: "Washing machine"
		val nickName = appliance["nickName"].asText() ?: ""
		// h.on fills in placeholder nicknames like "XXX" when none was set.
		val hasRealNickName = nickName.isNotEmpty() && !Regex("^[xX1\\s-]+$").matches(nickName)
		val brand = appliance["brand"].asText() ?: ""
		return MachineInfo(
			displayName = if (hasRealNickName) nickName else modelName,
			modelName = modelName,
			brand = brand.replaceFirstChar { it.uppercase() },
		)
	}

	override suspend fun getStatus(): WasherStatus = coroutineScope {
		val context = async { loadContext() }
		val catalogue = async { loadCatalogue() }
		WasherStatusParser.parse(context.await(), ProgramCatalogue.buildProgramNameByCode(catalogue.await()))
	}

	/**
	 * Status without the program name, which saves downloading the large
	 * program catalogue. Used by the background check.
	 */
	suspend fun getStatusWithoutProgramName(): WasherStatus = WasherStatusParser.parse(loadContext(), emptyMap())

	override suspend fun getPrograms(): List<WasherProgram> = ProgramCatalogue.parsePrograms(loadCatalogue())

	override suspend fun startProgram(programId: String, chosenOptions: ChosenOptions) {
		val program = loadCatalogue()["startProgram"][programId]
			?: throw WasherCommandException("Your machine doesn't offer the program \"$programId\".")
		sendCommand(CommandPayload.CommandName.START, program, programId, chosenOptions)
	}

	override suspend fun pause() = sendSimpleCommand(CommandPayload.CommandName.PAUSE)
	override suspend fun resume() = sendSimpleCommand(CommandPayload.CommandName.RESUME)
	override suspend fun stop() = sendSimpleCommand(CommandPayload.CommandName.STOP)

	override suspend fun getUsageSummary(): UsageSummary = coroutineScope {
		val context = async { loadContext() }
		val statistics = async {
			requestJson(
				"GET",
				"/commands/v1/statistics",
				mapOf("macAddress" to macAddress, "applianceType" to applianceType),
			)
		}
		val totals = WasherStatusParser.parseLifetimeTotals(context.await())
		val temperatureUsage = statistics.await()["payload"]["temperatureUsage"].asObject() ?: JsonObject(emptyMap())
		UsageSummary(
			totalEnergyKwh = totals.totalEnergyKwh,
			totalWaterLitres = totals.totalWaterLitres,
			totalCycles = totals.totalCycles,
			temperatureBands = temperatureUsage.map { (bandKey, count) ->
				TemperatureBand(TEMPERATURE_BAND_LABELS[bandKey] ?: bandKey, parseHonNumber(count).toInt())
			},
		)
	}

	override suspend fun getRecentCommands(): List<PastCommand> =
		loadHistory().map { entry ->
			val sentAtText = entry["timestampExecuted"].asText() ?: entry["timestampAccepted"].asText()
			val programId = entry["command"]["programName"].asText()
			PastCommand(
				commandName = entry["command"]["commandName"].asText() ?: "unknown",
				programName = programId?.let(ProgramCatalogue::programDisplayName),
				sentAt = sentAtText?.let { runCatching { Instant.parse(it) }.getOrNull() },
			)
		}

	/**
	 * The parts of h.on's answers the app doesn't read yet: the "activity"
	 * and connection events from the live status, and the newest entries of
	 * the machine's history, exactly as h.on sends them.
	 */
	override suspend fun getDiagnostics(): List<DiagnosticBlock> = coroutineScope {
		val context = async { loadContext() }
		val history = async { loadHistory() }
		val activity = buildJsonObject {
			put("activity", context.await()["activity"] ?: JsonNull)
			put("lastConnEvent", context.await()["lastConnEvent"] ?: JsonNull)
		}
		val allHistory = history.await()
		val recentHistory = JsonArray(allHistory.take(HISTORY_ENTRIES_TO_SHOW))
		listOf(
			DiagnosticBlock("Activity", toReadableJson(activity), activity),
			DiagnosticBlock(
				"History (first ${recentHistory.size} of ${allHistory.size})",
				toReadableJson(recentHistory),
				recentHistory,
			),
		)
	}

	override suspend fun getLeftOutPrograms(): List<LeftOutProgram> =
		ProgramCatalogue.listLeftOutPrograms(loadCatalogue())

	/** The program catalogue, for "Send a report". */
	suspend fun getCatalogue(): JsonObject = loadCatalogue()

	private suspend fun loadHistory(): List<JsonElement> =
		requestJson("GET", "/commands/v1/appliance/$macAddress/history")["payload"]["history"].asArray() ?: emptyList()

	private suspend fun sendSimpleCommand(commandName: CommandPayload.CommandName) {
		val command = loadCatalogue()[commandName.honName]
			?: throw WasherCommandException("Your machine doesn't support \"${commandName.honName}\".")
		sendCommand(commandName, command)
	}

	private suspend fun sendCommand(
		commandName: CommandPayload.CommandName,
		command: JsonElement,
		programId: String? = null,
		chosenOptions: ChosenOptions = emptyMap(),
	) {
		val catalogue = loadCatalogue()
		val body = CommandPayload.build(
			appliance = appliance,
			applianceOptions = catalogue["applianceModel"]["options"],
			commandName = commandName,
			command = command,
			mobileId = session.mobileId,
			programId = programId,
			chosenOptions = chosenOptions,
		)
		val response = requestJson("POST", "/commands/v1/send", body = body)
		if (response["payload"]["resultCode"].asText() != "0") {
			throw WasherCommandException(
				"The machine did not accept the command. Check that remote control is switched on at the machine.",
			)
		}
	}

	private suspend fun loadContext(): JsonElement =
		requestJson(
			"GET",
			"/commands/v1/context",
			mapOf("macAddress" to macAddress, "applianceType" to applianceType, "category" to "CYCLE"),
		)["payload"] ?: JsonObject(emptyMap())

	private suspend fun loadCatalogue(): JsonObject = catalogueLock.withLock {
		cachedCatalogue?.let { return@withLock it }

		val query = linkedMapOf(
			"applianceType" to applianceType,
			"applianceModelId" to (appliance["applianceModelId"].asText() ?: ""),
			"macAddress" to macAddress,
			"os" to HonConstants.OS_NAME,
			"appVersion" to HonConstants.APP_VERSION,
			"code" to applianceCode(),
		)
		appliance["eepromId"].asText()?.let { query["firmwareId"] = it }
		appliance["fwVersion"].asText()?.takeIf { it.isNotEmpty() }?.let { query["fwVersion"] = it }
		appliance["series"].asText()?.takeIf { it.isNotEmpty() }?.let { query["series"] = it }

		val payload = requestJson("GET", "/commands/v1/retrieve", query)["payload"].asObject()
		if (payload?.get("resultCode").asText() != "0") {
			throw WasherCommandException("Couldn't load your machine's programs from h.on.")
		}
		JsonObject(payload!!.filterKeys { it != "resultCode" }).also { cachedCatalogue = it }
	}

	/** h.on's model code; pyhOn derives it from the serial number when missing. */
	private fun applianceCode(): String {
		appliance["code"].asText()?.takeIf { it.isNotEmpty() }?.let { return it }
		val serialNumber = appliance["serialNumber"].asText() ?: ""
		return if (serialNumber.length < 18) serialNumber.take(8) else serialNumber.take(11)
	}

	/** Sends a request for this washer's session. */
	private suspend fun requestJson(
		method: String,
		path: String,
		query: Map<String, String> = emptyMap(),
		body: JsonElement? = null,
	): JsonElement = sendHonApiRequest(session, httpSender, HonApiRequest(method, path, query, body))
}
