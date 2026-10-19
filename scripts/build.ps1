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
    $version = [regex]::Match((Get-Content -Raw app\build.gradle.kts), 'versionName\s*=\s*"([0-9.]+)"').Groups[1].Value
    $apkPath = Join-Path 'dist' "yanjing-$version.apk"
    Copy-Item -LiteralPath app\build\outputs\apk\release\app-universal-release.apk -Destination $apkPath
    Copy-Item -LiteralPath app\build\outputs\apk\release\app-arm64-v8a-release.apk -Destination (Join-Path 'dist' "yanjing-$version-arm64-v8a.apk")
    Get-FileHash -LiteralPath $apkPath -Algorithm SHA256
} finally { Pop-Location }
