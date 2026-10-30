# Generate a Windows 7 compatible PNG-compressed ICO matching the Java app icon.
Add-Type -AssemblyName System.Drawing
$size = 64
$bitmap = New-Object System.Drawing.Bitmap($size, $size)
$g = [System.Drawing.Graphics]::FromImage($bitmap)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.Clear([System.Drawing.Color]::Transparent)
$bg = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(18,40,64))
$g.FillRectangle($bg, 0, 0, 63, 63)
$cyan = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(41,208,190), 4)
$white = New-Object System.Drawing.Pen([System.Drawing.Color]::White, 2)
$g.DrawLine($cyan,13,26,13,13); $g.DrawLine($cyan,13,13,26,13)
$g.DrawLine($cyan,38,13,51,13); $g.DrawLine($cyan,51,13,51,26)
$g.DrawLine($cyan,13,38,13,51); $g.DrawLine($cyan,13,51,26,51)
$g.DrawLine($cyan,38,51,51,51); $g.DrawLine($cyan,51,38,51,51)
$g.DrawEllipse($white,23,19,18,18)
$g.DrawArc($white,18,30,28,22,15,150)
$g.Dispose()
$stream = New-Object System.IO.MemoryStream
$bitmap.Save($stream,[System.Drawing.Imaging.ImageFormat]::Png)
$png = $stream.ToArray()
$stream.Dispose(); $bitmap.Dispose()
$out = [System.IO.File]::Create('facecrop.ico')
$writer = New-Object System.IO.BinaryWriter($out)
$writer.Write([UInt16]0); $writer.Write([UInt16]1); $writer.Write([UInt16]1)
$writer.Write([Byte]64); $writer.Write([Byte]64); $writer.Write([Byte]0); $writer.Write([Byte]0)
$writer.Write([UInt16]1); $writer.Write([UInt16]32)
$writer.Write([UInt32]$png.Length); $writer.Write([UInt32]22)
$writer.Write($png); $writer.Dispose()
