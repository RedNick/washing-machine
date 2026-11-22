// The native (Kotlin + Jetpack Compose) version of h.off.
// Built in phases; see native/README.md.

pluginManagement {
	repositories {
		google()
		mavenCentral()
		gradlePluginPortal()
	}
}

dependencyResolutionManagement {
	repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
	repositories {
		google()
		mavenCentral()
	}
}

rootProject.name = "h-off"

// Plain Kotlin module that talks to Haier's h.on cloud. No Android code, so its tests run fast.
include(":hon-client")
// The Android app itself: screens, storage, background checks and alerts.
include(":app")
