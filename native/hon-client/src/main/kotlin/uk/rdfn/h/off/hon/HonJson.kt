package uk.rdfn.h.off.hon

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import uk.rdfn.h.off.washer.formatHonNumber

/*
 * Small helpers for reading h.on's JSON.
 *
 * h.on's answers are large, loosely shaped and change between models, so the
 * client reads them as plain JSON trees (like the TypeScript version did)
 * instead of fixed classes that would break on every new field.
 */

/** Lenient JSON settings shared by the whole client. */
val honJson: Json = Json {
	ignoreUnknownKeys = true
	isLenient = true
	prettyPrint = false
}

/** The element as an object, or null when it is missing or something else. */
fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

/** The element as an array, or null when it is missing or something else. */
fun JsonElement?.asArray(): JsonArray? = this as? JsonArray

/** Looks up a key when this is an object. */
operator fun JsonElement?.get(key: String): JsonElement? = (this as? JsonObject)?.get(key)

/**
 * The element as text, the way JavaScript's String() would write it: numbers
 * without a trailing ".0", true/false as words. Null for JSON null, objects,
 * arrays and missing values.
 */
fun JsonElement?.asText(): String? {
	val primitive = this as? JsonPrimitive ?: return null
	if (primitive is JsonNull) return null
	if (primitive.isString) return primitive.content
	val number = primitive.doubleOrNull
	return if (number != null) formatHonNumber(number) else primitive.content
}
