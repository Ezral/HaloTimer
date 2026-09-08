#!/usr/bin/env bash
# Collect screenshots while the emulator is still alive, including on test failure.
set -u
./gradlew :app:connectedDebugAndroidTest --no-daemon --stacktrace
halo_test_result=$?
adb pull /sdcard/Download/halo-qa native-screenshots || true
exit "$halo_test_result"
