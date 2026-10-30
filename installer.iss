; Inno Setup 6 installer template. Build on Windows after running run-windows.bat.
; Self-contained x64 installer: requires staging runtime\\bin\\javaw.exe, DLL and XML models.
#define AppName "Face Crop Pro"
#define AppVersion "0.1.0"
[Setup]
AppId={{7C2D96E8-9E54-4A8B-90A0-53125D7C9C12}
AppName={#AppName}
AppVersion={#AppVersion}
; Windows 7 RTM (6.1.7600) without SP1. Override newer Inno Setup defaults.
MinVersion=6.1
DefaultDirName={autopf}\Face Crop Pro
DefaultGroupName=Face Crop Pro
OutputDir=installer-output
OutputBaseFilename=FaceCropPro-Setup-x64
SetupIconFile=facecrop.ico
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64
Compression=lzma
SolidCompression=yes
[Files]
Source: "facecrop.ico"; DestDir: "{app}"
Source: "build\*"; DestDir: "{app}\build"; Flags: recursesubdirs
Source: "lib\opencv-455.jar"; DestDir: "{app}\lib"
Source: "lib\flatlaf-3.5.jar"; DestDir: "{app}\lib"
Source: "lib\opencv_java455.dll"; DestDir: "{app}\lib"
Source: "models\haarcascade_frontalface_alt2.xml"; DestDir: "{app}\models"
Source: "models\haarcascade_eye.xml"; DestDir: "{app}\models"
Source: "launch.bat"; DestDir: "{app}"
Source: "launch.vbs"; DestDir: "{app}"
Source: "native-folder-picker.ps1"; DestDir: "{app}"
Source: "runtime\\*"; DestDir: "{app}\\runtime"; Flags: recursesubdirs createallsubdirs
[Icons]
Name: "{group}\Face Crop Pro"; Filename: "{sys}\wscript.exe"; Parameters: """{app}\launch.vbs"""; WorkingDir: "{app}"; IconFilename: "{app}\facecrop.ico"
Name: "{autodesktop}\Face Crop Pro"; Filename: "{app}\launch.bat"; WorkingDir: "{app}"; IconFilename: "{app}\facecrop.ico"; Tasks: desktopicon
[Tasks]
Name: desktopicon; Description: "Create a desktop shortcut"
