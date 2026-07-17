# InspireFace Example — Front-Camera Liveness Detection

[中文文档](README_CN.md)

A real-time front-camera liveness detection example built on the InspireFace Android SDK (1.2.0), with three switchable modes:

- **Silent liveness (RGB anti-spoofing)**: streams a per-frame liveness score for the current face, averages it over a sliding window, and labels the face real/spoof against a threshold.
- **Action liveness (cooperative)**: generates a random challenge sequence (blink / head shake / mouth open / head raise), prompts each action in turn, and detects completion with per-action timeouts and face-loss failure handling.
- **Pose recognition**: displays whatever action you perform — the latest action in large text, with a fading row of smaller history entries below (at most 6 shown; rising-edge debounced, so a held pose is recorded once).

Debug aids (top of the screen):

- **Euler angles** switch: live Yaw / Pitch / Roll readout for the tracked face (~10 Hz; note that in the 1.2.0 JNI only `angles[0]` is trustworthy, so with multiple faces the first face is shown).
- **Landmarks** switch (temporarily hidden — commented out in `activity_liveness.xml`, the Java wiring is null-guarded; uncomment to restore): renders the 106-point dense landmarks with OpenGL ES 2.0 (`LandmarkGlView`) — a transparent GLSurfaceView overlay, one `GL_POINTS` draw call per frame, shader-side coordinate mapping, `RENDERMODE_WHEN_DIRTY` on-demand rendering, zero cost while hidden.

Language: the UI defaults to **English** regardless of the system language; the bottom-right chip switches between English and Chinese (persisted across launches via per-app locales).

## Architecture

```
CameraX ImageAnalysis (YUV_420_888, 640x480, KEEP_ONLY_LATEST)
   └─ FaceAnalyzer (single-threaded analysis executor)
        ├─ Nv21Converter.convert()        YUV_420_888 → tight NV21 (VU interleave probed once, then bulk-copied)
        ├─ Nv21Converter.rotateUpright()  Java-side rotation to upright
        ├─ CreateImageStreamFromByteBuffer(nv21, CAMERA_ROTATION_0)
        ├─ ExecuteFaceTrack               LIGHT_TRACK mode
        ├─ LivenessController.onFrame()   per-mode MultipleFacePipelineProcess + state machine
        └─ ReleaseImageStream             released within the same frame
```

- `liveness/LivenessActivity` — camera binding, permissions, UI (mode toggle / prompt card / FPS chip / language toggle)
- `liveness/FaceAnalyzer` — per-frame NV21 conversion, tracking, perf stats, debug readouts
- `liveness/LivenessController` — the state machines for all three modes (tunables live at the top of this class)
- `liveness/Nv21Converter` — fast YUV→NV21 conversion + NV21 rotation
- `liveness/FaceOverlayView` — face bracket overlay (center-crop mapping + front mirror)
- `liveness/LandmarkGlView` — OpenGL landmark overlay
- `liveness/FaceEngine` — GlobalLaunch and session creation (Pikachu lightweight model pack)
- `LocalePrefs` / `App` — per-app language selection (English default)

## Key design decisions (verified against SDK source)

1. **Pre-rotate NV21 on the Java side and always pass `CAMERA_ROTATION_0`.**
   The SDK (≤1.2.3) crops RGB-liveness input using "the rotated upright full frame + an
   un-rotated face rect", so passing 90/270 rotation constants misplaces the crop and
   corrupts silent-liveness scores. Pre-rotating (~1–2 ms at 640×480) sidesteps that
   entirely, and every SDK output coordinate lands directly in display orientation, so
   the overlays only need the front-camera mirror. Also note the SDK's rotation
   constants are the *opposite* of Android's `rotationDegrees` (Android 90 → SDK
   ROTATION_270); pre-rotation avoids that trap too.

2. **Action liveness has three hard prerequisites** (miss one and actions never fire):
   - `DETECT_MODE_LIGHT_TRACK` (other modes rebuild tracked faces every frame, so the
     temporal action window never accumulates);
   - `enableInteractionLiveness` at session creation;
   - `enableFaceQuality` at session creation (loads the pose model — without it yaw/pitch
     stay 0 and shake/head-raise can never trigger).

3. **Action flag semantics** (SDK-internal 10-frame sliding window + rules):
   blink is a one-call pulse (the window resets after it); shake latches while both yaw
   extremes sit in the rolling window (~10 calls); mouth-open/head-raise are
   level-triggered. The controller therefore uses **edge gates**: each challenge step
   must observe the flag at 0 before a 1 counts, and the SDK's `normal` flag (warm-up
   indicator, also raised for ~9 calls after every blink-induced reset) quarantines the
   placeholder zeros so a pose held through a natural blink is neither double-counted
   (pose mode) nor accepted as fresh (action mode).

4. **`CreateImageStreamFromByteBuffer` does not copy** — the native stream aliases the
   byte[] until `ReleaseImageStream`. Safe pattern: create → track → pipeline → release
   within one frame, never overwriting the byte[] before release (this implementation
   reuses two persistent buffers on a single thread, which satisfies that naturally).

5. **Silent liveness**: single-frame score with the author-encoded 0.88 decision
   boundary; every pipeline call converts the full frame internally (a known SDK hot
   spot), so the pipeline runs every 2nd frame with an 8-sample sliding average — same
   accuracy, half the cost.

6. **`MultipleFaceData.angles[i]` is only valid for `i == 0`** (a 1.2.0 JNI bug writes
   face[0]'s angles into every slot); this app only reads it in single-face flows.

## Tunables

At the top of `LivenessController`:

| Parameter | Default | Meaning |
|---|---|---|
| `RGB_LIVENESS_THRESHOLD` | 0.88 | silent-liveness real/spoof boundary |
| `SCORE_WINDOW` | 8 | sliding average window for scores |
| `SILENT_PIPELINE_INTERVAL` | 2 | run the anti-spoofing pipeline every N frames |
| `ACTIONS_PER_RUN` | 3 | challenge actions per round |
| `ACTION_TIMEOUT_MS` | 8000 | per-action timeout |
| `MIN_FACE_WIDTH_RATIO` | 0.18 | minimum face width as a fraction of frame width |
| `POSE_HISTORY_MAX` | 6 | pose-mode entries shown (1 large + 5 history) |

## Requirements

- JDK 17 (required by AGP 8.5.1; Android Studio's embedded JDK works)
- Android Studio Koala+ — the Gradle 8.7 wrapper is committed, no local Gradle needed
- Network access to `google()`, `mavenCentral()` and `jitpack.io` on first sync
  (the InspireFace SDK and its bundled model packs resolve from JitPack)
- An ARM Android device, minSdk 24 (the SDK ships arm64-v8a / armeabi-v7a only —
  x86 emulators won't run it)

`local.properties` is intentionally not committed; Android Studio regenerates it, or set
`ANDROID_HOME` for command-line builds.

## Running

The first launch is slower while the model pack is unpacked from assets.

```bash
./gradlew :app:installDebug
```

Looking for the wider SDK surface (recognition, FeatureHub search)? See the upstream
[InspireFace](https://github.com/HyperInspire/InspireFace) Android example — the original
smoke-test activity was removed from this repo (recoverable from git history).
