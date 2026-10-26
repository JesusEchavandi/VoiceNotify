param([switch]$Release, [switch]$DeviceTests)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$previousJavaOptions = $env:JAVA_TOOL_OPTIONS
try {
    # Windows short TEMP paths can break JDK 21 Unix-domain loopback sockets.
    $socketDirectory = Join-Path ([Environment]::GetFolderPath('UserProfile')) '.gradle/voicenotify-sockets'
    New-Item -ItemType Directory -Force -Path $socketDirectory | Out-Null
    $socketPath = $socketDirectory.Replace('\', '/')
    $env:JAVA_TOOL_OPTIONS = "$previousJavaOptions -Djdk.net.unixdomain.tmpdir=$socketPath -Djava.net.preferIPv4Stack=true".Trim()
    Push-Location $projectRoot
    try {
        $tasks = @(':app:testDebugUnitTest', ':app:lintDebug', ':app:assembleDebug')
        if ($Release) {
            if (-not (Test-Path -LiteralPath 'signing.properties')) { throw 'Primero ejecuta scripts/New-PersonalSigning.ps1.' }
            $tasks += @(':app:lintRelease', ':app:assembleRelease')
        }
        if ($DeviceTests) { $tasks += ':app:connectedDebugAndroidTest' }
        & ./gradlew.bat @tasks --console plain --no-daemon
        if ($LASTEXITCODE -ne 0) { throw 'Falló la compilación o una comprobación. Revisa el resultado de Gradle.' }
        if ($Release) {
            New-Item -ItemType Directory -Force -Path 'dist' | Out-Null
            Copy-Item -LiteralPath 'app/build/outputs/apk/release/app-release.apk' -Destination 'dist/VoiceNotify-0.1.4-personal-preview.apk'
            $hash = (Get-FileHash -LiteralPath 'dist/VoiceNotify-0.1.4-personal-preview.apk' -Algorithm SHA256).Hash.ToLowerInvariant()
            "$hash  VoiceNotify-0.1.4-personal-preview.apk" | Set-Content -LiteralPath 'dist/SHA256SUMS.txt' -Encoding ascii
        }
    } finally { Pop-Location }
} finally { $env:JAVA_TOOL_OPTIONS = $previousJavaOptions }
