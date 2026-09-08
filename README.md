# Halo Timer

A native, quiet Android timer that keeps time in view while you use other apps.

Halo has three independent timer tracks, named sequences, adjacent colored edge progress, translucent floating controls, and visual or Morse vibration alerts. Kotlin + Jetpack Compose; no WebView, account, ads, analytics SDK, or runtime network permission.

**Status: internal alpha, not a production-qualified release.** See [validation status](docs/validation/STATUS.md) before relying on background alerts. The [implementation plan](docs/HALO_ANDROID_IMPLEMENTATION_PLAN.md) remains the acceptance baseline.

## Try it

1. Open this project in Android Studio with JDK 17 and Android SDK 36.
2. Select the `app` debug variant and run it on Android 8.0 or newer.
3. Enable **Display over other apps** in Halo's Permissions card for the edge overlay. Notification and precise-alarm access are separately optional.
4. Set a duration, or choose Sequence and add steps. Start a timer, then switch to another app.
5. Tap the floating time to pause/resume, drag it to move, use `⋮` for settings, or `×` to hide it. Restore controls from Halo or the ongoing notification.

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
- System/light/dark themes; original rounded Halo styling and monospace floating digits.
- Optional physical volume adjustment **while Halo is focused**: 30s, then 1m after 3s, then 5m after 8s. A hold ends after 15s or when the target changes. Floating ± controls work across apps.
- Room checkpoints include definitions, immutable run snapshots and event outbox atomically; DataStore preferences. Reboot/user-requested stop interrupts a running session.

## Boundaries of this alpha

Global volume-key interception is deliberately not packaged: ordinary overlays cannot provide it, and an AccessibilityService needs a separate device and distribution gate. The app does not request Accessibility access.

Exact-alarm access does not guarantee every short sequence vibration during deep idle. A late callback reconciles the correct logical step without extending the sequence. Normal screens may support overlays; protected screens and system UI may hide or cover them. No permanent wake lock or security-setting workaround is used.

Hardware touch safety, Samsung power behavior, 200% font scale, haptic feel and release performance have not yet been certified. Floating controls use a translucent tint, not screen capture or guaranteed cross-app blur.

## Structure

- `core/timer`: pure, serializable state engine, Morse encoder and JVM tests.
- `app/.../data`: atomic Room checkpoint and DataStore preferences.
- `app/.../runtime`: serialized coordinator, service, alarms, notifications and haptic arbitration.
- `app/.../overlay`: one decorative edge window and bounded control windows.
- `app/.../ui`: native Compose editor and themes.
- `docs/adr`: implementation decisions, tradeoffs and release gates.
- `docs/validation`: acceptance mapping and reproducible device protocol.

The provisional production application ID is `com.ezral.halo`; confirm ownership before signing a production build. Release signing is intentionally not configured. Do not distribute a debug key as a production identity.
