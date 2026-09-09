#!/usr/bin/env bash
# Collect screenshots while the emulator is still alive, including on test failure.
set -u
# Cold-boot Quickstep has shown an ANR under concurrent Gradle compilation.
# APKs are now compiled before boot; restart only the emulator launcher before testing.
adb shell am force-stop com.android.launcher3
adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME
./gradlew :app:connectedDebugAndroidTest --no-daemon --stacktrace
halo_test_result=$?
adb pull /sdcard/Download/halo-qa native-screenshots || true
exit "$halo_test_result"
