# Java DEX bridge

This directory contains the Java-side bridge source for the CSR extension. It is not an APK and does not create an Android application.

## JNI class contract

The C++ JNI source registers native methods on the class selected by CMake. This bridge deliberately uses the default-package class name DJLEngine, so the native build must use:

```cmake
set(SDENGINE_JNI ON)
set(SDENGINE_JNI_CLASS "DJLEngine")
```

The nested callback interface must remain named `DJLEngine$ProgressListener`; its method signature is `boolean onProgress(float fraction, String stage)`. These details must match the JNI registration table exactly.

## Runtime contract

1. Lua loads the Java class from its DEX container using the extension's class-loading pattern.
2. Lua calls `DJLEngine.loadNativeLibrary(absoluteSoPath)` from that class.
3. The class calls `System.load(absoluteSoPath)`; JNI_OnLoad registers native methods against DJLEngine.
4. Lua posts `DJLEngine.run(...)` to the dedicated worker HandlerThread, never to the UI thread.
5. Lua reads the status file written atomically by the Java bridge.
6. Lua calls `DJLEngine.cancel(cancelPath)` when the user requests cancellation.

## Input requirements

`rawPath` must be a file containing interleaved float32 PCM. This Java bridge does not decode compressed MP3/M4A/FLAC input. The CSR-side MediaExtractor/MediaCodec path must be adapted to write decoder output as float32 incrementally before calling `run`.

The existing tempo/chord reference decodes audio into short arrays for its analysis. That array path must not be reused to buffer an entire song for source separation.

## Status format

The status file is a single UTF-8 line:

`stage|percent|message|updatedEpochMillis`

The bridge writes to a sibling `.tmp` file and uses Android `Os.rename` for replacement. Lua should tolerate a missing or temporarily unreadable status file and should announce meaningful stage/progress changes only.

## Build notes

Compile `DJLEngine.java` against the Android platform android.jar (API 29 or newer), then convert the class to DEX with the Android build-tools D8 compiler. Package the DEX in the exact container format accepted by the CSR class-loading code. Do not assume whether the reference expects a raw .dex or a JAR/APK wrapper until that loader implementation has been inspected.

Keep the class name and nested interface name unchanged unless the CMake `SDENGINE_JNI_CLASS` value and JNI descriptors are updated together.
