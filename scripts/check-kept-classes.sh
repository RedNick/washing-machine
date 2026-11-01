#!/usr/bin/env bash
# Fails if the code shrinker (R8) removed or renamed classes the background
# "wash finished" check depends on. Build 4 crashed because of exactly this.
#
# Usage: scripts/check-kept-classes.sh path/to/app.apk
set -euo pipefail

apk_path="$1"

# Classes that Android or WorkManager looks up by name at run time, so a
# renamed or removed copy breaks the app without any build error.
required_classes=(
  "androidx.work.impl.background.systemjob.SystemJobService"
  "androidx.work.impl.WorkDatabase_Impl"
  "androidx.work.impl.background.systemalarm.RescheduleReceiver"
)

apkanalyzer="${ANDROID_HOME:-$ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/apkanalyzer"
defined_classes="$("$apkanalyzer" dex packages --defined-only "$apk_path")"

missing_count=0
for class_name in "${required_classes[@]}"; do
  if ! grep -q -F "$class_name" <<< "$defined_classes"; then
    echo "::error::The shrinker removed or renamed $class_name"
    missing_count=$((missing_count + 1))
  fi
done

if [ "$missing_count" -gt 0 ]; then
  exit 1
fi
echo "All ${#required_classes[@]} background-work classes are still in the app."
