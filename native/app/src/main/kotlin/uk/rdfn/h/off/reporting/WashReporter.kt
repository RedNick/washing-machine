package uk.rdfn.h.off.reporting

import android.util.Log
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import uk.rdfn.h.off.BuildConfig

/**
 * Wash reports: a short summary sent at the end of each wash, only when
 * "Wash reports" is switched on in Settings. They go to the h.off wash
 * report worker (wash-report-worker/ in this repo) and are mainly for
 * learning how different machines signal the end of a program.
 */
class WashReporter(
	private val client: OkHttpClient = OkHttpClient.Builder()
		// Give up after this long, so a slow network can't hold up a status check.
		.callTimeout(15, TimeUnit.SECONDS)
		.build(),
) {
	/** False when this build has no worker address, so the Settings switch is disabled. */
	val isSetUp: Boolean get() = BuildConfig.WASH_REPORT_URL.isNotEmpty()

	/** Never throws: a report that can't be sent is simply dropped. */
	suspend fun send(report: JsonElement) {
		if (!isSetUp) return
		withContext(Dispatchers.IO) {
			runCatching {
				val request = Request.Builder()
					.url(BuildConfig.WASH_REPORT_URL.trimEnd('/') + "/reports")
					.post(report.toString().toRequestBody("application/json".toMediaType()))
					.build()
				client.newCall(request).execute().use { response ->
					if (!response.isSuccessful) Log.w(TAG, "Wash report refused: HTTP ${response.code}")
				}
			}.onFailure { Log.w(TAG, "Couldn't send the wash report", it) }
		}
	}

	private companion object {
		const val TAG = "WashReporter"
	}
}
