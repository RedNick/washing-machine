package uk.rdfn.h.off.diagnostics

import java.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import uk.rdfn.h.off.hon.honJson
import uk.rdfn.h.off.history.FinishedCycle
import uk.rdfn.h.off.washer.MachineInfo
import uk.rdfn.h.off.washer.ProgramOption
import uk.rdfn.h.off.washer.WasherProgram
import uk.rdfn.h.off.washer.WasherService
import uk.rdfn.h.off.washer.WasherStatus

/** Which app and phone a report came from. */
data class AppDetails(
	/** e.g. "h.off 2.0.0 · build 75". */
	val version: String,
	val platform: String = "android",
	val osVersion: String,
)

/**
 * The two kinds of report h.off can make, in the same JSON format as the
 * Expo app (reportFormat 1), so the wash-report worker and anyone reading
 * old reports see the same shape.
 *
 * Ported from src/diagnostics/buildDiagnosticReport.ts and washReporting.ts.
 */
object Reports {
	private const val FINISHED_WASHES_TO_INCLUDE = 10

	/** A status as JSON, with the same field names (and lower-case states) as the Expo app used. */
	fun statusToJson(status: WasherStatus): JsonObject = buildJsonObject {
		put("state", status.state.name.lowercase())
		put("phase", status.phase.name.lowercase())
		put("programName", status.programName)
		put("remainingMinutes", status.remainingMinutes)
		put("delayMinutes", status.delayMinutes)
		put("temperatureCelsius", status.temperatureCelsius)
		put("spinSpeedRpm", status.spinSpeedRpm)
		put("currentCycleEnergyKwh", status.currentCycleEnergyKwh)
		put("currentCycleWaterLitres", status.currentCycleWaterLitres)
		put("doorLocked", status.doorLocked)
		put("remoteControlEnabled", status.remoteControlEnabled)
		put("errorCode", status.errorCode)
		put("fetchedAt", status.fetchedAt.toString())
		put(
			"rawReadings",
			buildJsonArray {
				status.rawReadings.forEach { reading ->
					add(buildJsonObject {
						put("name", reading.name)
						put("value", reading.value)
						put("lastUpdated", reading.lastUpdated?.toString())
					})
				}
			},
		)
	}

	private fun appToJson(app: AppDetails) = buildJsonObject {
		put("version", app.version)
		put("platform", app.platform)
		put("osVersion", app.osVersion)
	}

	/** Model and brand only, never the name you gave the machine. */
	private fun machineToJson(machineInfo: MachineInfo) = buildJsonObject {
		put("modelName", machineInfo.modelName)
		put("brand", machineInfo.brand)
	}

	/**
	 * Program names, lengths and the options each one offers. Enough to see
	 * how a model's catalogue was understood, without the whole h.on download.
	 */
	private fun summarisePrograms(programs: List<WasherProgram>): JsonArray = buildJsonArray {
		programs.forEach { program ->
			add(buildJsonObject {
				put("id", program.id)
				put("name", program.displayName)
				put("estimatedMinutes", program.estimatedMinutes)
				put("isBuiltIn", program.isBuiltIn)
				put("options", buildJsonArray {
					program.options.forEach { option ->
						add(buildJsonObject {
							put("key", option.key)
							when (option) {
								is ProgramOption.Choice -> {
									put("kind", "choice")
									put("choices", JsonArray(option.choices.map(::JsonPrimitive)))
									put("defaultValue", option.defaultValue)
								}
								is ProgramOption.Range -> {
									put("kind", "range")
									put("minimum", option.minimum)
									put("maximum", option.maximum)
									put("step", option.step)
								}
								is ProgramOption.Toggle -> {
									put("kind", "toggle")
									put("defaultValue", option.isOnByDefault)
								}
							}
						})
					}
				})
			})
		}
	}

	/** Runs one part of the report, turning a failure into a readable note. */
	private suspend fun describeFailures(loadPart: suspend () -> JsonElement): JsonElement =
		try {
			loadPart()
		} catch (problem: Exception) {
			buildJsonObject { put("failed", problem.message ?: problem.toString()) }
		}

	/**
	 * Everything in a "Send a report" file. Each part is fetched separately,
	 * so if h.on is failing the report still goes out with the error in place
	 * of that part (that's often when a report is most useful). Private
	 * details are removed before it is returned.
	 */
	suspend fun buildDiagnosticReport(
		washer: WasherService,
		machineInfo: MachineInfo,
		app: AppDetails,
		finishedCycles: List<FinishedCycle>,
		now: Instant = Instant.now(),
	): JsonElement = coroutineScope {
		val status = async { describeFailures { statusToJson(washer.getStatus()) } }
		val rawDiagnostics = async {
			describeFailures {
				if (washer.isDemo) {
					JsonPrimitive("Not available for this machine.")
				} else {
					JsonArray(washer.getDiagnostics().map { block ->
						buildJsonObject {
							put("title", block.title)
							put("data", block.data)
						}
					})
				}
			}
		}
		val programs = async { describeFailures { summarisePrograms(washer.getPrograms()) } }
		val leftOutPrograms = async {
			describeFailures {
				JsonArray(washer.getLeftOutPrograms().map { leftOut ->
					buildJsonObject {
						put("id", leftOut.id)
						put("reason", leftOut.reason)
					}
				})
			}
		}

		val report = buildJsonObject {
			put("reportFormat", 1)
			put("createdAt", now.toString())
			put("app", appToJson(app))
			put("machine", machineToJson(machineInfo))
			put("status", status.await())
			put("rawDiagnostics", rawDiagnostics.await())
			put("programs", programs.await())
			put("leftOutPrograms", leftOutPrograms.await())
			put("recentFinishedWashes", honJson.encodeToJsonElement(finishedCycles.take(FINISHED_WASHES_TO_INCLUDE)))
		}
		PrivateDetails.redact(report)
	}

	/**
	 * The short summary sent at the end of a wash when "Wash reports" is on.
	 * Checked by wash-report-worker/src/checkWashReport.ts.
	 */
	fun buildWashReport(
		finishedCycle: FinishedCycle,
		finalStatus: WasherStatus,
		machineInfo: MachineInfo,
		app: AppDetails,
	): JsonElement = PrivateDetails.redact(
		buildJsonObject {
			put("reportFormat", 1)
			put("app", appToJson(app))
			put("machine", machineToJson(machineInfo))
			put(
				"wash",
				buildJsonObject {
					put("programName", finishedCycle.programName)
					put("finishedAt", finishedCycle.finishedAt)
					put("energyKwh", finishedCycle.energyKwh)
					put("waterLitres", finishedCycle.waterLitres)
				},
			)
			// The status that showed the wash had finished, raw readings included.
			put("finalStatus", statusToJson(finalStatus))
		},
	)
}
