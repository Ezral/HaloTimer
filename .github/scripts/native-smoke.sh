#!/usr/bin/env bash
# Collect screenshots while the emulator is still alive, including on test failure.
set -u
# Cold-boot Quickstep has shown an ANR under concurrent Gradle compilation.
# APKs are now compiled before boot; restart only the emulator launcher before testing.
adb shell am force-stop com.android.launcher3
adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME
# Capture suites deliberately change timer definitions and launch other activities.
# They run in their dedicated capture workflows; keep runtime/editor regressions
# on a clean install so the smoke test's default-timer assumptions remain valid.
./gradlew :app:connectedDebugAndroidTest --no-daemon --stacktrace \
  -Pandroid.testInstrumentationRunnerArguments.class=com.ezral.halo.HaloSmokeTest,com.ezral.halo.HaloUpgradeTest
halo_test_result=$?
adb pull /sdcard/Download/halo-qa native-screenshots || true
exit "$halo_test_result"
