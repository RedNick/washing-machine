# h.off

A fast, simple Android app for Haier, Candy and Hoover washing machines that use the h.on app.

It talks to the same Haier cloud as h.on, but does far less, so it opens quickly and stays out of the way. Version 2 is a native Android app written in Kotlin with Jetpack Compose (version 1 was React Native).

## What it does

- **Now**: what the machine is doing, the time left and when it will finish, with Pause, Resume and Stop.
- **Programs**: every program your machine offers, with its options (temperature, spin, extra rinses, drying, delay start…). Tap Start. Delay start can be set as a time to start or a time to be finished by. Star the ones you use most to keep them at the top, pick a fabric (Cotton, Synthetics, Mixed, Delicates, Drying) to narrow the list, search, and use **Edit list** to hide the ones you never use.
- **History**: lifetime energy, water and wash counts, how often you use each temperature, the washes you've run, and recent commands.
- **"Wash finished" alerts**: the phone sets an alert for the moment the wash ends, and keeps it in step as the time left changes. Nothing else needs to run anywhere.
- **Live countdown**: while a wash runs, a quiet notification counts down to the end.
- **Home-screen widget** and **Quick Settings tile**: what the machine is doing and when it'll be done, without opening the app.
- **Settings and reports** (from History): **Send a report** makes a JSON file of what the app sees (model, readings, recent h.on activity, program list) with sign-in details, the machine's name, MAC address and serial number removed, and opens the share sheet so you choose where it goes. **Crash reports** (off by default) go to Sentry with screenshots, sessions and tracing off and private details removed; the switch only works in builds made with the `SENTRY_DSN` secret. **Wash reports** (off by default) send a short summary at the end of each wash; see [Wash reports](#wash-reports).
- **Demo machine**: try everything without an h.on account. A demo wash runs at one machine minute per second.

## How sign-in works

The app opens **Haier's own sign-in page** in a Chrome tab. You sign in there, with your email and password or with **Sign in with Google** (Chrome offers the Google accounts already on your phone). Haier then hands the app sign-in tokens through an `hon://` link. The app keeps only the long-lived refresh token, encrypted with a key held in the phone's Android Keystore. It never sees or stores your password.

The app answers `hon://` links for this, the same as the official h.on app. If both apps are installed, Android may ask once which one should open the link: pick h.off.

> Why not the Android account picker? It would sign you in to *this* app, and Haier's servers only accept a Google sign-in made on Haier's own page. Google also blocks its sign-in inside in-app web views, which is why the page opens in Chrome.

### Updating from version 1

Version 2 installs over version 1. You sign in again once (the old sign-in was kept somewhere the new app can't read), but your favourites, hidden programs, fabric choice, wash log and report settings are copied over on first start.

## Install it on your phone

### From GitHub (easiest)

Every push to `main` builds the app on GitHub and publishes it on the repo's **Releases** page.

1. On your phone, open the repo's [latest release](https://github.com/RedNick/washing-machine/releases/latest) (sign in to GitHub if asked).
2. Tap `h-off-<version>-build-N.apk` to download it, then open it. Android will ask you to allow installs from your browser the first time.

Newer builds install over older ones and keep your sign-in. Builds from pull requests show up as pre-releases. To build without pushing, use **Run workflow** on the Actions tab.

The APK is signed with a shared, public debug key (`native/app/debug.keystore`, the one version 1's APKs used). That's fine for your own phone, but it can't go on the Play Store, and a copy installed from Google Play can't be updated with it.

### For Google Play

With the upload key in the repo's Actions secrets (`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`), every build also attaches `h-off-<version>-build-N-play.aab` to its release, and each push to `main` uploads it to the Play closed test (secret `PLAY_SERVICE_ACCOUNT_JSON`). Each build's version code is its run number of `build-android.yml`, so every upload counts as newer than the last. Google Play re-signs the app for users (Play App Signing), so keep the upload key safe, but it can be reset through Google if lost.

The privacy policy is published from the public repo `RedNick/public` at https://rednick.github.io/public/h-off/privacy/.

### On your own computer

You need JDK 21 and the Android SDK (platform 37.2, build tools 36). From `native/`:

```sh
./gradlew :app:assembleRelease -PbuildNumber=123   # APK in app/build/outputs/apk/release/
./gradlew :app:installDebug                         # build and install on a phone plugged in with USB debugging on
```

Without `-PbuildNumber` the Now screen says "local build". See [native/README.md](native/README.md) for more.

## Before you start a wash from your phone

The machine only accepts commands when **remote control** is switched on at the machine itself. Your machine's manual shows how; it's usually a button or a long press marked with a Wi-Fi or remote icon. The app warns you when it is off.

## Developing

From `native/`:

```sh
./gradlew :hon-client:test :app:testDebugUnitTest   # unit tests
./gradlew :app:lintRelease                           # Android lint
```

On GitHub, each APK is also installed on an Android emulator (`scripts/launch-test.sh`), which opens the demo machine and goes through each tab, starts a demo wash and opens Settings. The APK is only published if the app doesn't crash. If that check fails, the run's `launch-test` download has the device log and screenshots. The Play screenshots come from `scripts/play-screenshots.sh`, run by hand from the **Play screenshots** workflow.

### Layout

| Folder | What's in it |
| --- | --- |
| `native/hon-client/` | Plain Kotlin (no Android), with its tests: sign-in, tokens and requests to the h.on cloud, turning h.on's JSON into the app's types, the demo machine, the program list, the finish-alert planner, the wash log and the reports. |
| `native/app/` | The Android app: `ui/` (Compose screens), `connection/` (the `WasherRepository` every screen uses), `storage/`, `notifications/`, `background/` (the 15-minute check), `reporting/`, `widget/` and `tile/`. |
| `wash-report-worker/` | The Cloudflare Worker that stores optional wash reports (see below). |
| `scripts/` | The emulator launch test, the Play screenshot script and the icon renderer. |
| `assets/` | The app icon sources and the Google Play listing art. |

Screens only use the `WasherService` interface in `native/hon-client/src/main/kotlin/uk/rdfn/h/off/washer/WasherTypes.kt`. `HonCloudWasher` (the real machine) and `DemoWasher` both implement it.

## Good to know

- **Unofficial.** Haier has no public API. This app copies the requests worked out by the community [pyhOn](https://pypi.org/project/pyhOn/) library and the Home Assistant [hon](https://github.com/mmalolepszy/hon-revived) integration. If Haier changes their servers, parts of it may stop working until it's updated. `HonConstants.kt` is the first place to look.
- **Being polite to Haier's servers.** In 2024 Haier objected to the Home Assistant integration polling every 10 seconds. This app only checks while it is open: every 30 seconds while a wash is running and every 60 seconds otherwise. While closed, it checks about every 15 minutes.
- **Option combinations.** Some machines don't allow every combination of options (for example, a high spin on a wool program). h.on sends rules for this, but the app doesn't apply them yet. If the machine rejects a combination, the app shows the error.
- **Alerts** depend on the machine's own estimate of the time left. Android may also deliver the alert a few minutes late to save battery (the app doesn't ask for the exact-alarm permission).
- **Cloud only.** h.on machines can't be controlled over the local network.

## Wash reports

If you switch **Wash reports** on in Settings, the app sends a short summary at the end of each wash (model, program, energy and water, and the machine's final readings, with private details removed) to a Cloudflare Worker in `wash-report-worker/`. It stores them in a D1 database for 180 days. There is no web page for reading them back: run the **Export wash reports** workflow from the Actions tab to download them.

Setting it up:
1. In Cloudflare, create an API token from the "Edit Cloudflare Workers" template, adding **Account → D1 → Edit**. Add it as the repository secret `CLOUDFLARE_API_TOKEN`, and your account ID as `CLOUDFLARE_ACCOUNT_ID`.
2. Run the **Deploy wash report worker** workflow. It creates the database and prints the worker's address (`https://h-off-wash-reports.<your subdomain>.workers.dev`).
3. Add that address as the secret `WASH_REPORT_URL`. Builds after that can switch wash reports on.
