# Halo Timer

A native, quiet Android timer that keeps time in view while you use other apps.

Halo has three independent timer tracks, named sequences, colored edge progress, translucent floating controls, and visual or Morse vibration alerts. Kotlin + Jetpack Compose; no WebView, account, ads, analytics SDK, or runtime network permission.

**Status: internal alpha, not a production-qualified release.** See [validation status](docs/validation/STATUS.md) before relying on background alerts. The [implementation plan](docs/HALO_ANDROID_IMPLEMENTATION_PLAN.md) remains the acceptance baseline.

## Try it

1. Open this project in Android Studio with JDK 17 and Android SDK 36.
2. Select the `app` debug variant and run it on Android 8.0 or newer.
3. Enable **Display over other apps** in Halo's Permissions card for the edge overlay. Notification and precise-alarm access are separately optional.
4. Set a duration, or choose Sequence and add steps. Start a timer: Halo returns to the previous app or home, with the overlay running. Preview keeps settings open.
5. Use the floating pause/play and reset controls, `⋮` for settings, or `×` to hide it. Drag to either edge to dock as a half-circle with stacked minutes/seconds and a rotating label; tap or drag inward to expand. Restore controls from Halo or the ongoing notification.

Alternatively, download `halo-debug-apk` from a successful [Android CI run](https://github.com/Ezral/HaloTimer/actions/workflows/android.yml). CI artifacts require a GitHub login. This APK uses debug signing and the separate package `com.ezral.halo.debug`.

## Build and check

```sh
./gradlew :core:timer:test :app:lintDebug :app:assembleDebug
```

The Gradle wrapper and dependency versions are pinned. JDK 17, Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21, compile/target SDK 36, minimum SDK 26. Dependency downloads require access to Google Maven, Maven Central, and Gradle's distribution service.

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Features

- Three renameable tabs; activate independently; launch selected/all.
- Single timers or up to 50 named steps, including editable pour-over and steak examples.
- Whole minute/second fields with text, vertical drag, mouse wheel, and accessible ± actions. Seconds carry and borrow.
- Current-step progress anchored to monotonic deadlines; pause, resume, adjust, reset, hide and rename preserve track independence.
- Breathe, Orbit, Ping-pong and Double pong; per-track color and glow; reduced motion.
- Silent notifications; vibration off, double tap, or validated A–Z/0–9 Morse, with a bounded serial haptic queue.
- System/light/dark themes; rounded Halo styling and bundled Poppins typography.
- Optional physical volume adjustment **while Halo is focused**: 30s, then 1m after 3s, then 5m after 8s. A hold ends after 15s or when the target changes.
- Room checkpoints include definitions, immutable run snapshots and event outbox atomically; DataStore preferences. Reboot/user-requested stop interrupts a running session.

## Boundaries of this alpha

Global volume-key interception is deliberately not packaged: ordinary overlays cannot provide it, and an AccessibilityService needs a separate device and distribution gate. The app does not request Accessibility access.

Exact-alarm access does not guarantee every short sequence vibration during deep idle. A late callback reconciles the correct logical step without extending the sequence. Normal screens may support overlays; protected screens and system UI may hide or cover them. No permanent wake lock or security-setting workaround is used.

Hardware touch safety, Samsung power behavior, 200% font scale, haptic feel and release performance have not yet been certified. Floating controls always use light glass, including in dark mode. Expanded pills use Android 12+ background blur when available; compact docks use a circular translucent tint and shadow; unsupported devices or power-saving states use a stronger translucent tint. No screen capture is used.

## Structure

- `core/timer`: pure, serializable state engine, Morse encoder and JVM tests.
- `app/.../data`: atomic Room checkpoint and DataStore preferences.
- `app/.../runtime`: serialized coordinator, service, alarms, notifications and haptic arbitration.
- `app/.../overlay`: one decorative edge window and bounded control windows.
- `app/.../ui`: native Compose editor and themes.
- `docs/adr`: implementation decisions, tradeoffs and release gates.
- `docs/validation`: acceptance mapping and reproducible device protocol.

The provisional production application ID is `com.ezral.halo`; confirm ownership before signing a production build. Release signing is intentionally not configured. Do not distribute a debug key as a production identity.

## Alpha 02 — phone feedback

Start timer, Start all and Stop all live in a persistent top-right header.

The decorative halo now uses full display coordinates, including the status and navigation bar regions, with a 2dp gap between 4dp tracks. On Android 12+, reported physical corner radii shape the border. Starting/resuming sends Halo to the background; preview stays in settings. System UI retains its normal z-order: opaque system surfaces can still cover a normal application overlay.

The first alpha used an ephemeral CI debug signing key. Android may require a one-time uninstall of alpha 01 before installing this build; uninstalling clears local timers/settings. Subsequent CI builds cache the debug keystore within this review branch to support in-place development updates while that cache is retained. Production signing remains separate and unconfigured.


## Alpha 03 — continuous alerts and controls

Completion lighting continues until Dismiss, with smooth wrapping and pong turns. Vibration (including Morse) supports Once, 3×, 5×, Until dismiss, or a custom cycle count/duration. Parallel timer vibrations take turns; previews play once. Finite repeats do not resume after process loss.

Opening Halo hides floating controls temporarily. Their backgrounds follow the chosen line color, and the dock has one larger bold rotating label with the current sequence step and timer name. The palette adds bright red, strong blue and purple; a custom hex dialog accepts any opaque RGB color. Header actions use icons, Morse editing opens a Save/Cancel popup, and appearance options live at the bottom.

The floating pill is compact, with matching dark text/icons on light color-tinted glass. The dock centers MM/SS inside its visible half. Hold the dock, slide onto Play, Pause, Restart or Dismiss, then release; releasing elsewhere cancels. Restart returns to the first step and runs immediately. Dismiss stops that timer. Tap or pull inward to reveal the standard controls.

The optional **Dismiss all timers on menu entry** setting defaults off. With it on, returning to Halo clears every running/paused/completed session and its alerts; timer definitions remain saved. Breathe uses a 4.8-second cycle with a gentle inhale and longer exhale, easing both line intensity and glow.

Poppins is bundled under the SIL Open Font License; see `app/src/main/assets/licenses/Poppins-OFL.txt`. `.github/scripts/fetch-fonts.py` verifies the upstream font blobs against pinned hashes.


## Alpha 04 — fluid controls

The bar and dock share the same tint recipe. The bar shows only the countdown, with JetBrains Mono used for all in-app MM:SS digits; Poppins remains the menu typeface. Docking/undocking morphs the glass between the pill and its edge bubble. Long-press actions pop onto equal-angle points around the dock with no connecting lines. Reduced motion skips these transitions. The persistent menu header casts a soft shadow over scrolling cards.

JetBrains Mono is bundled unchanged under OFL; its license is in `app/src/main/assets/licenses/JetBrainsMono-OFL.txt`.


CI now explicitly points AGP at the cached development keystore. Earlier builds did not actually save that key, so Android may reject an in-place update from those APKs. A one-time uninstall clears local timers/settings; subsequent builds can update in place while the development-key cache is retained. Durable production signing is still a separate release task.

### Alpha05 motion refinement

Flat tinted floating surfaces with no circular progress border; compact Primary/Reset/Hide controls; faster vsync-driven dock labels with visible-arc looping; 240 ms docking morphs; live edge attachment while dragging; and retracting three-action dock menus. A full circle shown while pulling the dock uses the full orbit. Marketing recordings are now explicitly launched through the Marketing capture workflow, rather than every test edit.
