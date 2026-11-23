package uk.rdfn.h.off.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import uk.rdfn.h.off.alerts.FinishAlertMemory
import uk.rdfn.h.off.history.CycleLogState
import uk.rdfn.h.off.hon.honJson
import uk.rdfn.h.off.programs.ProgramListChoices

/** The single preferences file everything below lives in. */
internal val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "h-off")

/** The two optional automatic reports. Both are off until you switch them on. */
@Serializable
data class DiagnosticsSettings(
	val sendCrashReports: Boolean = false,
	val sendWashReports: Boolean = false,
)

/**
 * What the widget and Quick Settings tile show. Saved after every status
 * check so they can draw without asking h.on themselves.
 */
@Serializable
data class StatusSnapshot(
	/** The [uk.rdfn.h.off.washer.MachineState] name, e.g. RUNNING. */
	val stateName: String,
	/** e.g. "Washing", "Ready". */
	val headline: String,
	/** e.g. "42 min left · done around 14:05". Empty when there's nothing to add. */
	val detail: String,
	val programName: String?,
	/** When the wash should end, for the countdown. Null when nothing is running. */
	val finishAtMillis: Long?,
	val checkedAtMillis: Long,
)

/**
 * Everything h.off keeps on the phone apart from the sign-in, which is in
 * [KeystoreSessionStore]. Each value is a small JSON string in DataStore.
 *
 * The demo machine gets its own wash log and alert memory, kept in memory
 * only, so trying the demo never mixes pretend washes into your real ones.
 */
class AppStorage(private val dataStore: DataStore<Preferences>) {
	private var demoCycleLog = CycleLogState()
	private var demoAlertMemory = FinishAlertMemory()

	val programListChoices: Flow<ProgramListChoices> =
		dataStore.data.map { decode(it, PROGRAM_LIST_CHOICES, ProgramListChoices.serializer()) ?: ProgramListChoices() }

	val diagnosticsSettings: Flow<DiagnosticsSettings> =
		dataStore.data.map { decode(it, DIAGNOSTICS_SETTINGS, DiagnosticsSettings.serializer()) ?: DiagnosticsSettings() }

	val statusSnapshot: Flow<StatusSnapshot?> =
		dataStore.data.map { decode(it, STATUS_SNAPSHOT, StatusSnapshot.serializer()) }

	suspend fun updateProgramListChoices(change: (ProgramListChoices) -> ProgramListChoices) {
		dataStore.edit { preferences ->
			val current = decode(preferences, PROGRAM_LIST_CHOICES, ProgramListChoices.serializer()) ?: ProgramListChoices()
			preferences[PROGRAM_LIST_CHOICES] = honJson.encodeToString(ProgramListChoices.serializer(), change(current))
		}
	}

	suspend fun saveDiagnosticsSettings(settings: DiagnosticsSettings) =
		write(DIAGNOSTICS_SETTINGS, DiagnosticsSettings.serializer(), settings)

	suspend fun loadDiagnosticsSettings(): DiagnosticsSettings = diagnosticsSettings.first()

	suspend fun loadCycleLog(isDemo: Boolean): CycleLogState =
		if (isDemo) demoCycleLog else read(CYCLE_LOG, CycleLogState.serializer()) ?: CycleLogState()

	suspend fun saveCycleLog(isDemo: Boolean, state: CycleLogState) {
		if (isDemo) demoCycleLog = state else write(CYCLE_LOG, CycleLogState.serializer(), state)
	}

	suspend fun loadFinishAlertMemory(isDemo: Boolean): FinishAlertMemory =
		if (isDemo) demoAlertMemory else read(FINISH_ALERT_MEMORY, FinishAlertMemory.serializer()) ?: FinishAlertMemory()

	suspend fun saveFinishAlertMemory(isDemo: Boolean, memory: FinishAlertMemory) {
		if (isDemo) demoAlertMemory = memory else write(FINISH_ALERT_MEMORY, FinishAlertMemory.serializer(), memory)
	}

	suspend fun saveStatusSnapshot(snapshot: StatusSnapshot?) {
		dataStore.edit { preferences ->
			if (snapshot == null) {
				preferences.remove(STATUS_SNAPSHOT)
			} else {
				preferences[STATUS_SNAPSHOT] = honJson.encodeToString(StatusSnapshot.serializer(), snapshot)
			}
		}
	}

	/** Forgets the demo's pretend washes, when you leave the demo. */
	fun forgetDemo() {
		demoCycleLog = CycleLogState()
		demoAlertMemory = FinishAlertMemory()
	}

	/** After signing out: the alert memory belongs to that account's machine. */
	suspend fun forgetAlertMemory() {
		dataStore.edit { it.remove(FINISH_ALERT_MEMORY) }
	}

	// Used by OldAppDataImport to check a copied value can be read back.
	internal suspend fun programListChoicesOrNull() = read(PROGRAM_LIST_CHOICES, ProgramListChoices.serializer())
	internal suspend fun diagnosticsSettingsOrNull() = read(DIAGNOSTICS_SETTINGS, DiagnosticsSettings.serializer())
	internal suspend fun cycleLogOrNull() = read(CYCLE_LOG, CycleLogState.serializer())

	/** Raw access for [KeystoreSessionStore] and [OldAppDataImport]. */
	internal suspend fun readText(key: Preferences.Key<String>): String? = dataStore.data.first()[key]

	internal suspend fun writeText(key: Preferences.Key<String>, value: String?) {
		dataStore.edit { preferences -> if (value == null) preferences.remove(key) else preferences[key] = value }
	}

	private suspend fun <T> read(key: Preferences.Key<String>, serializer: KSerializer<T>): T? =
		decode(dataStore.data.first(), key, serializer)

	private suspend fun <T> write(key: Preferences.Key<String>, serializer: KSerializer<T>, value: T) {
		dataStore.edit { it[key] = honJson.encodeToString(serializer, value) }
	}

	/** A garbled value (say, from an older version) counts as nothing saved. */
	private fun <T> decode(preferences: Preferences, key: Preferences.Key<String>, serializer: KSerializer<T>): T? {
		val text = preferences[key] ?: return null
		return runCatching { honJson.decodeFromString(serializer, text) }.getOrNull()
	}

	companion object {
		// The same names the React Native app used in AsyncStorage.
		internal val PROGRAM_LIST_CHOICES = stringPreferencesKey("programList.choices")
		internal val DIAGNOSTICS_SETTINGS = stringPreferencesKey("diagnostics.settings")
		internal val CYCLE_LOG = stringPreferencesKey("cycleLog")
		internal val FINISH_ALERT_MEMORY = stringPreferencesKey("finishAlert.memory")
		internal val STATUS_SNAPSHOT = stringPreferencesKey("status.snapshot")
		internal val ENCRYPTED_SESSION = stringPreferencesKey("hon.session.encrypted")
		internal val OLD_APP_IMPORT_DONE = stringPreferencesKey("oldApp.importDone")
	}
}
