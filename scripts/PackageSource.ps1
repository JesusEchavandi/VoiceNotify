param()
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$destination = Join-Path $projectRoot 'dist/VoiceNotify-0.1.4-source.zip'
New-Item -ItemType Directory -Force -Path (Join-Path $projectRoot 'dist') | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$entries = @(
    '.gitignore', '.gitattributes', 'LICENSE', 'README.md', 'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties',
    'gradlew', 'gradlew.bat', 'signing.properties.example', 'app/build.gradle.kts', 'app/proguard-rules.pro', 'icono.jpg'
)
$files = @($entries | ForEach-Object { Get-Item -LiteralPath (Join-Path $projectRoot $_) })
foreach ($directory in @('app/src', 'gradle', 'scripts', 'docs')) {
    $files += @(Get-ChildItem -LiteralPath (Join-Path $projectRoot $directory) -File -Recurse)
}
$stream = [IO.File]::Open($destination, [IO.FileMode]::Create)
$archive = [IO.Compression.ZipArchive]::new($stream, [IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($file in $files) {
        $relativePath = $file.FullName.Substring($projectRoot.Length + 1).Replace('\', '/')
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $file.FullName, $relativePath, [IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally { $archive.Dispose(); $stream.Dispose() }
$checksumLines = @()
foreach ($artifactName in @('VoiceNotify-0.1.4-personal-preview.apk', 'VoiceNotify-0.1.4-source.zip')) {
    $artifactPath = Join-Path $projectRoot "dist/$artifactName"
    if (Test-Path -LiteralPath $artifactPath) {
        $checksum = (Get-FileHash -LiteralPath $artifactPath -Algorithm SHA256).Hash.ToLowerInvariant()
        $checksumLines += "$checksum  $artifactName"
    }
}
$checksumLines | Set-Content -LiteralPath (Join-Path $projectRoot 'dist/SHA256SUMS.txt') -Encoding ascii
Write-Output $destination
