package uk.rdfn.h.off.hon

import java.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uk.rdfn.h.off.hon.HonFixtures.sampleCatalogue
import uk.rdfn.h.off.hon.HonFixtures.washerAppliance

/** Ported from src/hon/__tests__/buildCommandPayload.test.ts. */
class CommandPayloadTest {
	private val fixedTime = Instant.parse("2026-10-25T09:40:12.123Z")
	private val cottons = sampleCatalogue["startProgram"]["PROGRAMS.WM_WD.COTTONS"]!!

	private fun JsonObject.textValues(key: String): Map<String, String> =
		(this[key] as JsonObject).mapValues { (it.value as JsonPrimitive).content }

	@Test
	fun buildsAStartCommandWithTheUsersChoicesAndEveryDefault() {
		val body = CommandPayload.build(
			appliance = washerAppliance,
			applianceOptions = sampleCatalogue["applianceModel"]["options"],
			commandName = CommandPayload.CommandName.START,
			command = cottons,
			mobileId = "test-phone",
			programId = "PROGRAMS.WM_WD.COTTONS",
			chosenOptions = mapOf("temp" to "60", "prewash" to "1", "delayTime" to "90", "extraRinses" to "2"),
			now = fixedTime,
		)

		assertEquals("startProgram", body["commandName"].asText())
		assertEquals("PROGRAMS.WM_WD.COTTONS", body["programName"].asText())
		assertEquals("2026-10-25T09:40:12.123Z", body["timestamp"].asText())
		assertEquals("aa-bb-cc-dd-ee-ff_2026-10-25T09:40:12.123Z", body["transactionId"].asText())
		assertEquals("WM", body["applianceType"].asText())
		assertEquals(
			buildJsonObject {
				put("appVersion", "2.6.5")
				put("mobileId", "test-phone")
				put("mobileOs", "android")
				put("osVersion", 999)
				put("deviceModel", "WasherApp")
			},
			body["device"],
		)
		assertEquals(
			mapOf(
				"prCode" to "201",
				"temp" to "60",
				"spinSpeed" to "1400",
				"delayTime" to "90",
				"prewash" to "1",
				"weight" to "40",
				"prStr" to "PROGRAMS.WM_WD.COTTONS",
				"lang" to "0",
				"delayStatus" to "1",
				"extraRinse1" to "1",
				"extraRinse2" to "1",
			),
			body.textValues("parameters"),
		)
	}

	@Test
	fun sendsAncillaryValuesButLeavesOutTheProgramRules() {
		val body = CommandPayload.build(
			appliance = washerAppliance,
			applianceOptions = null,
			commandName = CommandPayload.CommandName.START,
			command = cottons,
			mobileId = "test-phone",
			programId = "PROGRAMS.WM_WD.COTTONS",
			now = fixedTime,
		)
		assertEquals(
			mapOf("remoteVisible" to "1", "suggestedLoadW" to "12", "suggestedLoadWD" to "0", "programFamily" to "dashboard"),
			body.textValues("ancillaryParameters"),
		)
	}

	@Test
	fun buildsAStopCommandWithNoProgramName() {
		val body = CommandPayload.build(
			appliance = washerAppliance,
			applianceOptions = null,
			commandName = CommandPayload.CommandName.STOP,
			command = sampleCatalogue["stopProgram"]!!,
			mobileId = "test-phone",
			now = fixedTime,
		)
		assertEquals(mapOf("onOffStatus" to "0"), body.textValues("parameters"))
		assertNull(body["programName"])
	}

	@Test
	fun turnsANumberOfExtraRinsesIntoTheSeparateSwitches() {
		assertEquals(
			mapOf("extraRinse1" to "1", "extraRinse2" to "0", "extraRinse3" to "0"),
			CommandPayload.expandCombinedOptions(mapOf("extraRinses" to "1")),
		)
		assertEquals(
			mapOf("extraRinse1" to "0", "extraRinse2" to "0", "extraRinse3" to "0"),
			CommandPayload.expandCombinedOptions(mapOf("extraRinses" to "0")),
		)
	}

	@Test
	fun switchesOnDelayStatusOnlyWhenThereIsADelay() {
		assertEquals(
			mapOf("delayTime" to "120", "delayStatus" to "1"),
			CommandPayload.expandCombinedOptions(mapOf("delayTime" to "120")),
		)
		assertEquals(mapOf("delayTime" to "0"), CommandPayload.expandCombinedOptions(mapOf("delayTime" to "0")))
	}
}
