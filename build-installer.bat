@echo off
setlocal
cd /d "%~dp0"
set "FAILED=0"
if not exist "runtime\bin\javaw.exe" (echo MISSING: runtime\bin\javaw.exe & set "FAILED=1")
if not exist "runtime\bin\java.exe" (echo MISSING: runtime\bin\java.exe & set "FAILED=1")
if not exist "lib\opencv-455.jar" (echo MISSING: lib\opencv-455.jar & set "FAILED=1")
if not exist "lib\flatlaf-3.5.jar" (echo MISSING: lib\flatlaf-3.5.jar & set "FAILED=1")
if not exist "lib\opencv_java455.dll" (echo MISSING: lib\opencv_java455.dll & set "FAILED=1")
if not exist "models\haarcascade_frontalface_alt2.xml" (echo MISSING: face model & set "FAILED=1")
if not exist "models\haarcascade_eye.xml" (echo MISSING: eye model & set "FAILED=1")
if not exist "build\FaceCropPro.class" (echo MISSING: compiled application & set "FAILED=1")
if "%FAILED%"=="1" (
 echo Installer cannot be built until all dependencies are staged.
 exit /b 1
)
where ISCC.exe >nul 2>nul
if errorlevel 1 (
 echo Inno Setup ISCC.exe not on PATH. Install a Windows 7 compatible Inno Setup release.
 exit /b 1
)
ISCC.exe installer.iss
if errorlevel 1 exit /b 1
echo Installer created in installer-output
