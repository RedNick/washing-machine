package uk.rdfn.h.off.hon

import org.junit.Assert.assertTrue
import org.junit.Test

/** Placeholder test so the test task runs from phase 0; real tests arrive with the h.on port. */
class HonConstantsTest {
	@Test
	fun pollingStaysWithinHaiersLimitWhileIdle() {
		assertTrue(HonConstants.STATUS_POLL_WHILE_IDLE_MILLISECONDS >= 60_000)
	}

	@Test
	fun tokensAreRefreshedBeforeTheyExpire() {
		val eightHoursInMilliseconds = 8L * 60 * 60 * 1000
		assertTrue(HonConstants.TOKEN_REFRESH_AFTER_MILLISECONDS < eightHoursInMilliseconds)
	}
}
