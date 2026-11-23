package uk.rdfn.h.off.hon

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** One HTTP request, as the h.on client needs it. */
data class HttpRequest(
	val method: String,
	val url: String,
	val headers: Map<String, String> = emptyMap(),
	/** JSON text, or null for no body. */
	val body: String? = null,
)

/** The parts of an HTTP answer the h.on client looks at. */
data class HttpResponse(val status: Int, val bodyText: String) {
	val isSuccessful: Boolean get() = status in 200..299
}

/**
 * Sends HTTP requests. The real one uses OkHttp; tests use a fake that
 * answers like Haier's servers, so no test ever talks to h.on.
 */
fun interface HttpSender {
	suspend fun send(request: HttpRequest): HttpResponse
}

/** Thrown when the phone can't reach h.on at all (no signal, DNS failure…). */
class HonNetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The real [HttpSender], using OkHttp on a background thread. */
class OkHttpSender(
	private val client: OkHttpClient = OkHttpClient.Builder()
		.connectTimeout(20, TimeUnit.SECONDS)
		// The program catalogue is about 1 MB, so allow time on a slow connection.
		.readTimeout(60, TimeUnit.SECONDS)
		.build(),
) : HttpSender {
	override suspend fun send(request: HttpRequest): HttpResponse = withContext(Dispatchers.IO) {
		val requestBody = when {
			request.body != null -> request.body.toRequestBody(JSON_MEDIA_TYPE)
			// OkHttp insists on a body for POST, even an empty one.
			request.method == "POST" -> ByteArray(0).toRequestBody(null)
			else -> null
		}
		val okHttpRequest = Request.Builder()
			.url(request.url)
			.apply { request.headers.forEach { (name, value) -> header(name, value) } }
			.method(request.method, requestBody)
			.build()
		try {
			client.newCall(okHttpRequest).execute().use { response ->
				HttpResponse(response.code, response.body.string())
			}
		} catch (networkProblem: IOException) {
			throw HonNetworkException("Couldn't reach h.on. Check your internet connection.", networkProblem)
		}
	}

	private companion object {
		val JSON_MEDIA_TYPE = "application/json".toMediaType()
	}
}
