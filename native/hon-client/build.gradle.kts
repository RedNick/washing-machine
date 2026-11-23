// The h.on cloud client: plain Kotlin, no Android, so it can be tested on any computer.
plugins {
	alias(libs.plugins.kotlin.jvm)
	alias(libs.plugins.kotlin.serialization)
}

kotlin {
	jvmToolchain(21)
}

dependencies {
	api(libs.kotlinx.coroutines.core)
	api(libs.kotlinx.serialization.json)
	implementation(libs.okhttp)

	testImplementation(libs.junit)
	testImplementation(libs.kotlinx.coroutines.test)
}
