#!/usr/bin/env bash
set -euo pipefail
mkdir -p fold-captures
adb emu unfold
# Portrait Fold7 inner-display pixel dimensions, with an explicit test density.
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
adb shell wm size 1968x2184
adb shell wm density 368
adb shell am force-stop com.android.launcher3
adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME
adb shell wm size > fold-captures/display-size.txt
adb shell wm density > fold-captures/display-density.txt
adb shell dumpsys device_state > fold-captures/device-state.txt
adb shell dumpsys display > fold-captures/display-details.txt
set +e
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ezral.halo.HaloFoldCapture -Pandroid.testInstrumentationRunnerArguments.haloFold=true --no-daemon --stacktrace
halo_fold_result=$?
adb pull /sdcard/Download/halo-fold/. fold-captures/ || true
exit "$halo_fold_result"
