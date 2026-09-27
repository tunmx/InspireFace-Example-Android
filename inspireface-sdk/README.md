# InspireFace SDK integration — 1.2.4

This Android library combines the supplied native SDK with the published base Java API
and model assets. The application depends only on this module.

| Component | Source |
| --- | --- |
| `src/main/jniLibs/*/libInspireFace.so` | `inspireface-android-1.2.4/lib/`, three ABIs |
| `FaceCapture*` and `FaceDetectionSnapshot` Java sources | `inspireface-android-1.2.4/java/`, unchanged |
| `CustomParameter.java` | Base Java 1.2.0, extended with integer pose/emotion fields and fluent setters required by 1.2.4 JNI |
| `FaceBasicToken.java` | 1.2.4 JNI layout: owned `byte[] data` and `int size`, replacing the old native handle |
| Other base Java classes | JitPack `com.github.HyperInspire:inspireface-android-sdk:1.2.0:sources@jar`, extracted during `preBuild` |
| `assets/inspireface/` | Base 1.2.0 AAR, extracted during `preBuild`; includes Pikachu and Megatron |

The base AAR is a build input only. Its compiled classes and native libraries are not
dependencies of the app, preventing duplicate classes or accidental loading of 1.2.0.
The retained legacy `enableDetectModeLandmark` Java field has no effect in 1.2.4:
landmarks are now enabled internally in all modes. Do not reuse its former `0x200` bit,
which now enables pose estimation.

The original native package's build metadata and checksums are in `sdk-info/`.
Generated verification reports and local migration notes are ignored by Git.
`sdk-info/README.md` is an English translation of the package documentation;
`sdk-info/SHA256SUMS` retains the paths and hashes from the original distribution,
including its original README. `SDK-SHA256SUMS` verifies the imported SDK files at
their paths in this module:

```sh
cd inspireface-sdk
shasum -a 256 -c SDK-SHA256SUMS
```

To verify the app integration:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :inspireface-sdk:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
# Requires a connected ARM or x86_64 Android device/emulator:
./gradlew :app:connectedDebugAndroidTest
```

The instrumented smoke tests check the loaded native version, bundled models, landmark
output in all three detection modes, recognition, attribute/liveness JNI calls, and the
capture/snapshot extensions. Packaging and host tests do not replace device inference
tests or front/rear-camera liveness checks.

The host-side `JniDataContractTest` also checks the token and parameter field descriptors
looked up by JNI. A Java build alone cannot catch missing JNI fields: the old token class
has only `handle`/`size`, which causes `NoSuchFieldError: data [B` and a native abort as soon
as tracking detects a face with the 1.2.4 binary.
