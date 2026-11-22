package uk.rdfn.h.off

/**
 * The version shown at the bottom of the Now screen, e.g.
 * "h.off 2.0.0 · build 75", so you can tell which build is installed.
 *
 * The build number is the GitHub Actions run number, which is also the Play
 * version code. Builds made by hand have no build number (0).
 */
fun describeAppVersion(
	versionName: String = BuildConfig.VERSION_NAME,
	buildNumber: Int = BuildConfig.BUILD_NUMBER,
): String =
	if (buildNumber > 0) "h.off $versionName · build $buildNumber"
	else "h.off $versionName · local build"
