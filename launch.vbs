Option Explicit
Dim shell, fso, root, java, cp, cmd
Set shell = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")
root = fso.GetParentFolderName(WScript.ScriptFullName)
java = root & "\runtime\bin\javaw.exe"
If Not fso.FileExists(java) Then
  MsgBox "Bundled Java runtime is missing. Please reinstall Face Crop Pro.", vbCritical, "Face Crop Pro"
  WScript.Quit 1
End If
shell.CurrentDirectory = root
cp = "build;lib\opencv-455.jar;lib\flatlaf-3.5.jar"
cmd = """" & java & """ -cp """ & cp & """ FaceCropPro"
shell.Run cmd, 1, False
