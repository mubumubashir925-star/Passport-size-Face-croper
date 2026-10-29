# Standalone Windows x64 installer

The end user should run only `FaceCropPro-Setup-x64.exe`. They should **not** need to install Java, OpenCV or a JDK. The setup includes a private Java runtime and native libraries.

## Build machine (not the customer PC)

1. Use a Windows build PC with a **Java 8 JDK**, and run `run-windows.bat` to compile the app.
2. Place a **legally redistributable, Windows 7 compatible x64 Java 8 JRE** under `runtime/`, so `runtime/bin/javaw.exe` exists. Review the runtime vendor's license before redistribution.
3. Place matching OpenCV **4.5.5 Windows x64** `opencv_java455.dll` in `lib/`. Its architecture must match the bundled Java runtime.
4. Place OpenCV face and eye cascade files in `models/haarcascade_frontalface_alt2.xml` and `models/haarcascade_eye.xml`. The existing Java JARs remain under `lib/`.
5. Install an Inno Setup compiler version compatible with the desired Windows 7 target and add `ISCC.exe` to PATH.
6. Run `build-installer.bat`. It validates required files and produces `installer-output/FaceCropPro-Setup-x64.exe`.

## On the target PC

Run the installer, then open **Face Crop Pro** from the desktop or Start Menu. The shortcut invokes `launch.vbs` which starts the bundled `runtime/bin/javaw.exe` in the application folder. No command prompt should appear. Uninstall using Windows Programs and Features.

## Important limitations

- A compiled and tested EXE has **not** yet been produced by this repository change.
- The runtime, OpenCV native DLL and cascade models are **not currently committed** to this repository; they must be staged before building.
- Test on a real Windows 7 SP1 x64 system, including fresh installs, native DLL loading, crop/save, preview and uninstall.
- Some newer Java 8 distributions or Inno Setup releases may not support Windows 7. Select compatible versions and verify on the actual target OS.
- Avoid installing under a source/output folder; cropped photos should be written to a user-selected output directory.
