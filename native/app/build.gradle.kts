// The h.off Android app.
plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.compose)
}

/**
 * The build number is the GitHub Actions run number of build-android.yml,
 * passed in as -PbuildNumber=N. It is also the Play version code, so every
 * upload counts as newer than the last. Builds made by hand don't have one.
 */
val buildNumber: Int? = (findProperty("buildNumber") as String?)?.toIntOrNull()

android {
	namespace = "uk.rdfn.h.off"
	// Android 17 QPR2 (API 37.2): the Compose BOM's libraries need at least 37.1.
	compileSdk {
		version = release(37) { minorApiLevel = 2 }
	}

	defaultConfig {
		// Must never change: it is what ties this app to its Google Play listing.
		applicationId = "uk.rdfn.h.off"
		minSdk = 29
		targetSdk = 36
		versionCode = buildNumber ?: 1
		versionName = "2.0.0"

		// Shown on the Now screen, e.g. "h.off 2.0.0 · build 75".
		buildConfigField("int", "BUILD_NUMBER", (buildNumber ?: 0).toString())
	}

	signingConfigs {
		// The public React Native debug key the Expo app's GitHub APKs were signed with.
		// Using the same one means new APKs install over the old app. Play builds are
		// signed with the real upload key instead (see build-android.yml).
		getByName("debug") {
			storeFile = file("debug.keystore")
			storePassword = "android"
			keyAlias = "androiddebugkey"
			keyPassword = "android"
		}
	}

	buildTypes {
		release {
			isMinifyEnabled = true
			isShrinkResources = true
			signingConfig = signingConfigs.getByName("debug")
			proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
		}
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_21
		targetCompatibility = JavaVersion.VERSION_21
	}

	buildFeatures {
		compose = true
		buildConfig = true
	}
}

kotlin {
	jvmToolchain(21)
}

dependencies {
	implementation(project(":hon-client"))

	implementation(platform(libs.androidx.compose.bom))
	implementation(libs.androidx.compose.material3)
	implementation(libs.androidx.compose.ui)
	implementation(libs.androidx.compose.ui.tooling.preview)
	implementation(libs.androidx.activity.compose)
	implementation(libs.androidx.lifecycle.runtime.compose)
	debugImplementation(libs.androidx.compose.ui.tooling)

	testImplementation(libs.junit)
}
