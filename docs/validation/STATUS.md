# Native alpha validation status

Date: 2026-09-08. Initial implementation against the product plan. This report deliberately separates code coverage from observed Android behavior.

| Area | Implementation | Evidence/status |
|---|---|---|
| R01–R03, R06–R08: three tracks, identity, activation, sequences, independent commands | Pure engine + native editor | JVM tests added; CI result pending |
| R04–R05: two-field MM:SS, carry/borrow | Text, drag, wheel and accessible increment/decrement | Arithmetic tests added; native interaction/font-scale checks pending |
| R09–R12: lines, glass, hide/restore | Shared edge surface + bounded draggable controls + app/notification restoration | Device touch/geometry gate pending |
| R13: four visual modes | Native path renderer | Native visual QA pending |
| R14–R16: silent alerts, Morse, contention | Silent channels, encoder and bounded queue | Encoder tests added; queue/hardware delivery gate pending |
| R17–R18: volume controls | App-local keys; fixed-target repeat/cancel; floating ± taps | Threshold tests added; physical-key checks pending; global capture not shipped |
| R19: themes | System/light/dark with DataStore persistence | Native screenshot/contrast review pending |
| R20: process recovery | Durable Room checkpoint and boot/user-stop policy | Serialization and engine recovery tests added; Room/lifecycle device checks pending |
| R21–R23: sleep, permissions, stop | Alarm fallback, runtime lifecycle, cancellation | Hardware/OS matrix pending; no deep-idle precision guarantee |
| R24: accessibility | Labels, 48dp actions, scrolling main layout, reduced motion | TalkBack/200% font and keyboard/RTL checks pending |

## Build evidence

- Local `git diff --check`: passed at initial review.
- Local Gradle attempt: blocked before compilation by `java.net.SocketException: Network is unreachable` fetching Gradle 8.13. No Android SDK is installed in the local environment.
- GitHub workflow: JVM tests + Android lint + debug APK compilation. Results are recorded on the PR and in workflow artifacts; pending at the time this report was drafted.

## Remaining product work before a production claim

1. Complete the physical-device protocol and resolve touch, keyboard/corner geometry, OEM idle behavior and accessibility findings.
2. Add Android database recreation/migration and fault-injection tests, export/commit Room v1 schema, and validate malformed restored payloads.
3. Decide and validate global volume capture separately; no accessibility service is currently requested.
4. Measure rendering/CPU/battery performance; fixed corner radius and tinted glass are provisional native defaults.
5. Verify notification-only long haptic delivery when an alarm wakes a reclaimed process. No unconditional foreground-service restart is used.
6. Add user-facing export of timing diagnostics without private timer text, native screenshot regression coverage, and release localization.
7. Confirm production package identity, signing, current target requirements and distribution declarations. No production signing or store deployment was performed.
