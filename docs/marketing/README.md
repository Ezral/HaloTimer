# Marketing captures

Run the **Marketing capture** workflow to record the unmodified native Halo UI on an Android 15 Pixel 6 emulator. The dedicated instrumentation argument prevents capture work in normal smoke tests.

The separate test APK includes an original, clearly labelled sample recipe app. It is a backdrop for demonstrating real application overlays, not a Halo feature and not shipped in the app. The recordings also switch to Android Settings to demonstrate cross-app behavior. All countdowns, alerts, docks and menus are rendered by Halo's production implementation. No timer animation is composited onto the capture.

Outputs: dark/light/sequence menu screenshots, three parallel overlays, dock/actions/glass screenshots, and real-time MP4s of parallel timers, docking and all four completion alert styles. Sound is intentionally absent; vibration cannot be conveyed by screen recording. Android system elements keep their normal z-order.

The capture fixture creates sample data only in the disposable emulator. It never accesses a user's phone or changes production app behavior. Compiled test activities are excluded from shipping APKs.

## Campaign layouts

After downloading the capture artifact, run:

```bash
python3 docs/marketing/render-pack.py /path/to/marketing-captures /path/to/deliverables
```

Requires Python with Pillow and NumPy, plus FFmpeg. Produces six portrait PNGs, a contact sheet, and four silent 1080 × 1920 H.264 videos. The native screen remains complete and proportional; only external headlines and a frame are composited. Keep original captures alongside finished assets. Do not imply that the sample recipe app is a Halo feature, or that screen recordings demonstrate vibration.

## Validated capture

The final capture run [34244527124](https://github.com/Ezral/HaloTimer/actions/runs/34244527124) passed at source `2dba2198fe664c260fdcc6ecbfbe33fae463b677`. The fixture verifies all three tracks are running and exercises dock, pause and expand gestures. Output artifact `10063697689` contains 12 native PNGs and six raw MP4s. The test-only backdrop uses Java so it runs as a separate APK without relying on the instrumented app's Kotlin runtime. Menu captures wait for Compose to redraw before capture; animated overlay captures use native screen recording without waiting for UI idleness.

Reviewed the actual three-track overlays, both menu themes, sequence editor, dock menu and sampled video frames. The campaign renderer normalizes video timestamps and can hold the final frame briefly to reach an exact clip duration. It does not speed up the timer or synthesize its animations.
