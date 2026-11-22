package uk.rdfn.h.off.hon

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Small, made-up h.on responses shaped like real ones (based on the public
 * hon-test-data captures). Real catalogues have 100+ programs. The same data
 * as the Expo app's src/hon/__tests__/honFixtures.ts.
 */
object HonFixtures {
	private fun readFixture(name: String): JsonElement {
		val text = HonFixtures::class.java.getResource("/fixtures/$name")!!.readText()
		return honJson.parseToJsonElement(text)
	}

	val washerAppliance: JsonElement = readFixture("washer-appliance.json")
	val sampleCatalogue: JsonObject = readFixture("sample-catalogue.json") as JsonObject

	/**
	 * Builds a context response from simple name → value pairs, with an
	 * optional last-changed time for some of them.
	 */
	fun contextWith(
		values: Map<String, String>,
		connection: String = "CONNECTED",
		lastUpdates: Map<String, String> = emptyMap(),
		activity: JsonObject = JsonObject(emptyMap()),
	): JsonObject = buildJsonObject {
		putJsonObject("shadow") {
			putJsonObject("parameters") {
				for ((name, value) in values) {
					putJsonObject(name) {
						put("parNewVal", value)
						lastUpdates[name]?.let { put("lastUpdate", it) }
					}
				}
			}
		}
		put("activity", activity)
		putJsonObject("lastConnEvent") { put("category", connection) }
	}

	val runningContext = contextWith(
		mapOf(
			"machMode" to "2",
			"prPhase" to "4",
			"prCode" to "201",
			"remainingTimeMM" to "83",
			"temp" to "40",
			"spinSpeed" to "0",
			"currentElectricityUsed" to "0.42",
			"currentWaterUsed" to "31",
			"doorLockStatus" to "1",
			"remoteCtrValid" to "1",
			"errors" to "00",
			"totalElectricityUsed" to "33.34",
			"totalWaterUsed" to "2571",
			"totalWashCycle" to "45",
		),
	)
}
