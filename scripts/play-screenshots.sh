#!/usr/bin/env bash
# Takes the Google Play screenshots on a running Android emulator, using the
# app's demo machine so no h.on account is needed.
#
# Usage: scripts/play-screenshots.sh path/to/app.apk
#
# Saves numbered PNGs in play-screenshots/ in the current folder:
#   1-welcome, 2-programs, 3-program-options, 4-wash-running, 5-history.
set -Eeuo pipefail

apk_path="$1"
package_name="uk.rdfn.h.off"
output_folder="play-screenshots"
mkdir -p "$output_folder"

# If any step fails, keep what the screen and the device log showed.
save_failure_details() {
  adb exec-out screencap -p > "$output_folder/failed-here.png" || true
  adb logcat -d -v threadtime > "$output_folder/logcat.txt" || true
}
trap save_failure_details ERR

# Prints the centre "x y" of the first on-screen element whose text or
# accessibility label is exactly $1, or nothing if it isn't on screen.
find_on_screen() {
  local wanted_text="$1"
  adb shell uiautomator dump /sdcard/screen.xml > /dev/null
  adb shell cat /sdcard/screen.xml | python3 -c '
import re, sys
wanted_text = sys.argv[1]
screen = sys.stdin.read()
for node in re.findall(r"<node [^>]*>", screen):
    text = re.search(r" text=\"([^\"]*)\"", node).group(1)
    label = re.search(r" content-desc=\"([^\"]*)\"", node).group(1)
    if wanted_text in (text, label):
        left, top, right, bottom = map(int, re.findall(r"\d+", re.search(r" bounds=\"([^\"]*)\"", node).group(1)))
        print((left + right) // 2, (top + bottom) // 2)
        break
' "$wanted_text"
}

# Taps the element showing $1, waiting up to 30s for it to appear.
tap_text() {
  local wanted_text="$1"
  for attempt in $(seq 1 15); do
    local centre
    centre="$(find_on_screen "$wanted_text")"
    if [ -n "$centre" ]; then
      echo "Tapping \"$wanted_text\" at $centre"
      # $centre is "x y", so it is left unquoted to pass two arguments.
      adb shell input tap $centre
      return 0
    fi
    sleep 2
  done
  echo "::error::Couldn't find \"$wanted_text\" on screen."
  adb exec-out screencap -p > "$output_folder/not-found.png" || true
  exit 1
}

# Waits for the screen to settle, then saves it as $output_folder/$1.png.
take_screenshot() {
  sleep 4
  adb exec-out screencap -p > "$output_folder/$1.png"
  echo "Saved $output_folder/$1.png"
}

adb install -r "$apk_path"
# Grant the alert permission up front so its prompt doesn't cover the app.
adb shell pm grant "$package_name" android.permission.POST_NOTIFICATIONS

# Android's demo mode gives a tidy status bar: 12:00, full battery and signal.
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter > /dev/null
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200 > /dev/null
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false > /dev/null
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 > /dev/null
adb shell am broadcast -a com.android.systemui.demo -e command network -e mobile show -e datatype none -e level 4 > /dev/null
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false > /dev/null

# Open the app from its launcher entry. monkey finds the launcher activity
# itself, so this doesn't depend on the activity's class name.
adb shell monkey -p "$package_name" -c android.intent.category.LAUNCHER 1 > /dev/null
# Give the first launch time to draw.
sleep 10
take_screenshot 1-welcome

tap_text "Try the demo machine"
tap_text "Programs"
take_screenshot 2-programs

tap_text "Cottons"
take_screenshot 3-program-options

# The Start button is at the bottom of the options, so scroll down to it.
# Starting a program takes the app back to the Now tab.
adb shell input swipe 540 1500 540 300 300
adb shell input swipe 540 1500 540 300 300
sleep 1
tap_text "Start"
take_screenshot 4-wash-running

tap_text "History"
take_screenshot 5-history

adb shell am broadcast -a com.android.systemui.demo -e command exit > /dev/null
ls -l "$output_folder"
