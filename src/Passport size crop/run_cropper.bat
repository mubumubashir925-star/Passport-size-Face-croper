@echo off
cd /d "C:\FaceCropper\src\Passport size crop"

:: Remove previous class files
del /Q *.class

:: Compile the Java code
"C:\Program Files\Eclipse Adoptium\jdk-8.0.462.8-hotspot\bin\javac.exe" -cp "C:\FaceCropper\lib\opencv-455.jar;C:\FaceCropper\lib\flatlaf-3.5.jar" PassportCropper.java

:: Run the Java program
java -cp ".;C:\FaceCropper\lib\opencv-455.jar;C:\FaceCropper\lib\flatlaf-3.5.jar" -Djava.library.path="C:\FaceCropper\lib" PassportCropper