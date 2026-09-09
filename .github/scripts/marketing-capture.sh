#!/usr/bin/env bash
set -u
adb shell am force-stop com.android.launcher3
adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME
if [[ "${1:-marketing}" == "features" ]]; then
  ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ezral.halo.HaloFeatureCapture --no-daemon --stacktrace
  halo_capture_result=$?
  adb pull /sdcard/Download/halo-qa marketing-captures || true
else
  ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ezral.halo.HaloMarketingCapture -Pandroid.testInstrumentationRunnerArguments.haloMarketing=true --no-daemon --stacktrace
  halo_capture_result=$?
  adb pull /sdcard/Download/halo-marketing marketing-captures || true
fi
exit "$halo_capture_result"
