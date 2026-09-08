# Native alpha validation status

Date: 2026-09-08. Initial implementation against the product plan. This report deliberately separates code coverage from observed Android behavior.

| Area | Implementation | Evidence/status |
|---|---|---|
| R01–R03, R06–R08: three tracks, identity, activation, sequences, independent commands | Pure engine + native editor | 29 JVM tests passed (0 failures), including 2,000 randomized command operations |
| R04–R05: two-field MM:SS, carry/borrow | Text, drag, wheel and accessible increment/decrement | Arithmetic tests added; native interaction/font-scale checks pending |
| R09–R12: lines, glass, hide/restore | Full-display edge surface, 2dp lane gaps, light glass and persisted half-circle docks | Android 15 bounds/gaps/touch-through regression passed; physical-device matrix pending |
| R13: four visual modes | Native path renderer | Native visual QA pending |
| R14–R16: silent alerts, Morse, contention | Silent channels, encoder and bounded queue | 5 Morse tests passed; queue/hardware delivery gate pending |
| R17–R18: volume controls | App-local keys; fixed-target repeat/cancel; floating playback controls | Threshold tests added; physical-key checks pending; global capture not shipped |
| R19: themes | System/light/dark with DataStore persistence | Native screenshot/contrast review pending |
| R20: process recovery | Durable Room checkpoint and boot/user-stop policy | Serialization and engine recovery tests added; Room/lifecycle device checks pending |
| R21–R23: sleep, permissions, stop | Alarm fallback, runtime lifecycle, cancellation | Hardware/OS matrix pending; no deep-idle precision guarantee |
| R24: accessibility | Labels, 48dp actions, scrolling main layout, reduced motion | TalkBack/200% font and keyboard/RTL checks pending |

## Build evidence

- Local `git diff --check`: passed at initial review.
- Local Gradle attempt: blocked before compilation by `java.net.SocketException: Network is unreachable` fetching Gradle 8.13. No Android SDK is installed in the local environment.
- GitHub workflow: JVM tests + Android lint + debug APK compilation. 27 JVM tests passed in run 34206059196. Subsequent Android compilation succeeded; lint identified restricted key-dispatch API calls, replaced with public onKeyDown/onKeyUp hooks. The final build and emulator result are tracked on the PR.

## Remaining product work before a production claim

1. Complete the physical-device protocol and resolve touch, keyboard/corner geometry, OEM idle behavior and accessibility findings.
2. Add Android database recreation/migration and fault-injection tests, validate malformed restored payloads; generated Room v1 schema is committed.
3. Decide and validate global volume capture separately; no accessibility service is currently requested.
4. Measure rendering/CPU/battery performance; physical corner geometry, native background blur and its tint fallback need OEM validation.
5. Verify notification-only long haptic delivery when an alarm wakes a reclaimed process. No unconditional foreground-service restart is used.
6. Add user-facing export of timing diagnostics without private timer text, native screenshot regression coverage, and release localization.
7. Confirm production package identity, signing, current target requirements and distribution declarations. No production signing or store deployment was performed.

## Owner feedback and alpha 02 regression

The owner tested the first debug APK on their phone and reported that the halo and other features worked well. Two display revisions were requested: use the real display perimeter, including system-bar regions, and add gaps between lanes. A third request makes Start return to the previous app/home. Alpha 02 implements these; see ADR-009. This is owner feedback, not a substitute for the complete physical-device matrix.

Alpha 01 had 27 passing JVM tests, a passing Android lint/debug build, and a passing Android 15 emulator smoke test (run 34208242394). Alpha 02 adds attached-window bounds, rendered lane-gap, touch-through and background-launch checks to the emulator test.


The full-display/gap/background-launch regression passed on the Android 15 emulator in run 34210599896, including the persistent header. The subsequent glass/docking change adds a persisted-dock isolation/serialization JVM test and native drag-to-dock, expand, pause and resume checks. These checks passed in run 34212566530; screenshot review then found clipped dock digits, corrected in the final revision below.


## Verified alpha 02 build — d95fcf7

[Run 34213996335](https://github.com/Ezral/HaloTimer/actions/runs/34213996335) passed the JVM test, Android lint, debug APK and Android 15 emulator jobs. The core suite contains 29 passing tests. The native suite reports 1 test, 0 failures, 0 errors and 0 skipped tests.

Native checks cover full-display overlay bounds, three 4dp lane cores separated by transparent 2dp gaps, touch-through, MM:SS adjustment, themes, automatic return to Home, drag-to-dock, drag inward to expand, floating pause/reset/play and editor reset. Eight screenshots were collected. Visual review confirmed stacked dock digits and a label outside the half-circle, light floating controls over Home, the persistent header and corrected dark status-bar icons. Rotation is disabled by the emulator's reduced-animation setting; animated label motion, right-edge docking, gesture-navigation conflicts and native backdrop blur still require physical-device review.

[Download the tested APK artifact](https://github.com/Ezral/HaloTimer/actions/runs/34213996335/artifacts/10051124728). Expanded pills request native background blur only when supported; the dock uses a circular translucent tint and shadow. Full bounds do not override the normal system UI stacking order. The Samsung/OEM idle, haptic, accessibility and production-release gates above remain open.


## Verified alpha 04 UI and runtime — 767ee93

[Run 34237927322](https://github.com/Ezral/HaloTimer/actions/runs/34237927322) passed all 38 JVM tests, Android lint (zero errors), debug assembly and the Android 15 native regression (1 test, zero failures/errors/skips, 56.08 seconds).

The native flow covers full-display bounds, lane gaps, deterministic animation frames and the Orbit seam, MM:SS carry, theme changes, Morse/color/repeat editors, background launch, docking/expanding, long-press Play/Pause and release-to-cancel, floating pause/reset/play, temporary menu hiding, real timer completion with moving edge pixels, dismissal, and the opt-in menu-entry Stop all policy. Screenshots are in [the native artifact](https://github.com/Ezral/HaloTimer/actions/runs/34237927322/artifacts/10061102143).

Screenshot review confirmed the centered mono dock digits, rotating single label, separate evenly spaced action bubbles and the scrolled menu beneath its persistent header. Morph animation feel and OEM blur/hardware vibration still need phone review.

A packaging review found that earlier CI runs did not actually cache AGP's generated debug key. The follow-up change explicitly pins AGP to the keystore path prepared and cached by CI. Screenshot review also normalized the shared bar/dock RGB tint: clipping a two-color gradient to a half-circle had made the same recipe look different. The tested timer and gesture implementations are unchanged. Existing ephemeral-key APKs may require uninstalling before installation, which clears local data. In-place development updates depend on retaining that cache; production signing remains a separate release gate.
