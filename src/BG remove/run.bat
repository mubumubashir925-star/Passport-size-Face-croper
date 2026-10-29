@echo off
cd /d "C:\FaceCropper\src\BG remove"

:: Remove previous class files
del /Q *.class

:: Compile the Java code
echo Compiling Java code...
"C:\Program Files\Eclipse Adoptium\jdk-8.0.462.8-hotspot\bin\javac.exe" -cp "C:\FaceCropper\lib\opencv-455.jar;C:\FaceCropper\lib\flatlaf-3.5.jar" BatchBackgroundRemover.java
if %ERRORLEVEL% NEQ 0 (
    echo Compilation failed. Check the error messages above.
    pause
    exit /b %ERRORLEVEL%
)

:: Run the Java program
echo Running Java program...
java -cp ".;C:\FaceCropper\lib\opencv-455.jar;C:\FaceCropper\lib\flatlaf-3.5.jar" -Djava.library.path="C:\FaceCropper\lib" BatchBackgroundRemover
if %ERRORLEVEL% NEQ 0 (
    echo Execution failed. Check the error messages above.
    pause
    exit /b %ERRORLEVEL%
)

pause