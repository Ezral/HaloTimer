#!/usr/bin/env bash
set -euo pipefail
mkdir -p fold-promo-captures
adb emu unfold
# Half-resolution Fold7 aspect ratio; half density preserves the same logical layout.
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
adb shell wm size 984x1092
adb shell wm density 184
adb shell settings put secure immersive_mode_confirmations confirmed
adb shell am force-stop com.android.launcher3
adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME
adb shell wm size > fold-promo-captures/display-size.txt
adb shell wm density > fold-promo-captures/display-density.txt
adb shell dumpsys device_state > fold-promo-captures/device-state.txt
adb shell dumpsys display > fold-promo-captures/display-details.txt
set +e
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ezral.halo.HaloFoldPromoCapture -Pandroid.testInstrumentationRunnerArguments.haloFoldPromo=true --no-daemon --stacktrace
halo_fold_result=$?
adb pull /sdcard/Download/halo-fold-promo/. fold-promo-captures/ || true
exit "$halo_fold_result"
