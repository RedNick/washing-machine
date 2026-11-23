# h.off, native app

The Kotlin + Jetpack Compose app. See the [main README](../README.md) for what
it does and how to install it.

| Module | What's in it |
| --- | --- |
| `hon-client/` | Plain Kotlin (no Android) code that talks to Haier's h.on cloud, with its tests. |
| `app/` | The Android app: screens, storage, background checks and alerts. |

All versions are in `gradle/libs.versions.toml`.

## Building

You need JDK 21 and the Android SDK (platform 37.2, build tools 36).

```sh
./gradlew :hon-client:test :app:testDebugUnitTest   # unit tests
./gradlew :app:lintRelease                           # Android lint
./gradlew :app:assembleRelease -PbuildNumber=123     # release APK in app/build/outputs/apk/release/
```

Without `-PbuildNumber` the Now screen says "local build" and the version code is 1.

## Signing

- The APK is signed with the public React Native debug key in `app/debug.keystore`
  (password `android`), the same key the version 1 (Expo) APKs used, so it
  installs over them. It is not a secret.
- The Play bundle is signed in CI with the upload key from the repo's secrets.
