package uk.rdfn.h.off.diagnostics

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Test
import uk.rdfn.h.off.diagnostics.PrivateDetails.REMOVED

/** Ported from src/diagnostics/__tests__/redactPrivateDetails.test.ts. */
class PrivateDetailsTest {
	@Test
	fun removesTokensMacAddressesSerialNumbersAndAccountDetailsByFieldName() {
		val redacted = PrivateDetails.redact(buildJsonObject {
			put("macAddress", "ab-cd-ef-01-23-45")
			put("serialNumber", "123456")
			put("idToken", "secret")
			put("nickName", "Washer Dryer")
			put("mac", "x")
			putJsonObject("machine") { put("modelName", "HWD100") }
			putJsonObject("parameters") { putJsonObject("machMode") { put("parNewVal", "2") } }
		})
		assertEquals(
			buildJsonObject {
				put("macAddress", REMOVED)
				put("serialNumber", REMOVED)
				put("idToken", REMOVED)
				put("nickName", REMOVED)
				put("mac", REMOVED)
				putJsonObject("machine") { put("modelName", "HWD100") }
				putJsonObject("parameters") { putJsonObject("machMode") { put("parNewVal", "2") } }
			},
			redacted,
		)
	}

	@Test
	fun removesMacAndEmailAddressesHiddenInsideOtherText() {
		val redacted = PrivateDetails.redact(buildJsonObject {
			put("transactionId", "ab-cd-ef-01-23-45_2026-11-03T21:36:00Z")
			put("note", "from me@example.com")
		})
		assertEquals(
			buildJsonObject {
				put("transactionId", "${REMOVED}_2026-11-03T21:36:00Z")
				put("note", "from $REMOVED")
			},
			redacted,
		)
	}

	@Test
	fun keepsNumbersAndBooleansAndWorksThroughLists() {
		val list = buildJsonArray {
			add(JsonPrimitive(1))
			add(JsonPrimitive(true))
			add(JsonNull)
		}
		assertEquals(list, PrivateDetails.redact(list))
	}
}
