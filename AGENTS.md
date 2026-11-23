h.off is a native Android app in Kotlin with Jetpack Compose, in `native/`. The Cloudflare Worker for wash reports is a separate TypeScript project in `wash-report-worker/`.

## Check versions, don't trust memory

Android libraries change quickly. Before adding or upgrading a library, check its current stable version on its official release page (developer.android.com/jetpack/androidx/releases/…, kotlinlang.org, the library's GitHub releases). `dl.google.com` may be blocked in cloud sessions, so read the release pages rather than the Maven index. All versions live in `native/gradle/libs.versions.toml`; keep the "checked on" date there current.

When unsure of an API, look at the library's own classes (for example `javap` on the downloaded jar) or its docs instead of guessing.

## Commands

Run from `native/`, with JDK 21 and the Android SDK (platform 37.2, build tools 36):

```bash
./gradlew :hon-client:test :app:testDebugUnitTest   # unit tests
./gradlew :app:lintRelease                           # Android lint
./gradlew :app:assembleRelease -PbuildNumber=999     # release APK
```

Run the tests and lint before declaring any task done. CI (`.github/workflows/build-android.yml`) runs the same, then opens the APK on an emulator with `scripts/launch-test.sh`.

## Rules

- **Never change** the application id `uk.rdfn.h.off`, the debug signing key in `native/app/debug.keystore`, or the name of `build-android.yml`: the version code is that workflow's run number, and Play needs it to keep rising.
- Every build shows its version and build number on the Now screen (`describeAppVersion()`).
- Code that doesn't need Android goes in `hon-client/` with a unit test. The app module is for screens and phone services.
- Screens talk to the machine only through `WasherRepository` and the `WasherService` interface, so the demo machine keeps working.
- UK English in the app and comments. Tab indentation in Kotlin and Gradle files (YAML keeps spaces). Readable names and comments that say why.
- Never put personal details, tokens or passwords in code, logs or reports. Reports go through `PrivateDetails.redact`.
