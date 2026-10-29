# Face Crop Pro

Professional offline passport photo cropping application for Windows.

## Development status
Phase 1: Unified Java desktop application foundation.

## Windows 7 (64-bit) target
- Java 8-compatible source code
- Swing desktop UI
- FlatLaf 3.5 (existing repository dependency)
- OpenCV 4.5.5 (existing Java bindings)
- Bundled Java runtime and native DLLs in the planned installer

**Note:** Windows 7 compatibility and image accuracy have not yet been validated on a Windows 7 machine.

## Build (developer machine)
Requires JDK 8 or compatible Java compiler.

```bat
mkdir build
javac -encoding UTF-8 -cp "lib/flatlaf-3.5.jar" -d build app/src/FaceCropPro.java
java -cp "build;lib/flatlaf-3.5.jar" FaceCropPro
```

The UI foundation opens without OpenCV. Processing is deliberately disabled until the image engine and bundled native dependencies are integrated.

## Roadmap
1. Unified desktop interface
2. Face detection and landmark-aware crop engine
3. Batch processing, multiple faces and manual review
4. Background removal and folder watcher
5. Offline Windows 7 x64 installer with bundled runtime, models and native libraries

Existing source tools under `src/` are preserved unchanged.
