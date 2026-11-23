package uk.rdfn.h.off

import org.junit.Assert.assertEquals
import org.junit.Test

class AppVersionTest {
	@Test
	fun showsTheBuildNumberForCiBuilds() {
		assertEquals("h.off 2.0.0 · build 75", describeAppVersion(versionName = "2.0.0", buildNumber = 75))
	}

	@Test
	fun saysLocalBuildWhenThereIsNoBuildNumber() {
		assertEquals("h.off 2.0.0 · local build", describeAppVersion(versionName = "2.0.0", buildNumber = 0))
	}
}
