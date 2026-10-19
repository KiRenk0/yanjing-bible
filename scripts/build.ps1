param([switch]$SkipTests)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    # Use locally trusted Windows root CAs without disabling TLS validation.
    if ($env:OS -eq 'Windows_NT') {
        $env:GRADLE_OPTS = "$env:GRADLE_OPTS -Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE"
    }
    $tasks = @(':app:assembleRelease')
    if (-not $SkipTests) { $tasks += @(':app:testDebugUnitTest', ':app:lintDebug') }
    & .\gradlew.bat @tasks --console plain
    if ($LASTEXITCODE -ne 0) { throw 'Build failed.' }
    New-Item -ItemType Directory -Force -Path dist | Out-Null
    Copy-Item -LiteralPath app\build\outputs\apk\release\app-release.apk -Destination dist\yanjing-1.3.0.apk
    Get-FileHash -LiteralPath dist\yanjing-1.3.0.apk -Algorithm SHA256
} finally { Pop-Location }
