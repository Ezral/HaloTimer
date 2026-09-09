# Fold7 marketing capture

Run the **Fold7 marketing video** workflow on `feat/native-halo-android`.
It builds the existing Halo app and records a 16-second timer, an upward swipe
between two stock clips, and the actual completion reveal on an opened generic
foldable emulator configured to 1968 × 2184 pixels at 368 logical dpi.

`ShortsBackdropActivity` is a swipeable demonstration feed in the test APK.
It is not YouTube and is not distributed in the Halo APK. The timer and completion
UI are the real Halo overlay. This is not a physical Samsung/One UI test.

The workflow downloads and transcodes two stock videos for test-only playback:

- Tima Miroshnichenko, [A Man Working Out Using Dumbbell](https://www.pexels.com/video/a-man-working-out-using-dumbbell-5319099/).
- Tima Miroshnichenko, [A Man Working Out Inside the Gym](https://www.pexels.com/video/a-man-working-out-inside-the-gym-5319856/).

[Pexels license](https://www.pexels.com/license/): permits commercial use and
modification; no endorsement by the depicted people is implied. Downloaded footage
is generated during CI, not committed to the repository or included in the app.
