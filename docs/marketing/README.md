# Marketing captures

Run the **Marketing capture** workflow to record the unmodified native Halo UI on an Android 15 Pixel 6 emulator. The dedicated instrumentation argument prevents capture work in normal smoke tests.

The separate test APK includes an original, clearly labelled sample recipe app. It is a backdrop for demonstrating real application overlays, not a Halo feature and not shipped in the app. The recordings also switch to Android Settings to demonstrate cross-app behavior. All countdowns, alerts, docks and menus are rendered by Halo's production implementation. No timer animation is composited onto the capture.

Outputs: dark/light/sequence menu screenshots, three parallel overlays, dock/actions/glass screenshots, and real-time MP4s of parallel timers, docking and all four completion alert styles. Sound is intentionally absent; vibration cannot be conveyed by screen recording. Android system elements keep their normal z-order.

The capture fixture creates sample data only in the disposable emulator. It never accesses a user's phone or changes production app behavior. Compiled test activities are excluded from shipping APKs.
