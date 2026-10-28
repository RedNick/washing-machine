#!/usr/bin/env bash
# Opens the app on a running Android emulator and fails if it crashes.
#
# Usage: scripts/launch-test.sh path/to/app.apk
#
# Leaves launch-logcat.txt (the device log) and launch-screenshot.png in the
# current folder so a failure can be looked at afterwards.
set -euo pipefail

apk_path="$1"
package_name="uk.ncls.h.off"
# Long enough for the app to load its JavaScript and draw the first screen.
seconds_to_watch=30

adb install -r "$apk_path"
adb logcat -c

echo "Opening $package_name and watching it for ${seconds_to_watch}s…"
adb shell monkey -p "$package_name" -c android.intent.category.LAUNCHER 1 > /dev/null
sleep "$seconds_to_watch"

adb exec-out screencap -p > launch-screenshot.png || true
adb logcat -d -v threadtime > launch-logcat.txt || true

# Crashes (Java, native and fatal JavaScript errors) end up in the crash
# buffer, or as "FATAL EXCEPTION" / ReactNativeJS errors in the main log.
crash_log="$(adb logcat -d -b crash || true)"
app_process_id="$(adb shell pidof "$package_name" || true)"

if [ -n "$crash_log" ] || [ -z "$app_process_id" ]; then
  echo "::error::The app crashed or closed within ${seconds_to_watch}s of opening."
  echo "----- crash log -----"
  echo "$crash_log"
  echo "----- app errors from the main log -----"
  grep -E "FATAL|AndroidRuntime|ReactNativeJS|ReactNative|libc|DEBUG|Expo" launch-logcat.txt | tail -n 200 || true
  exit 1
fi

echo "The app is still running after ${seconds_to_watch}s (process $app_process_id)."
grep -E "ReactNativeJS" launch-logcat.txt | tail -n 50 || true
