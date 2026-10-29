@echo off
setlocal
cd /d "%~dp0"
if not exist "lib\opencv-455.jar" (
  echo Missing lib\opencv-455.jar
  exit /b 1
)
if not exist "lib\flatlaf-3.5.jar" (
  echo Missing lib\flatlaf-3.5.jar
  exit /b 1
)
where javac >nul 2>nul
if errorlevel 1 (
  echo Java 8 JDK required. javac not found.
  exit /b 1
)
if not exist build mkdir build
javac -source 8 -target 8 -encoding UTF-8 -cp "lib\opencv-455.jar;lib\flatlaf-3.5.jar" -d build app\src\*.java
if errorlevel 1 exit /b 1
echo Compilation successful.
echo For execution, models\haarcascade_frontalface_alt2.xml and lib\opencv_java455.dll are required.
java -cp "build;lib\opencv-455.jar;lib\flatlaf-3.5.jar" FaceCropPro
