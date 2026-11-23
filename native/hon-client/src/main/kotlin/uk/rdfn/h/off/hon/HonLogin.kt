package uk.rdfn.h.off.hon

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Signing in works like the official app: we open Haier's own sign-in page
 * (in a Chrome tab), the user signs in there, and when it is done the page
 * redirects to [HonConstants.LOGIN_REDIRECT_URI] with the tokens in the
 * address. This app never sees the password.
 *
 * Ported from src/hon/auth/honLogin.ts.
 */
object HonLogin {
	/** The address of Haier's sign-in page, ready to open in the browser. */
	fun buildLoginPageUrl(nonce: String = UUID.randomUUID().toString()): String {
		val queryParts = listOf(
			// The literal "+" is how the h.on app asks for both token types.
			"response_type=token+id_token",
			"client_id=${encodeUriComponent(HonConstants.OAUTH_CLIENT_ID)}",
			"redirect_uri=${encodeUriComponent(HonConstants.LOGIN_REDIRECT_URI)}",
			"display=touch",
			"scope=${encodeUriComponent("api openid refresh_token web")}",
			"nonce=$nonce",
		)
		return "${HonConstants.AUTH_BASE_URL}/services/oauth2/authorize/expid_Login?${queryParts.joinToString("&")}"
	}

	/** True for the "signed in" redirect link. */
	fun isLoginRedirect(url: String): Boolean = url.startsWith(HonConstants.LOGIN_REDIRECT_URI)

	/**
	 * Reads the tokens out of the redirect address, which looks like
	 * hon://mobilesdk/detect/oauth/done#access_token=…&refresh_token=…&id_token=…
	 * Returns null if any token is missing.
	 */
	fun parseLoginRedirect(url: String): HonLoginTokens? {
		val fragment = url.substringAfter('#', missingDelimiterValue = "").ifEmpty {
			url.substringAfter('?', missingDelimiterValue = "")
		}
		val values = fragment.split('&').mapNotNull { pair ->
			val separatorIndex = pair.indexOf('=')
			if (separatorIndex <= 0) null else pair.substring(0, separatorIndex) to pair.substring(separatorIndex + 1)
		}.toMap()

		val accessToken = values["access_token"]
		val refreshToken = values["refresh_token"]
		val idToken = values["id_token"]
		if (accessToken.isNullOrEmpty() || refreshToken.isNullOrEmpty() || idToken.isNullOrEmpty()) return null
		return HonLoginTokens(
			accessToken = decodeUriComponent(accessToken),
			refreshToken = decodeUriComponent(refreshToken),
			idToken = decodeUriComponent(idToken),
		)
	}
}

/** Tokens returned by the sign-in page. */
data class HonLoginTokens(
	val accessToken: String,
	val refreshToken: String,
	/** Proves who you are to the h.on API; exchanged for a "cognito" token. */
	val idToken: String,
)

/** Like JavaScript's encodeURIComponent: spaces become %20, not "+". */
fun encodeUriComponent(value: String): String =
	URLEncoder.encode(value, StandardCharsets.UTF_8)
		.replace("+", "%20")
		.replace("%21", "!")
		.replace("%27", "'")
		.replace("%28", "(")
		.replace("%29", ")")
		.replace("%7E", "~")

/** Like JavaScript's decodeURIComponent: a "+" stays a "+". */
fun decodeUriComponent(value: String): String =
	URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8)
