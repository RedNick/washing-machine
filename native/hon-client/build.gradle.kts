// The h.on cloud client: plain Kotlin, no Android, so it can be tested on any computer.
plugins {
	alias(libs.plugins.kotlin.jvm)
}

kotlin {
	jvmToolchain(21)
}

dependencies {
	testImplementation(libs.junit)
}
