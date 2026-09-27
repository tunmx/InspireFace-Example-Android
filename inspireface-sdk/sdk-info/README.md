# InspireFace Android Native SDK 1.2.4

This package was built from the source workspace in Release mode using Android
NDK r28b (28.1.13356709). The minimum native API level is 21 (Android 5.0).

## Contents

- `lib/arm64-v8a/libInspireFace.so`
- `lib/armeabi-v7a/libInspireFace.so`
- `lib/x86_64/libInspireFace.so`
- `include/`: Installed C/C++ headers. Applications can access the public C API
  through `inspireface.h`.
- `java/`: FaceCapture, FaceDetectionSnapshot and related Java extension sources
  provided by the repository.
- `version.txt`, `build-info.json`: Version and build information.
- `verification.json`: Generated binary verification results (kept locally).
- `SHA256SUMS`: Checksums for files in the original distribution.

## Usage

Copy the required architecture directories and `.so` files to the Android app's
`src/main/jniLibs/` directory. Add `include/` to the native header search path and
link against `libInspireFace.so`. For JNI, the Java SDK calls
`System.loadLibrary("InspireFace")`.

This build uses the MNN CPU backend. MNN and the C++ runtime are statically linked
into the shared library. CUDA, RKNN, OpenCL, Vulkan and OpenCV are disabled.
The application must provide model packs separately and pass their file paths
during initialization.

The project's default build script produces a native SDK package, not an AAR.
The `java/` directory contains only the extension APIs maintained in this
repository. They depend on base Java SDK classes such as
`com.insightface.sdk.inspireface.base.Session` and `ImageStream`, and cannot be
compiled independently as a complete Java SDK.

## Verification scope

Package verification covered each architecture's ELF machine type, public C API
and JNI exports, dynamic dependencies, and 16 KB LOAD segment alignment. A C API
usage example was compiled and linked with the NDK for each architecture. The
repository's Java/JNI capture API consistency checks were also run.
Package verification did not include Android device or emulator execution, or
model inference. The application build remains responsible for final APK alignment.

## Rebuilding

Run the following from the SDK source repository root, with `ANDROID_NDK` pointing
to the local NDK r28b directory:

```sh
ANDROID_NDK=/path/to/android-ndk-r28b VERSION=1.2.4 bash -e command/build_android.sh
```

The original script did not retain the Java extension sources when assembling the
installation directory. This package additionally includes all `.java` files from
`cpp/inspireface/platform/jni/java/`. Build logs and the C verification examples are
stored in the source workspace's `build/android-sdk-logs/` directory.
