# Native alpha decisions

Date: 2026-09-08. Scope: first native implementation. These decisions do not mark physical-device spikes as passed.

## ADR-001 — Toolchain and boundaries

Use Kotlin 2.2.21, AGP 8.13.2, Gradle 8.13, JDK 17, SDK 36 and minimum API 26. This is a pinned compatible baseline, not a claim to be the latest SDK/toolchain. AGP's [official compatibility table](https://developer.android.com/build/releases/agp-8-13-0-release-notes) supports this Gradle/JDK combination and API 36. Reassess the applicable target before store submission.

Use one Android module and a separate pure JVM domain module. Compose/Material 3 supplies the native UI; package boundaries isolate platform adapters. Manual application-scoped injection avoids an unnecessary DI plugin for three tracks. The permanent application namespace needs owner confirmation before production signing.

## ADR-002 — Overlay topology

One shared `FLAG_NOT_TOUCHABLE | FLAG_NOT_FOCUSABLE` decorative application-overlay window draws all tracks. Its window alpha is capped at `min(0.75, InputManager.maximumObscuringOpacityForTouch)` on API 31+. One bounded touchable window per glass control contains only the actual control footprint. No full-screen touch listener, accessibility content scraping, screenshot capture or platform security override.

Equal 4dp strokes are separated by exactly 4dp between centerlines, packed by stable track ID. Paths start at top center and advance clockwise. Cached paths are rebuilt for size/lane-count changes. Floating positions persist as normalized coordinates. Software canvas provides bounded glow; initial redraw cap is 30Hz. Screen off removes windows entirely.

The tint fallback is always used. Rounded corner radius is provisional at 28dp. Keyboard/cutout/foldable geometry and overlapping controls require SP01/SP04 device evidence. If touch safety fails, replace the full-display decoration with narrow perimeter windows before release. See [Android untrusted-touch rules](https://developer.android.com/about/versions/12/behavior-changes-all).

## ADR-003 — Foreground runtime

One user-started `specialUse` foreground service owns visible overlay operation. The manifest describes the actual timer/overlay use. This is an internal-build classification subject to Play declaration/review, not a guarantee of acceptance. No media/location type is misused.

Start promptly in the foreground, then load/reconcile state. Stop when there are no running sessions or bounded visual previews/completion alerts. Paused state persists without retaining a perpetual service. Use `START_NOT_STICKY`; ordinary alarm delivery reconciles durable state and does not unconditionally resurrect overlays. A user opens Halo to restore the visual runtime after process termination.

Reference: [service types](https://developer.android.com/develop/background-work/services/fgs/service-types), [background start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start).

## ADR-004 — Alarm tier

Use user-granted `SCHEDULE_EXACT_ALARM`, checked before each schedule, with one elapsed-realtime next-boundary alarm per running track. Fall back to `setAndAllowWhileIdle` on denial/revocation; show degraded capability in settings. No alarm-clock UI semantics are silently introduced. No permanent CPU wake lock.

PendingIntent identities are stable per slot. Alarm callbacks carry no command against a historical session: they only load and reconcile current deadlines. Every semantic mutation reschedules as necessary. A stale alarm therefore cannot reset or expire a newer run.

Logical timing is exact deadline arithmetic. Physical delivery is conditional: idle throttling can postpone closely spaced boundaries and long background haptic queues can be interrupted by process reclamation. Late delivery skips stale intermediate haptics. Do not advertise exact short-sequence alerts in deep idle until SP02 passes. See [AlarmManager](https://developer.android.com/reference/android/app/AlarmManager).

## ADR-005 — Recovery and stopping

Room holds the monotonic deadline and `BOOT_COUNT`. Same-boot recovery reconciles all elapsed boundaries; a changed/unknown boot token changes Running to Interrupted. Wall-clock time never drives countdown arithmetic. On API 30+, user-requested historical process exit also interrupts rather than resumes. `Stop all` cancels alarms, current/pending haptics and completions; service removes windows on its next bounded loop.

Force-stop cannot promise recovery until user reopens the app. Task Manager stop differs from force-stop; the recovery path respects the recorded user exit. Backup is disabled for this first version so live sessions can never migrate to another device. Selective definition-only backup is deferred.

## ADR-006 — Volume scope

Ship app-local input only. The setting explicitly says “Volume buttons in Halo,” defaults off and consumes no keys while disabled. Freeze track/session/step on down, use a 600ms repeat cadence, stop at 15 seconds, and cancel on focus loss, pause, target change, direction chord or key-up. The engine rejects an old step/session target after reconciliation.

The floating ± buttons are the cross-app alternative. They apply one 30s adjustment per tap in this alpha. An optional global adapter remains outside the manifest until a separately consented key-filtering spike is tested with TalkBack, competing services, media/calls, Samsung behavior, and the distribution policy. Do not represent Halo as an accessibility tool solely to obtain key access.

## ADR-007 — Haptics and crash tradeoff

Use an event ID of session/step, scheduled-time ordering and stable slot tie-breaks. Commit the transition and outbox in the same Room checkpoint. Before physical effects, persist outbox consumption. This deliberately chooses at-most-once delivery attempts over duplicate haptics on recovery; a crash in that interval can lose an alert. No exactly-once claim is made.

All real haptic requests enter one bounded queue. Real events preempt previews; cancel/reset only removes the selected track. Drop intermediate events older than five seconds; coalesce overflow into a short cue if budget remains. No repeated waveform. Use `USAGE_ALARM` to let Android apply the user's settings. The native waveform begins with a zero off interval.

## ADR-008 — Atomic persistence

Use Room v1 with a single serialized checkpoint row containing all three definitions, sessions, revisions and outbox. This is a deliberate refinement of the plan's proposed normalized tables: a bounded three-track model fits one atomic record and needs no joins or partial cross-table updates. JSON schema version is independent of Room schema version. DataStore holds only theme, motion and local volume preferences.

Save on semantic changes only; displayed seconds/frames do not write storage. Hold a mutex through state transition, commit and effect handoff. A storage error retains the database and stops further mutation with an interruption message; never fall back to destructive migration or silently overwrite unreadable data. CI exports the Room schema as a report artifact; the generated v1 schema is committed under app/schemas.

Before release, add on-device database recreation/migration tests, bounds/schema validation on restore, and fault injection at persistence/effect boundaries. JVM serialization tests already cover immutable run snapshots and outbox round trips.

## ADR-009 — Full-display decoration and separated lanes (alpha 02)

Owner device feedback confirmed that the timer/features worked but the original overlay frame stopped at the app's usable area. The owner also requested gaps between lines and automatic return to the prior app/home at Start. These supersede the plan's original no-gap preference.

Use `FLAG_LAYOUT_IN_SCREEN`, `setFitInsetsTypes(0)` and `LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS` on API 30+ for the decoration. On API 26–29 use the layout-in-screen/no-limits fallback, with short-edge cutout support on API 28+. Keep the decorative surface non-focusable/non-touchable and below the platform's obscuring-opacity cap. Interactive control windows retain safe-area layout. Keyboard appearance must not shrink the full-display decoration.

Use 4dp strokes with 2dp separation. On API 31+, obtain each physical corner radius from WindowInsets and rebuild paths when those change. The legacy fallback remains 28dp. This changes the layout rectangle, not overlay privileges: system UI and protected screens may still draw above Halo. See [WindowManager.LayoutParams](https://developer.android.com/reference/android/view/WindowManager.LayoutParams) and [rounded corners](https://developer.android.com/develop/ui/views/layout/insets/rounded-corners).

Start/resume commits valid runtime state, requests the foreground service while the Activity is visible, then calls `moveTaskToBack(true)` to reveal the prior task or home. Invalid starts keep the editor open. Preview and Show controls do not minimize the app. No app-history/usage permission is requested.

The emulator smoke test checks the actual attached overlay's origin and full physical dimensions, transparent 2dp gaps in the rendered pixels, a tap through the decorative overlay, and loss of Activity focus after starting. Screenshots include the three-lane full-display overlay and a timer over home. Samsung confirmation of the revised bounds is still needed.

The owner subsequently requested Start timer / Start all / Stop all at the top right. Alpha 02 places these in a persistent toolbar, visible while settings scroll; the selected action becomes Pause/Resume according to state. Duplicate launch/stop controls were removed from the scrolling body.


## ADR 010 — Light glass and edge docking

Floating controls stay light regardless of editor theme. Small non-modal overlay Dialog windows expose the public Android 12+ background blur API; the window shape limits blur to the control. No screen capture or whole-screen blur is used. Capability is checked on runtime ticks, with stronger light tint when unsupported/disabled. Pause/play, reset, menu and hide replace duration adjustment buttons.

Dragging a pill to either side persists a DockSide with normalized position. A 144 dp circle window extends halfway off-screen; its 96 dp glass circle contains stacked minutes/seconds in the visible half, with a rotating name around its rim. Tap or drag inward to expand; long press opens settings. Reduced motion stops label rotation. Docking does not change timer deadlines, and older stored definitions default to undocked.

Reference: https://source.android.com/docs/core/display/window-blurs

Floating reset rewinds the run to its first step and waits for Play, keeping the control visible. Paused runs retain the foreground service and its stop notification so playback remains available over other apps; idle ticks slow to 1 Hz. Editor Reset continues to clear the run for structural editing.

Visual QA found that an inset Window background displaced/clipped dock digits. The pill uses a padding-free background drawable; the dock draws its circular glass tint and shadow directly on a transparent window, keeping the label outside the circle. Native backdrop blur is limited to expanded pills to avoid a rectangular blur footprint surrounding the dock.


## ADR 011 — Continuous completion alerts and repeat policies

Final edge alerts persist until dismissal. Their phase is anchored to the actual step boundary rather than a loop restart; orbit segments cross the closed path seam, and pong reversals use a smooth cosine. The native renderer is tested with distinct frames and immediately before/after the orbit seam, and instrumentation expires a real timer over Home. Canvas alerts follow Halo’s explicit Reduce motion switch. Android’s transition-animation scale no longer silently disables timer alerts or label rotation; this is verified with transition animations disabled in CI.

Haptic policies are Once, 3×, 5×, Until dismiss and Custom (1–99 cycles or 1–3600 seconds). A fair queue submits one unchanged double-tap or Morse waveform at a time, with at least 700 ms between cycles. Parallel tracks take turns. Timed cycles are cut at their deadline. Intermediate until-dismiss requests play once; advancing a sequence cancels its prior step's pending cycles. Reset, rewind, deactivate, vibration Off and Stop all cancel the relevant output. The completion notification and floating × expose Dismiss. Preview plays once. Finite repeat progress is process-local; it is not replayed after process loss. An active until-dismiss final alert can resume with an explicitly restarted runtime, subject to Android vibration policy.

Menu visibility is tracked across activity instances and immediately hides every floating control without changing saved hide/dock preferences. The edge remains visible. Floating backgrounds follow the line color; the dock shows one bold, larger current-step/timer label, clipped only by the physical screen (long labels are ellipsized before they overlap). Expanded labels use the same content. Color changes invalidate the glass tint. Colors include bright red, strong blue and purple, plus an opaque hex color dialog. Palette rows wrap to fit narrow phones.

Header actions are icons with accessibility labels. Appearance sits at the bottom with Follow system, Light and Dark. Morse text is edited in a validated Save/Cancel dialog; the normalized text and Morse symbols update together on Save.
