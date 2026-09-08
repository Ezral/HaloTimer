#!/usr/bin/env bash
set -u
adb shell am force-stop com.android.launcher3
adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ezral.halo.HaloMarketingCapture -Pandroid.testInstrumentationRunnerArguments.haloMarketing=true --no-daemon --stacktrace
halo_capture_result=$?
adb pull /sdcard/Download/halo-marketing marketing-captures || true
exit "$halo_capture_result"
