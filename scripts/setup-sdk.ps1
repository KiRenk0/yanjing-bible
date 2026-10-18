$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$sdkRoot = Join-Path $projectRoot '.tools\android-sdk'
$downloads = Join-Path $projectRoot '.tools\downloads'
$sdkManager = Join-Path $sdkRoot 'cmdline-tools\latest\bin\sdkmanager.bat'
New-Item -ItemType Directory -Force -Path $sdkRoot, $downloads | Out-Null
if (-not (Test-Path -LiteralPath $sdkManager)) {
    $archive = Join-Path $downloads 'commandlinetools-win.zip'
    & curl.exe -sSL --fail --retry 3 --max-time 600 -o $archive 'https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip'
    if ($LASTEXITCODE -ne 0) { throw 'Android command-line tools download failed.' }
    $extractRoot = Join-Path $downloads 'commandline'
    Expand-Archive -LiteralPath $archive -DestinationPath $extractRoot -Force
    New-Item -ItemType Directory -Force -Path (Join-Path $sdkRoot 'cmdline-tools') | Out-Null
    $target = Join-Path $sdkRoot 'cmdline-tools\latest'
    $resolvedTarget = [IO.Path]::GetFullPath($target)
    if (-not $resolvedTarget.StartsWith([IO.Path]::GetFullPath($sdkRoot) + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'SDK destination escaped project directory.' }
    Move-Item -LiteralPath (Join-Path $extractRoot 'cmdline-tools') -Destination $target
}
# Accept SDK terms for this local Android development setup.
(1..40 | ForEach-Object { 'y' }) | & $sdkManager "--sdk_root=$sdkRoot" --licenses *> (Join-Path $downloads 'sdk-licenses.log')
if ($LASTEXITCODE -ne 0) { throw 'SDK license setup failed. See .tools/downloads/sdk-licenses.log.' }
& $sdkManager "--sdk_root=$sdkRoot" 'platforms;android-37.0' 'build-tools;36.0.0' 'platform-tools'
if ($LASTEXITCODE -ne 0) { throw 'Android SDK installation failed.' }
[IO.File]::WriteAllText((Join-Path $projectRoot 'local.properties'), "sdk.dir=$($sdkRoot.Replace('\', '/').Replace(':', '\:'))`n", [Text.Encoding]::ASCII)
Write-Output "Android SDK ready: $sdkRoot"
