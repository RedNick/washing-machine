package uk.rdfn.h.off.hon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ported from src/hon/__tests__/honLogin.test.ts. */
class HonLoginTest {
	@Test
	fun pointsAtTheHonSignInPageWithTheAppsSettings() {
		val url = HonLogin.buildLoginPageUrl("11111111-2222-3333-4444-555555555555")
		assertTrue(url.startsWith("https://account2.hon-smarthome.com/services/oauth2/authorize/expid_Login?"))
		assertTrue(url.contains("response_type=token+id_token"))
		assertTrue(url.contains("redirect_uri=hon%3A%2F%2Fmobilesdk%2Fdetect%2Foauth%2Fdone"))
		assertTrue(url.contains("scope=api%20openid%20refresh_token%20web"))
		assertTrue(url.contains("nonce=11111111-2222-3333-4444-555555555555"))
	}

	@Test
	fun usesAFreshUuidShapedNonceByDefault() {
		assertTrue(
			Regex("nonce=[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$").containsMatchIn(HonLogin.buildLoginPageUrl()),
		)
	}

	private val redirect =
		"hon://mobilesdk/detect/oauth/done#access_token=00Dabc%21AQ&refresh_token=5Aep861%3D%3D&instance_url=x&id_token=eyJ.payload.sig&issued_at=1"

	@Test
	fun recognisesTheRedirect() {
		assertTrue(HonLogin.isLoginRedirect(redirect))
		assertFalse(HonLogin.isLoginRedirect("https://account2.hon-smarthome.com/s/login"))
	}

	@Test
	fun pullsOutTheThreeTokens() {
		assertEquals(HonLoginTokens("00Dabc!AQ", "5Aep861==", "eyJ.payload.sig"), HonLogin.parseLoginRedirect(redirect))
	}

	@Test
	fun returnsNullWhenATokenIsMissing() {
		assertNull(HonLogin.parseLoginRedirect("hon://mobilesdk/detect/oauth/done#access_token=a&id_token=b"))
	}
}
