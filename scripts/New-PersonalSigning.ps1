param()
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$signingDirectory = Join-Path $projectRoot 'signing'
$propertiesPath = Join-Path $projectRoot 'signing.properties'
$keyPath = Join-Path $signingDirectory 'voicenotify-personal.p12'
if ((Test-Path -LiteralPath $propertiesPath) -or (Test-Path -LiteralPath $keyPath)) {
    throw 'La firma ya existe. Conserva la clave y signing.properties para poder actualizar el APK. No se sobrescribió ningún archivo.'
}
$keytool = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/keytool.exe' } else { 'keytool' }
New-Item -ItemType Directory -Force -Path $signingDirectory | Out-Null
$randomBytes = New-Object byte[] 32
$generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$generator.GetBytes($randomBytes)
$generator.Dispose()
$password = [Convert]::ToBase64String($randomBytes)
$previousPassword = $env:VOICENOTIFY_KEY_PASSWORD
try {
    $env:VOICENOTIFY_KEY_PASSWORD = $password
    & $keytool -genkeypair -keystore $keyPath -storetype PKCS12 -alias voicenotify-personal -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=VoiceNotify Personal, OU=Personal Android App' '-storepass:env' VOICENOTIFY_KEY_PASSWORD '-keypass:env' VOICENOTIFY_KEY_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo generar la firma personal.' }
    @("storeFile=signing/voicenotify-personal.p12", "storePassword=$password", 'keyAlias=voicenotify-personal', "keyPassword=$password") |
        Set-Content -LiteralPath $propertiesPath -Encoding ascii
    Write-Output 'Firma personal creada. La contraseña no se muestra. Respalda signing/ y signing.properties de forma privada.'
} finally {
    $env:VOICENOTIFY_KEY_PASSWORD = $previousPassword
    [Array]::Clear($randomBytes, 0, $randomBytes.Length)
}
