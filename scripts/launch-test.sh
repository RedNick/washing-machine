#!/usr/bin/env bash
# Opens h.off on an emulator and checks it doesn't crash: first the sign-in
# screen, then the demo machine and each tab, a program and Settings.
# Run by the launch-test job in .github/workflows/build-android.yml.
#
#   ./scripts/launch-test.sh path/to/app.apk
#
# Leaves launch-logcat.txt and launch-*.png screenshots behind for the job to keep.
set -euo pipefail

apk_path="$1"
package_name="uk.rdfn.h.off"

# -g grants the notification permission up front, so its dialog doesn't cover the screen.
adb install -r -g "$apk_path"
adb logcat -c

# Fails the test if the app has crashed or closed.
check_still_running() {
	local step="$1"
	local crash_log app_process_id
	crash_log="$(adb logcat -d -b crash || true)"
	app_process_id="$(adb shell pidof "$package_name" || true)"
	if [ -n "$crash_log" ] || [ -z "$app_process_id" ]; then
		adb logcat -d -v threadtime > launch-logcat.txt || true
		echo "::error::The app crashed or closed: $step"
		echo "----- crash log -----"
		echo "$crash_log"
		echo "----- app errors from the main log -----"
		grep -E "FATAL|AndroidRuntime|$package_name" launch-logcat.txt | tail -n 200 || true
		exit 1
	fi
	echo "Still running after: $step (process $app_process_id)"
}

screenshot() {
	adb exec-out screencap -p > "launch-$1.png" || true
}

# Taps the first thing on screen showing exactly this text. Waits up to 20 s for it to appear.
tap_text() {
	local text="$1"
	for _ in $(seq 1 20); do
		adb shell uiautomator dump /sdcard/launch-ui.xml > /dev/null 2>&1 || true
		local bounds
		bounds="$(adb shell cat /sdcard/launch-ui.xml 2>/dev/null \
			| grep -o "text=\"$text\"[^>]*bounds=\"\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]\"" \
			| head -1 | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' || true)"
		if [ -n "$bounds" ]; then
			# [left,top][right,bottom] -> the middle.
			read -r left top right bottom <<< "$(echo "$bounds" | tr -c '0-9' ' ')"
			adb shell input tap $(( (left + right) / 2 )) $(( (top + bottom) / 2 ))
			echo "Tapped \"$text\""
			return 0
		fi
		sleep 1
	done
	screenshot "missing"
	echo "::error::Couldn't find \"$text\" on screen."
	exit 1
}

# Scrolls the screen up, to reach buttons below the fold.
scroll_down() {
	local size width height
	size="$(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1)"
	width="${size%x*}"
	height="${size#*x}"
	adb shell input swipe $(( width / 2 )) $(( height * 3 / 4 )) $(( width / 2 )) $(( height / 4 )) 300
	sleep 1
}

echo "Opening $package_name…"
adb shell monkey -p "$package_name" -c android.intent.category.LAUNCHER 1 > /dev/null
sleep 10
check_still_running "opening the app"
screenshot "sign-in"

tap_text "Try the demo machine"
sleep 8
check_still_running "opening the demo machine"
screenshot "now"

tap_text "Programs"
sleep 4
check_still_running "the Programs tab"
screenshot "programs"

tap_text "Cottons"
sleep 3
check_still_running "opening a program"
screenshot "program"

scroll_down
scroll_down
tap_text "Start"
sleep 8
check_still_running "starting the demo wash"
screenshot "running"

tap_text "History"
sleep 4
check_still_running "the History tab"
screenshot "history"

# The button is under the History cards.
scroll_down
scroll_down
tap_text "Settings and reports"
sleep 3
check_still_running "Settings"
screenshot "settings"

adb logcat -d -v threadtime > launch-logcat.txt || true
echo "The app ran through the demo without crashing."
