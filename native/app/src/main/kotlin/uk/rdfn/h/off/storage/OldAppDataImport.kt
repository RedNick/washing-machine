package uk.rdfn.h.off.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import uk.rdfn.h.off.hon.honJson

/**
 * A one-off copy of your favourites, hidden programs, wash log and report
 * settings from the React Native version of h.off (1.x), which this app
 * replaces in place.
 *
 * The old app kept them in AsyncStorage: a SQLite database called
 * "RKStorage" with one table of key/value text pairs. That file is left on
 * the phone after the update, so we read it once on first start and then
 * never look at it again. The old sign-in is not copied (it was in a
 * different, encrypted store), so you sign in again once.
 */
class OldAppDataImport(private val context: Context, private val storage: AppStorage) {
	suspend fun importOnce() {
		if (storage.readText(AppStorage.OLD_APP_IMPORT_DONE) != null) return
		runCatching { importFrom(readOldValues()) }
			.onFailure { Log.w(TAG, "Couldn't copy the old app's settings", it) }
		storage.writeText(AppStorage.OLD_APP_IMPORT_DONE, "1")
	}

	private fun readOldValues(): Map<String, String> {
		val databaseFile = context.getDatabasePath(OLD_DATABASE_NAME)
		if (!databaseFile.exists()) return emptyMap()
		val values = mutableMapOf<String, String>()
		SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
			database.rawQuery("SELECT key, value FROM catalystLocalStorage", null).use { rows ->
				while (rows.moveToNext()) {
					val key = rows.getString(0) ?: continue
					val value = rows.getString(1) ?: continue
					values[key] = value
				}
			}
		}
		return values
	}

	private suspend fun importFrom(oldValues: Map<String, String>) {
		if (oldValues.isEmpty()) return
		// These two were saved in the same shape the new app uses.
		oldValues["programList.choices"]?.let { copyIfValid(AppStorage.PROGRAM_LIST_CHOICES, it) }
		oldValues["diagnostics.settings"]?.let { copyIfValid(AppStorage.DIAGNOSTICS_SETTINGS, it) }

		// The wash log was two separate values. The old app wrote machine
		// states in lower case ("running"); the new one uses RUNNING.
		val entries = oldValues["cycleLog.entries"]?.let(::parseOrNull) as? JsonArray
		val lastSeen = oldValues["cycleLog.lastSeen"]?.let(::parseOrNull) as? JsonObject
		if (entries != null || lastSeen != null) {
			val cycleLog = buildJsonObject {
				put("entries", entries ?: JsonArray(emptyList()))
				if (lastSeen != null) put("lastSeen", upperCaseState(lastSeen))
			}
			copyIfValid(AppStorage.CYCLE_LOG, cycleLog.toString())
		}
		// The alert memory isn't copied: the old app's scheduled alert can't be
		// moved, and the next status check works out a fresh one.
	}

	/** Copies the JSON only if the new app can read it back. */
	private suspend fun copyIfValid(key: androidx.datastore.preferences.core.Preferences.Key<String>, json: String) {
		storage.writeText(key, json)
		val readable = when (key) {
			AppStorage.PROGRAM_LIST_CHOICES -> storage.programListChoicesOrNull()
			AppStorage.DIAGNOSTICS_SETTINGS -> storage.diagnosticsSettingsOrNull()
			AppStorage.CYCLE_LOG -> storage.cycleLogOrNull()
			else -> null
		}
		if (readable == null) storage.writeText(key, null)
	}

	private fun parseOrNull(text: String): JsonElement? = runCatching { honJson.parseToJsonElement(text) }.getOrNull()

	private fun upperCaseState(lastSeen: JsonObject): JsonObject {
		val state = (lastSeen["state"] as? JsonPrimitive)?.content ?: return lastSeen
		return JsonObject(lastSeen + ("state" to JsonPrimitive(state.uppercase())))
	}

	private companion object {
		const val TAG = "OldAppDataImport"
		const val OLD_DATABASE_NAME = "RKStorage"
	}
}
