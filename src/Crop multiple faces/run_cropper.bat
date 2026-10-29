@echo off
cd /d "C:\FaceCropper\src\Crop multiple faces"
del /Q *.class 2>nul

echo Compiling...
"C:\Program Files\Eclipse Adoptium\jdk-8.0.462.8-hotspot\bin\javac.exe" ^
 -cp "C:\FaceCropper\lib\opencv-455.jar;C:\FaceCropper\lib\flatlaf-3.5.jar" ^
 -encoding UTF-8 Crop_multiple_faces.java

if %errorlevel% neq 0 (
    echo.
    echo COMPILATION FAILED!
    pause
    exit /b
)

echo.
echo Starting Perfect Passport Extractor...
echo.
java -cp ".;C:\FaceCropper\lib\opencv-455.jar;C:\FaceCropper\lib\flatlaf-3.5.jar" ^
 -Djava.library.path="C:\FaceCropper\lib" ^
 Crop_multiple_faces

echo.
echo All done!
pause