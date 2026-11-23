package uk.rdfn.h.off.hon

import kotlinx.serialization.json.JsonElement
import uk.rdfn.h.off.washer.formatHonNumber

/*
 * Reading h.on's parameter definitions. h.on describes every setting as
 * either fixed, a range of numbers, or a list of choices ("enum"):
 *
 *   { "typology": "range", "minimumValue": "0", "maximumValue": "1410",
 *     "incrementValue": "30", "defaultValue": "0" }
 *
 * Ported from src/hon/honParameterValues.ts.
 */

/** h.on sometimes wraps defaults in brackets, e.g. "[dashboard]". */
fun stripBrackets(rawValue: String): String = rawValue.removePrefix("[").removeSuffix("]")

/** Parses h.on's numeric strings, which can use a comma as decimal point. Missing or invalid values are 0. */
fun parseHonNumber(rawValue: JsonElement?): Double = parseHonNumber(rawValue.asText())

fun parseHonNumber(rawValue: String?): Double {
	if (rawValue.isNullOrEmpty()) return 0.0
	return rawValue.replace(',', '.').trim().toDoubleOrNull()?.takeIf { it.isFinite() } ?: 0.0
}

/** The "typology" of a parameter definition: "fixed", "range" or "enum". */
fun typologyOf(definition: JsonElement?): String? = definition["typology"].asText()

/** A parameter's enumValues as text, e.g. ["0", "20", "40"]. */
fun enumValuesOf(definition: JsonElement?): List<String> =
	definition["enumValues"].asArray()?.mapNotNull { it.asText() } ?: emptyList()

/**
 * The value h.on would use for a parameter if the user does not change it,
 * as the string the API expects.
 */
fun defaultValueOf(definition: JsonElement?): String =
	when (typologyOf(definition)) {
		// A missing fixed value is sent as "0", matching pyhOn.
		"fixed" -> definition["fixedValue"].asText() ?: "0"
		"range" -> {
			val rawDefault = definition["defaultValue"].asText() ?: definition["minimumValue"].asText()
			// Written without a decimal point for whole numbers ("40", not "40.0"), as the machine expects.
			formatHonNumber(parseHonNumber(rawDefault))
		}
		"enum" -> {
			val rawDefault = definition["defaultValue"].asText()
			when {
				rawDefault != null -> stripBrackets(rawDefault)
				else -> enumValuesOf(definition).firstOrNull() ?: "0"
			}
		}
		else -> ""
	}
