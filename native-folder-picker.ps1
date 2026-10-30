$ErrorActionPreference = 'Stop'
$outputFile = if ($args.Count -gt 0) { $args[0] } else { $null }
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
[ComImport, Guid("DC1C5A9C-E88A-4DDE-A5A1-60F82A20AEF7")]
class FileOpenDialog {}
[ComImport, Guid("D57C7288-D4AD-4768-BE02-9D969532D960"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
interface IFileOpenDialog {
    [PreserveSig] int Show(IntPtr parent);
    void SetFileTypes(uint count, IntPtr types);
    void SetFileTypeIndex(uint index);
    void GetFileTypeIndex(out uint index);
    void Advise(IntPtr events, out uint cookie);
    void Unadvise(uint cookie);
    void SetOptions(uint options);
    void GetOptions(out uint options);
    void SetDefaultFolder(IntPtr folder);
    void SetFolder(IntPtr folder);
    void GetFolder(out IntPtr folder);
    void GetCurrentSelection(out IntPtr item);
    void SetFileName([MarshalAs(UnmanagedType.LPWStr)] string name);
    void GetFileName([MarshalAs(UnmanagedType.LPWStr)] out string name);
    void SetTitle([MarshalAs(UnmanagedType.LPWStr)] string title);
    void SetOkButtonLabel([MarshalAs(UnmanagedType.LPWStr)] string text);
    void SetFileNameLabel([MarshalAs(UnmanagedType.LPWStr)] string text);
    void GetResult(out IShellItem item);
}
[ComImport, Guid("43826D1E-E718-42EE-BC55-A1E261C37BFE"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
interface IShellItem {
    void BindToHandler(IntPtr pbc, ref Guid bhid, ref Guid riid, out IntPtr ppv);
    void GetParent(out IShellItem parent);
    void GetDisplayName(uint sigdnName, out IntPtr name);
}
public static class NativeFolderPicker {
    public static string Pick(string title) {
        IFileOpenDialog dlg = (IFileOpenDialog)new FileOpenDialog();
        try {
            uint opts; dlg.GetOptions(out opts);
            dlg.SetOptions(opts | 0x20u | 0x40u); // FOS_PICKFOLDERS | FOS_FORCEFILESYSTEM
            dlg.SetTitle(title);
            if (dlg.Show(IntPtr.Zero) != 0) return null;
            IShellItem item; dlg.GetResult(out item);
            try {
                IntPtr name; item.GetDisplayName(0x80058000u, out name);
                try { return Marshal.PtrToStringUni(name); }
                finally { Marshal.FreeCoTaskMem(name); }
            } finally { Marshal.ReleaseComObject(item); }
        } finally { Marshal.ReleaseComObject(dlg); }
    }
}
'@
$result = [NativeFolderPicker]::Pick('Select Folder')
if ($result) {
    if ($outputFile) { [System.IO.File]::WriteAllText($outputFile, $result, [System.Text.Encoding]::Unicode) }
    [Console]::WriteLine('SELECTED_FOLDER=' + $result)
    else { [Console]::WriteLine($result) }
}
