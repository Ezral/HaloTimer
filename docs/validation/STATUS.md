# Native alpha validation status

Date: 2026-09-08. Initial implementation against the product plan. This report deliberately separates code coverage from observed Android behavior.

| Area | Implementation | Evidence/status |
|---|---|---|
| R01–R03, R06–R08: three tracks, identity, activation, sequences, independent commands | Pure engine + native editor | 27 JVM tests passed (0 failures), including 2,000 randomized command operations |
| R04–R05: two-field MM:SS, carry/borrow | Text, drag, wheel and accessible increment/decrement | Arithmetic tests added; native interaction/font-scale checks pending |
| R09–R12: lines, glass, hide/restore | Shared edge surface + bounded draggable controls + app/notification restoration | Device touch/geometry gate pending |
| R13: four visual modes | Native path renderer | Native visual QA pending |
| R14–R16: silent alerts, Morse, contention | Silent channels, encoder and bounded queue | 5 Morse tests passed; queue/hardware delivery gate pending |
| R17–R18: volume controls | App-local keys; fixed-target repeat/cancel; floating ± taps | Threshold tests added; physical-key checks pending; global capture not shipped |
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
4. Measure rendering/CPU/battery performance; fixed corner radius and tinted glass are provisional native defaults.
5. Verify notification-only long haptic delivery when an alarm wakes a reclaimed process. No unconditional foreground-service restart is used.
6. Add user-facing export of timing diagnostics without private timer text, native screenshot regression coverage, and release localization.
7. Confirm production package identity, signing, current target requirements and distribution declarations. No production signing or store deployment was performed.

## Owner feedback and alpha 02 regression

The owner tested the first debug APK on their phone and reported that the halo and other features worked well. Two display revisions were requested: use the real display perimeter, including system-bar regions, and add gaps between lanes. A third request makes Start return to the previous app/home. Alpha 02 implements these; see ADR-009. This is owner feedback, not a substitute for the complete physical-device matrix.

Alpha 01 had 27 passing JVM tests, a passing Android lint/debug build, and a passing Android 15 emulator smoke test (run 34208242394). Alpha 02 adds attached-window bounds, rendered lane-gap, touch-through and background-launch checks to the emulator test.
