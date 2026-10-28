# Washer

A fast, simple Android app for Haier, Candy and Hoover washing machines that use the h.on app.

It talks to the same Haier cloud as h.on, but does far less, so it opens quickly and stays out of the way.

## What it does

- **Now**: what the machine is doing, the time left and when it will finish, with Pause, Resume and Stop.
- **Programs**: every program your machine offers, with its options (temperature, spin, delay start, extra rinse…). Tap Start. Star the ones you use most to keep them at the top. The rest are grouped (Everyday, Quick, Delicates, Care and hygiene, Rinse and spin), and **Edit list** hides the ones you never use.
- **History**: lifetime energy, water and wash counts, how often you use each temperature, the washes you've run, and recent commands.
- **"Wash finished" alerts**: the phone schedules an alert for the moment the wash ends, and keeps it in step as the time left changes. Nothing else needs to run anywhere.
- **Demo machine**: try everything without an h.on account. A demo wash runs at one machine minute per second.

## How sign-in works

The app opens **Haier's own sign-in page** in a Chrome tab. You sign in there, with your email and password or with **Sign in with Google** (Chrome offers the Google accounts already on your phone). Haier then hands the app sign-in tokens through an `hon://` link. The app keeps only the long-lived refresh token, in Android's encrypted storage. It never sees or stores your password.

The app answers `hon://` links for this, the same as the official h.on app. If both apps are installed, Android may ask once which one should open the link: pick Washer.

> Why not the Android account picker? It would sign you in to *this* app, and Haier's servers only accept a Google sign-in made on Haier's own page. Google also blocks its sign-in inside in-app web views, which is why the page opens in Chrome.

## Install it on your phone

### From GitHub (easiest)

Every push to `main` builds the app on GitHub and publishes it on the repo's **Releases** page.

1. On your phone, open the repo's [latest release](https://github.com/RedNick/washing-machine/releases/latest) (sign in to GitHub if asked).
2. Tap `washer-build-N.apk` to download it, then open it. Android will ask you to allow installs from your browser the first time.

Newer builds install over older ones and keep your sign-in. To build without pushing, use **Run workflow** on the Actions tab (builds from other branches show up as pre-releases).

The APK is signed with Expo's shared debug key. That's fine for your own phone, but it can't go on the Play Store.

### With Expo's cloud build

You need a free [Expo](https://expo.dev) account.

```sh
npm install
npx eas-cli@latest login
npx eas-cli@latest build --platform android --profile preview
```

When the build finishes, open the link it prints on your phone and install the APK.

If you have Android Studio, `npm run android` with the phone plugged in (USB debugging on) builds and installs it directly.

> The app uses features that Expo Go doesn't include (background checks, secure storage), so use one of the builds above rather than Expo Go.

## Before you start a wash from your phone

The machine only accepts commands when **remote control** is switched on at the machine itself. Your machine's manual shows how; it's usually a button or a long press marked with a Wi-Fi or remote icon. The app warns you when it is off.

## Developing

```sh
npm test           # unit tests
npm run typecheck  # TypeScript
npm start          # Metro bundler for a development build
```

On GitHub, each APK is also opened on an Android emulator (`scripts/launch-test.sh`) and is only published if the app starts without crashing. If that check fails, the run's `launch-test` download has the device log and a screenshot.

Code shrinking (R8) is turned off for release builds in `app.json`. It removed classes that Android's background-task scheduler needs, which made build 4 crash on launch.

### Layout

| Folder | What's in it |
| --- | --- |
| `src/app/` | Screens. Each file is a route (Expo Router). |
| `src/hon/` | Everything that talks to the h.on cloud: sign-in, tokens, requests, and turning h.on's JSON into the app's types. |
| `src/washer/` | The app's own washing-machine types, the demo machine, shared state (`WasherContext`) and formatting helpers. |
| `src/notifications/` | The "wash finished" alert. `planFinishAlert.ts` decides what to do and `finishAlertScheduler.ts` does it. |
| `src/background/` | The check Android runs about every 15 minutes while the app is closed. |
| `src/storage/` | Secure token storage and the on-phone log of finished washes. |
| `src/programs/` | Organising the program list: groups, favourites and hidden programs (saved on the phone). |
| `src/components/` | Small shared UI pieces and colours. |

Screens only use the `WasherService` interface in `src/washer/washerTypes.ts`. `HonCloudWasher` (the real machine) and `DemoWasher` both implement it.

## Good to know

- **Unofficial.** Haier has no public API. This app copies the requests worked out by the community [pyhOn](https://pypi.org/project/pyhOn/) library and the Home Assistant [hon](https://github.com/mmalolepszy/hon-revived) integration. If Haier changes their servers, parts of it may stop working until it's updated. `src/hon/honConstants.ts` is the first place to look.
- **Being polite to Haier's servers.** In 2024 Haier objected to the Home Assistant integration polling every 10 seconds. This app only checks while it is open: every 30 seconds while a wash is running and every 60 seconds otherwise. While closed, it checks about every 15 minutes.
- **Option combinations.** Some machines don't allow every combination of options (for example, a high spin on a wool program). h.on sends rules for this, but the app doesn't apply them yet. If the machine rejects a combination, the app shows the error.
- **Alerts** depend on the machine's own estimate of the time left. If the estimate jumps a lot after the background check, the alert can be a few minutes off.
- **Cloud only.** h.on machines can't be controlled over the local network.
