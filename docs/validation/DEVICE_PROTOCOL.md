# Device acceptance protocol

Record app commit, APK SHA-256, device model, OS build fingerprint, navigation mode, font scale, battery settings and permission state for every run. Never label an unperformed step “passed.” Primary target: owner's Samsung S24; also use a Pixel/reference Android and a midrange 60Hz device.

## Install and baseline

Build the debug APK, install it and open Halo. Start with notifications, overlays and exact alarms denied. In-app timers must still work; denied capabilities must be represented honestly. Grant each permission through system UI and return to Halo. No permission is required merely to edit a timer.

## SP01 — Touch and geometry (release blocker)

1. Configure and start three 5-minute timers. Open a host app containing a grid of touch targets.
2. Tap every host target outside the actual glass control bounds, including the transparent center and corners. Record missed touches and relevant logcat security messages.
3. Hide every glass control; repeat the grid. Progress must continue and every host target must work.
4. Restore controls through the notification, then through the launcher with notification permission denied.
5. Drag all controls to each corner; test narrow portrait, landscape, open keyboard and both navigation modes. Ensure no controls become unreachable. Check protected screens; Halo must respect suppression.
6. Capture screenshots at native resolution. Inspect three adjacent cores, rounded corners, no clipped strokes, no intentional lane gaps. Repeat on white and black hosts.

Do not change Android touch-security settings to obtain a pass. On failure redesign perimeter windows before release.

## SP02 — Timing and lifecycle (release blocker)

Use three sequences with 30s, 60s and staggered boundaries. Record scheduled/actual boundary timestamps using an instrumented debug build or external recording, separately for screen-on, screen-off, battery saver, forced Doze and OEM restricted modes. Never log private names or Morse text.

For every mode: compare logical current-step position, notification arrival and physical vibration. A late callback must anchor subsequent steps to original deadlines. Do not infer vibration delivery from a method call.

Exercise Activity recreation, background process kill, task removal, Task Manager stop, Settings force-stop, reboot and package update. Same boot must retain logical remaining time; reboot/user-requested stop must not silently relaunch timers. Pause exactly before/at/after a boundary, adjust during a boundary, reset and immediately restart, then deliver the earlier alarm.

Revoke overlay and alarm access during a run. Return to Halo and confirm graceful recovery and accurate degraded-mode messaging.

## SP03 — Input

Enable local volume keys and verify ±30s on tap, ±60s after 3s, ±300s after 8s, no repeats after 15s. Check release, focus loss, tab change, selected row change, pause, rotation, lock, disabled toggle and simultaneous keys. Normal volume behavior must return when disabled/outside Halo. Global interception is not shipped and must not be marked passed.

## Haptics and accessibility

Test C, E T, SOS and FLIP on real motors. Complete A/B/C together; stop/dismiss one while another is queued. Stop all must silence all pending work. Test no-vibrator emulator and vibration disabled/DND.

With TalkBack and 200% font scale, edit name, duration, seconds carry/borrow, choose a row, add/remove a step, launch/pause/reset, hide/restore and change theme. There must be no every-second spoken countdown. Inspect expanded targets and overlay text wrapping. Ensure all required actions remain reachable without color or drag gestures.

## Release gate

Attach evidence for R01–R24 to STATUS.md. Resolve failures, profile frame pacing/CPU/battery against an idle baseline, verify store declarations and production signing, and review original UI screenshots with the owner. The presence of a debug APK is not release approval.


## Alpha 03 dock and alerts regression

- Long-press either dock until four action blobs appear. Slide to each target and release: Play resumes, Pause pauses, Restart starts the first step, Dismiss removes that timer and its haptics. Release between/outside targets to cancel; drag before the hold threshold to move/expand normally.
- Confirm the stacked Poppins MM/SS ink is centered in the visible half on left and right. Inspect larger display/font settings, long sequence labels and all three simultaneous docks.
- Compare the compact pill text and icon colors. Check pale line-colored glass on bright/dark backgrounds and with system blur disabled.
- Confirm Breathe eases through inhale, peak, longer exhale and the cycle seam; observe at least three cycles after actual completion. Other completion modes must wrap continuously even when system transition animations are disabled.
- With menu-entry dismissal off, opening Halo only hides controls. With it on, opening Halo clears running, paused and completed sessions plus Morse/repeated vibration. Definitions remain available for the next run.


## Alpha 04 fluid controls

- Confirm the pill and dock share their color tint for red, blue, purple and a custom color. The bar must show only MM:SS, with fixed digit widths during changes such as 01:11 → 01:10.
- Dock and expand repeatedly from both sides. The glass should merge/split without a flash, duplicate controls, lost countdown or invisible window remaining after opening Halo/Stop all.
- Hold the dock: four disconnected bubbles should spring to equally spaced arc positions. Slide and release to select; release outside to cancel. Test near the top/bottom display limits as well as the center.
- Enable Reduce motion and repeat: controls should switch immediately and the action bubbles should appear at their final positions.
- Scroll both light and dark menus; cards should pass under the softly elevated persistent header, while the header action targets remain clickable.
