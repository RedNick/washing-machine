package uk.rdfn.h.off.hon

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random

/** What we keep on the phone between launches. Never the password. */
data class SavedHonSession(
	val refreshToken: String,
	/** Random id for this phone, sent with every request like the h.on app does. */
	val mobileId: String,
)

/** Where [SavedHonSession] lives. The app uses encrypted storage. */
interface SessionStore {
	suspend fun load(): SavedHonSession?
	suspend fun save(session: SavedHonSession)
	suspend fun clear()
}

/** Thrown when we can't sign in, or the saved sign-in no longer works. */
class HonAuthException(message: String) : Exception(message)

/**
 * Keeps the h.on tokens fresh and hands out the headers every API request
 * needs.
 *
 * h.on uses two tokens: an "id token" from the sign-in service, and a
 * "cognito token" that the API gives us in exchange for it. Both last about
 * 8 hours. The long-lived refresh token gets us new ones without asking the
 * user to sign in again.
 *
 * Ported from src/hon/auth/honSession.ts.
 */
class HonSession private constructor(
	private var refreshToken: String,
	val mobileId: String,
	private val store: SessionStore,
	private val httpSender: HttpSender,
	private val currentTimeMillis: () -> Long,
) {
	private var idToken = ""
	private var cognitoToken = ""
	private var tokensIssuedAtMillis = 0L

	/** Shared by concurrent callers so we never refresh twice at once. */
	private val refreshLock = Mutex()
	private var refreshInProgress: CompletableDeferred<Unit>? = null

	companion object {
		/** Starts a session from the tokens the sign-in page gave us. */
		suspend fun fromLogin(
			loginTokens: HonLoginTokens,
			store: SessionStore,
			httpSender: HttpSender,
			currentTimeMillis: () -> Long = System::currentTimeMillis,
		): HonSession {
			val existing = store.load()
			val session = HonSession(
				loginTokens.refreshToken,
				existing?.mobileId ?: generateMobileId(),
				store,
				httpSender,
				currentTimeMillis,
			)
			session.idToken = loginTokens.idToken
			session.exchangeIdTokenForCognitoToken()
			store.save(SavedHonSession(session.refreshToken, session.mobileId))
			return session
		}

		/**
		 * Picks up where the last launch left off. Returns null when there is
		 * no saved sign-in, so the app should show the sign-in screen.
		 */
		suspend fun restore(
			store: SessionStore,
			httpSender: HttpSender,
			currentTimeMillis: () -> Long = System::currentTimeMillis,
		): HonSession? {
			val saved = store.load() ?: return null
			if (saved.refreshToken.isEmpty()) return null
			val session = HonSession(saved.refreshToken, saved.mobileId, store, httpSender, currentTimeMillis)
			session.refreshTokens()
			return session
		}

		/** 16 random hex digits, like the h.on app's own id. */
		private fun generateMobileId(): String = (1..16).joinToString("") { Random.nextInt(16).toString(16) }
	}

	/** Headers for an API request, refreshing the tokens first if they're old. */
	suspend fun authorizedHeaders(): Map<String, String> {
		if (currentTimeMillis() - tokensIssuedAtMillis >= HonConstants.TOKEN_REFRESH_AFTER_MILLISECONDS) {
			refreshTokens()
		}
		return mapOf(
			"user-agent" to HonConstants.USER_AGENT,
			"Content-Type" to "application/json",
			"cognito-token" to cognitoToken,
			"id-token" to idToken,
		)
	}

	/** Gets fresh tokens using the refresh token. Callers arriving during a refresh wait for that one. */
	suspend fun refreshTokens() {
		val (refresh, isOurs) = refreshLock.withLock {
			val running = refreshInProgress
			if (running != null) {
				running to false
			} else {
				CompletableDeferred<Unit>().also { refreshInProgress = it } to true
			}
		}
		if (!isOurs) return refresh.await()
		try {
			performRefresh()
			refresh.complete(Unit)
		} catch (problem: Throwable) {
			refresh.completeExceptionally(problem)
			throw problem
		} finally {
			refreshLock.withLock { refreshInProgress = null }
		}
	}

	/** Forgets the saved sign-in. */
	suspend fun signOut() {
		idToken = ""
		cognitoToken = ""
		refreshToken = ""
		store.clear()
	}

	private suspend fun performRefresh() {
		val query = listOf(
			"client_id=${encodeUriComponent(HonConstants.OAUTH_CLIENT_ID)}",
			"refresh_token=${encodeUriComponent(refreshToken)}",
			"grant_type=refresh_token",
		).joinToString("&")
		val response = httpSender.send(
			HttpRequest(
				method = "POST",
				url = "${HonConstants.AUTH_BASE_URL}/services/oauth2/token?$query",
				headers = mapOf("user-agent" to HonConstants.USER_AGENT),
			),
		)
		if (!response.isSuccessful) {
			throw HonAuthException("Your h.on sign-in has expired (refresh failed with ${response.status}).")
		}
		val tokenResponse = runCatching { honJson.parseToJsonElement(response.bodyText) }.getOrNull()
		val newIdToken = tokenResponse["id_token"].asText()
			?: throw HonAuthException("h.on did not return a new sign-in token.")
		idToken = newIdToken
		// Salesforce normally keeps the same refresh token, but use a new one if offered.
		val newRefreshToken = tokenResponse["refresh_token"].asText()
		if (!newRefreshToken.isNullOrEmpty() && newRefreshToken != refreshToken) {
			refreshToken = newRefreshToken
			store.save(SavedHonSession(refreshToken, mobileId))
		}
		exchangeIdTokenForCognitoToken()
	}

	/** The API wants its own "cognito" token, which it swaps for the id token. */
	private suspend fun exchangeIdTokenForCognitoToken() {
		val body = buildJsonObject {
			put("appVersion", HonConstants.APP_VERSION)
			put("mobileId", mobileId)
			put("os", HonConstants.OS_NAME)
			put("osVersion", HonConstants.OS_VERSION)
			put("deviceModel", HonConstants.DEVICE_MODEL)
		}
		val response = httpSender.send(
			HttpRequest(
				method = "POST",
				url = "${HonConstants.API_BASE_URL}/auth/v1/login",
				headers = mapOf(
					"user-agent" to HonConstants.USER_AGENT,
					"Content-Type" to "application/json",
					"id-token" to idToken,
				),
				body = body.toString(),
			),
		)
		val loginResponse = runCatching { honJson.parseToJsonElement(response.bodyText) }.getOrNull()
		val newCognitoToken = loginResponse["cognitoUser"]["Token"].asText()
		if (!response.isSuccessful || newCognitoToken.isNullOrEmpty()) {
			throw HonAuthException("h.on refused the sign-in (status ${response.status}).")
		}
		cognitoToken = newCognitoToken
		tokensIssuedAtMillis = currentTimeMillis()
	}
}
