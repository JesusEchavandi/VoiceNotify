param()
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$sourcePath = Join-Path $projectRoot 'icono.jpg'
if (-not (Test-Path -LiteralPath $sourcePath)) { throw 'Falta icono.jpg en la raíz del proyecto.' }
Add-Type -AssemblyName System.Drawing
$sourceImage = [Drawing.Image]::FromFile($sourcePath)
try {
    if ($sourceImage.Width -ne $sourceImage.Height) { throw 'icono.jpg debe ser cuadrado para conservar el diseño sin recortes.' }
    $targets = @{
        'mipmap-mdpi' = 48
        'mipmap-hdpi' = 72
        'mipmap-xhdpi' = 96
        'mipmap-xxhdpi' = 144
        'mipmap-xxxhdpi' = 192
        'drawable-nodpi' = 512
    }
    foreach ($target in $targets.GetEnumerator()) {
        $directory = Join-Path $projectRoot "app/src/main/res/$($target.Key)"
        New-Item -ItemType Directory -Force -Path $directory | Out-Null
        $bitmap = [Drawing.Bitmap]::new($target.Value, $target.Value)
        try {
            $graphics = [Drawing.Graphics]::FromImage($bitmap)
            try {
                $graphics.Clear([Drawing.Color]::White)
                $graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                $graphics.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::HighQuality
                $graphics.DrawImage($sourceImage, [Drawing.Rectangle]::new(0, 0, $target.Value, $target.Value))
            } finally { $graphics.Dispose() }
            $fileName = if ($target.Key -eq 'drawable-nodpi') { 'ic_launcher_art.png' } else { 'ic_launcher.png' }
            $destination = Join-Path $directory $fileName
            $bitmap.Save($destination, [Drawing.Imaging.ImageFormat]::Png)
            if ($target.Key -ne 'drawable-nodpi') {
                Copy-Item -LiteralPath $destination -Destination (Join-Path $directory 'ic_launcher_round.png')
            }
        } finally { $bitmap.Dispose() }
    }
    Write-Output 'Íconos Android generados desde icono.jpg; la imagen original se conserva.'
} finally { $sourceImage.Dispose() }
