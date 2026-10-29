@echo off
cd /d "%~dp0"
java -cp "build;lib\opencv-455.jar;lib\flatlaf-3.5.jar" FaceCropPro
if errorlevel 1 pause
