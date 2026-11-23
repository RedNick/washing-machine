package uk.rdfn.h.off.diagnostics

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Removes anything that could identify you or your machine from data
 * before it leaves the phone in a report: sign-in tokens, the machine's MAC
 * address and serial number, and account details.
 *
 * It works on field names (so new h.on fields with familiar names are
 * caught too) and on values that look like MAC addresses or email addresses.
 *
 * Ported from src/diagnostics/redactPrivateDetails.ts.
 */
object PrivateDetails {
	const val REMOVED = "[removed]"

	/**
	 * Field names, in lower case, whose values are always removed. A field
	 * matches if its name contains any of these, so "macAddress" and
	 * "applianceMacAddress" are both caught.
	 */
	private val PRIVATE_FIELD_NAME_PARTS = listOf(
		"token", "password", "secret", "cookie", "authorization", "macaddress", "serial", "email", "phone",
		"firstname", "lastname", "nickname", "address", "mobileid", "userid", "personid", "coords", "latitude",
		"longitude", "ssid",
	)

	/**
	 * Field names removed only on an exact match, because as parts of a name
	 * they'd catch useful fields too ("mac" is inside "machine" and "machMode").
	 */
	private val PRIVATE_FIELD_NAMES = setOf("mac", "ip")

	/**
	 * 12 hex digits, with or without - or : between the pairs. Uses
	 * look-arounds rather than \b so a MAC followed by "_" (as in h.on
	 * transaction ids) still matches.
	 */
	private val MAC_ADDRESS_PATTERN =
		Regex("(?<![0-9a-f])[0-9a-f]{2}([-:]?)[0-9a-f]{2}(\\1[0-9a-f]{2}){4}(?![0-9a-f])", RegexOption.IGNORE_CASE)
	private val EMAIL_PATTERN = Regex("[^\\s@\"]+@[^\\s@\"]+\\.[^\\s@\"]+")

	private fun isPrivateFieldName(fieldName: String): Boolean {
		val lowerCaseName = fieldName.lowercase()
		return lowerCaseName in PRIVATE_FIELD_NAMES || PRIVATE_FIELD_NAME_PARTS.any { it in lowerCaseName }
	}

	fun redactText(text: String): String = text.replace(MAC_ADDRESS_PATTERN, REMOVED).replace(EMAIL_PATTERN, REMOVED)

	/** A copy of the value with private details replaced by "[removed]". */
	fun redact(value: JsonElement): JsonElement = when (value) {
		is JsonObject -> JsonObject(
			value.mapValues { (fieldName, fieldValue) ->
				if (isPrivateFieldName(fieldName)) JsonPrimitive(REMOVED) else redact(fieldValue)
			},
		)
		is JsonArray -> JsonArray(value.map(::redact))
		is JsonPrimitive -> if (value.isString) JsonPrimitive(redactText(value.content)) else value
	}
}
