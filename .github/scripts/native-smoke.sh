#!/usr/bin/env bash
# Collect screenshots while the emulator is still alive, including on test failure.
set -u
# Cold-boot Quickstep has shown an ANR under concurrent Gradle compilation.
# APKs are now compiled before boot; restart only the emulator launcher before testing.
adb shell am force-stop com.android.launcher3
adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME
mkdir -p native-screenshots
adb logcat -v threadtime > native-screenshots/logcat.txt 2>&1 &
halo_logcat_pid=$!
trap 'kill "$halo_logcat_pid" 2>/dev/null || true' EXIT
# Capture suites deliberately change timer definitions and launch other activities.
# They run in their dedicated capture workflows; keep runtime/editor regressions
# on a clean install so the smoke test's default-timer assumptions remain valid.
./gradlew :app:connectedDebugAndroidTest --no-daemon --stacktrace \
  -Dorg.gradle.jvmargs='-Xmx768m -Dfile.encoding=UTF-8' --max-workers=2 \
  -Pandroid.testInstrumentationRunnerArguments.class=com.ezral.halo.HaloUpgradeTest,com.ezral.halo.HaloSmokeTest
halo_test_result=$?
if [ "$halo_test_result" -ne 0 ]; then
  free -m > native-screenshots/host-memory.txt
  sudo dmesg --ctime > native-screenshots/kernel.txt 2>&1 || true
fi
adb pull /sdcard/Download/halo-qa/. native-screenshots/ || true
exit "$halo_test_result"
