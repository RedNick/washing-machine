package uk.rdfn.h.off.hon

import kotlinx.serialization.json.JsonElement
import uk.rdfn.h.off.washer.WasherCommandException

/** One request to the h.on API. */
data class HonApiRequest(
	val method: String,
	/** Path under the API, e.g. "/commands/v1/context". */
	val path: String,
	val query: Map<String, String> = emptyMap(),
	val body: JsonElement? = null,
)

private fun isAuthRejection(status: Int): Boolean = status == 401 || status == 403

/**
 * Sends one request to the h.on API with the session's tokens. If h.on says
 * the tokens are no longer valid, it refreshes them and tries exactly once
 * more.
 *
 * Ported from src/hon/honApiRequest.ts.
 */
suspend fun sendHonApiRequest(session: HonSession, httpSender: HttpSender, request: HonApiRequest): JsonElement {
	val queryString = if (request.query.isEmpty()) {
		""
	} else {
		"?" + request.query.entries.joinToString("&") { (key, value) -> "$key=${encodeUriComponent(value)}" }
	}
	val url = "${HonConstants.API_BASE_URL}${request.path}$queryString"

	suspend fun send(): HttpResponse = httpSender.send(
		HttpRequest(
			method = request.method,
			url = url,
			headers = session.authorizedHeaders(),
			body = request.body?.toString(),
		),
	)

	var response = send()
	if (isAuthRejection(response.status)) {
		session.refreshTokens()
		response = send()
		if (isAuthRejection(response.status)) {
			throw HonAuthException("h.on no longer accepts your sign-in. Please sign in again.")
		}
	}
	if (!response.isSuccessful) {
		throw WasherCommandException("h.on returned an error (${response.status}). Try again in a minute.")
	}
	return runCatching { honJson.parseToJsonElement(response.bodyText) }.getOrElse {
		throw WasherCommandException("h.on sent an answer the app couldn't read. Try again in a minute.")
	}
}
