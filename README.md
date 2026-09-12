# HaloTimer · v1.2

A quiet Android timer that stays with you while you use your phone.

HaloTimer turns the edge of your display into a countdown. Run up to three timers, build named sequences, and get a moving light or a vibration when time is up. Keep the controls as a compact pill, or pull them into a shallow dock at either side of the screen.

[Quick start](docs/QUICK_START.md) · [v1.2 notes](docs/releases/V1.2.md) · [Download builds](https://github.com/Ezral/HaloTimer/actions/workflows/android.yml)

## Get started

1. Install the APK from a successful **Android** workflow's `halo-debug-apk` artifact. GitHub may require you to sign in to download it.
2. Open Halo and enable **Display over other apps** in the Permissions card.
3. Pick a timer tab, enter a duration and tap the top-right play icon.
4. Halo returns to the previous app or home. Your timer stays visible over supported apps.

Requires **Android 8.0 or newer**. Version 1.0 is the product milestone; the downloadable APK still uses the existing development signing identity and `com.ezral.halo.debug` package. Store distribution and production signing are separate release tasks.

## Timers and sequences

- **Three independent tracks.** Rename Timer A, B and C, switch between document-style tabs, and activate each track separately. Mix standalone timers and sequences in any combination.
- **Whole-minute and whole-second controls.** Type, swipe vertically, scroll with a mouse, or use the accessible plus/minus actions. Seconds carry and borrow across minute boundaries: `00:59 ↔ 01:00`.
- **Flexible duration.** Each timer or sequence step supports `00:01–99:59` by default. Enable **Hours** on a timer to use `00:00:01–99:59:59`, including its sequence steps; HH:MM:SS appears across the menu, pill and dock. Turning Hours off never truncates a longer duration. Quick presets set a single timer to 1, 5, 15 or 25 minutes.
- **Repeat a timer or whole sequence.** Choose 1× (default), infinity, or 1–9999 total rounds. Rounds advance immediately; Reset returns to round one, paused, and Stop ends the loop. Round alerts play once; your configured final-alert repeat applies at the end of the last round. Missed rounds are reconciled without replaying a backlog.
- **Replace digits directly.** Tap HRS, MIN or SEC and type the new value; the first input replaces the old value. Tap Done or move to another field to apply it.
- **Named sequences.** Add up to 50 steps with independent names and durations. Halo advances automatically, shows the current step and resets that track's edge progress for each step.
- **Editable examples.** Pour-over includes blooming and slow pouring. Steak includes searing, flipping, the fatty side and resting. Loading an example asks before replacing your current steps.
- **Independent playback.** Pause, resume, stop or restart one timer while the others continue. Adjust a running timer by 30 seconds from its menu.
- **Persistent configuration.** Timer definitions, positions, appearance choices and run checkpoints are stored locally. A running sequence uses a snapshot of its steps; stop/reset it before changing its structure.

Timer names support 1–24 characters; step names support 1–60 characters.

## Light around the display

The halo uses full-display coordinates, including the status and navigation bar regions. Three 4dp colored tracks are separated by 2dp gaps. Supported Android versions provide physical corner radii for the light's path.

Each track has its own color and glow strength. Choose from the palette—including bright red, strong blue and purple—or use the custom hue/saturation/brightness palette (hex input is optional). Palette colors already used by another active track are marked unavailable; use distinct custom colors to keep tracks easy to distinguish.

The final palette circle remembers your last saved custom color for quick reuse. **Aurora**, **Sunset** and **Electric** add mixed-color edge lines, while the pill and dock keep a solid matching accent.

When time is up, choose one of four continuous animations:

| Alert | Appearance |
| --- | --- |
| Breathe | The light brightens and softens in a smooth cycle. |
| Orbit | A light travels continuously around the display. |
| Ping-pong | A light travels along the perimeter and reverses direction. |
| Double pong | Two lights travel and bounce around the perimeter. |

Final edge alerts continue until dismissed or stopped. **Preview edge alert** lets you try a style without starting a timer or leaving the menu. **Text motion on timer dock** controls the dock label independently of edge alerts and surface transitions.

## Full-screen timer display

Choose **Full screen** in Timer display, select any one, two or three active tracks, and open the display. One timer fills the view, two split it, and three use pizza-style sections. Layouts adapt to portrait and landscape. Each section shows its name, current step, fixed-width countdown and round, with independent play/pause, reset and stop controls.

Starting from the menu opens this display when full-screen mode is selected. The header starts the displayed timers, stops all timers, opens settings or returns to overlays. Floating windows are suppressed while the full-screen display is visible; the same timer sessions continue when switching back.

Enabled completion color animations expand and shrink inside their own section, leaving other timers visible. Repeating rounds show a round-complete message; the last round shows the final timer-complete message. Very fast repeats coalesce visual reveals already in progress. **Keep screen awake** is optional and off by default; it applies only while a displayed timer runs.

The display's menu opens its controls immediately: select timers, choose Pizza or Split for three timers, show/hide names and playback controls, adjust number size, line thickness, line spacing and alert speed, and set an edge inset. Phone corner matching is on by default and uses reported corner radii and curved-glass insets; a manual corner radius is available for phones that need adjustment. Each section has an independent inset perimeter, so its animation cannot paint over a neighboring section's line.

## Floating pill and edge dock

The controls use one opaque, solid surface matching the timer's line color, without glass, gradients or outlines. Countdown digits use **JetBrains Mono** for fixed spacing; menu and label typography uses **Poppins**.

The pill contains four icons:

| Control | Action |
| --- | --- |
| Play / Pause | Start, pause or resume the selected timer, depending on its state. |
| Reset arrow | Return to the beginning, paused and ready to start again. For a sequence, return to its first step. |
| Stop square | End this timer, cancel its alerts and remove its floating control. |
| Settings gear | Open the Halo menu on this timer's tab. |

Drag the countdown area to move the pill. Contact with either screen edge starts a controlled merge animation before you lift your finger. Release to form a broad, shallow dock. Pull inward to separate it into a circle; while still holding, move to the opposite edge to dock again.

The dock centers minutes above seconds, or hours / minutes / seconds when Hours is enabled. Its single bold label follows the dock's curved outline, with letters rotating along the path and clipping at the physical edge. A held full circle uses a continuous circular path. Sequence labels show **current step · timer name**; standalone labels show the timer name. Long labels are shortened to fit.

Long-press the dock to reveal four evenly spaced action bubbles. Slide to Play/Pause, Reset, Stop or Settings and release to select. Release away from a target to cancel. The bubbles retract into the dock with a short exit animation.

### Name and visibility options

- **Show timer name:** display the name just outside the pill's bottom-left edge, in the pill's color.
- **Text rotates around pill:** move that one label around the outside instead of holding it below the pill. This needs Show timer name enabled.
- **Floating control:** show or hide the control for a track from its menu. This setting does not stop the timer or edge light; the pill itself has Stop instead of Hide.
- **Show all floating controls:** restore controls from Halo or the ongoing notification.

Entering the Halo menu hides floating controls. Timers normally continue; enable **Dismiss all timers on menu entry** if opening the menu should stop them all.

## Vibration, including your own Morse text

Choose **Double tap** or **Morse** per timer, with separate **Vibration** and **Sound** switches. Sound is off by default; the soft 660 Hz tone follows the same pulse lengths, silent gaps and repeat limits. It uses the alarm volume and requests transient audio focus; it does not override device volume or Do Not Disturb. Morse text is entered in a popup; saving updates its dot-and-dash display automatically. Use A–Z, 0–9 and spaces, up to 24 characters and a maximum encoded pattern length of 20 seconds.

Final-alert vibration supports:

- Once, 3× or 5×.
- Until dismiss.
- A custom count of 1–99 repetitions.
- A custom duration of 1–3600 seconds.

These repeat settings apply to Morse as well as double taps. **Test vibration once** previews one cycle. Repeat choices also apply to sequence-step alerts, except Until dismiss becomes one cycle between steps so it cannot continue indefinitely into the next step. Simultaneous alerts share a serial vibration queue so their patterns do not overlap. Actual haptic output follows the phone's hardware and Android settings.

## Color-expansion completion screen

An optional full-screen color reveal expands from the pill or dock when the timer finishes. It displays **Timer is completed for**, followed by the timer name and final sequence step when applicable.

In the **Completion screen** card, control:

- Enable/disable with **Expand timer color**.
- Display duration: **1–30 seconds** after expansion.
- Text size: **18–48sp**.
- Bold or regular text.
- Left or center alignment.

On timeout or an early tap, the color contracts back toward the pill/dock. The menu preview wraps to fit text size, timer name and sequence step. Tap the completion page to close it early. Closing it leaves the final edge alert active; use Stop or Dismiss to end that alert. A finished session does not repeatedly reopen the page.

## Menu, themes and notifications

The persistent header places selected-timer Play/Pause, Start all and Stop all icons at the top right. A soft shadow separates it from scrolling settings.

Choose **Light**, **Dark** or **Follow system** in the bottom Appearance card. The floating surfaces retain each timer's solid color in every theme.

Notifications are silent. The ongoing notification opens Halo and offers grouped Pause/Resume, Show controls and Stop all actions. Completion notifications include Dismiss. Private lock-screen content uses a generic public notification.

Optional **Volume buttons in Halo** adjusts the selected timer while Halo has focus: 30-second steps, 1-minute steps after holding for 3 seconds, and 5-minute steps after 8 seconds. A hold ends after 15 seconds or when its target changes. This does not intercept volume keys while another app is active.

## Permissions, timing and privacy

| Access | Purpose |
| --- | --- |
| Display over other apps | Draw the edge light and floating controls over supported apps. |
| Notifications | Show timer status and completion actions. |
| Precise alarm access | Improve screen-off deadline delivery where Android permits it. |
| Vibration | Play the selected haptic pattern. |

Android can hide overlays on protected screens, and system windows can cover them. Full-display coordinates do not place Halo above every system surface. Screen-off delivery and very short sequence alerts during deep sleep depend on Android and manufacturer power management.

Countdowns use monotonic deadlines rather than counting rendered frames. A delayed callback reconciles the logical sequence position without extending the sequence. Reboot or a user-requested app stop interrupts running sessions rather than silently starting them again.

Halo has no account, ads, analytics SDK or runtime Internet permission. Data stays in the app's local Room database and DataStore preferences. The app does not use screen capture or request Accessibility access. Uninstalling clears its local data.

## Build and development

Use JDK 17 and Android SDK 36. The repository pins Gradle 8.13, Android Gradle Plugin 8.13.2 and Kotlin 2.2.21. Minimum SDK is 26; compile and target SDK are 36.

```sh
./gradlew :core:timer:test :app:lintDebug :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

With an Android emulator or device attached:

```sh
./gradlew :app:connectedDebugAndroidTest
```

CI reuses its development signing identity so compatible previous builds update in place. Android Studio may use a different local debug key. Release signing is not configured.

| Area | Location |
| --- | --- |
| Pure timer engine, sequence logic, Morse and JVM tests | `core/timer` |
| Room checkpoints and preferences | `app/.../data` |
| Coordinator, foreground service, alarms and haptics | `app/.../runtime` |
| Edge renderer, pill, dock and completion screen | `app/.../overlay` |
| Compose editor and themes | `app/.../ui` |
| Design decisions and validation protocols | `docs/adr`, `docs/validation` |

The v1.0 feature baseline passed 42 JVM tests, Android lint with zero errors, and native Android 15 gesture/playback/capture checks. Phone testing also informed the overlay revisions. This does not constitute certification across all devices; see [validation status](docs/validation/STATUS.md) and the [implementation plan](docs/HALO_ANDROID_IMPLEMENTATION_PLAN.md).

Bundled Poppins and JetBrains Mono licenses are included in `app/src/main/assets/licenses/`.
