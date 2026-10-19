$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$version = [regex]::Match((Get-Content -Raw (Join-Path $root 'app\build.gradle.kts')), 'versionName\s*=\s*"([0-9.]+)"').Groups[1].Value
$output = Join-Path $root "dist\yanjing-$version-source.zip"
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
New-Item -ItemType Directory -Force -Path (Join-Path $root 'dist') | Out-Null
if (Test-Path -LiteralPath $output) { Remove-Item -LiteralPath $output }
$archive = [IO.Compression.ZipFile]::Open($output, [IO.Compression.ZipArchiveMode]::Create)
try {
    # Publish reviewed repository files only; never sweep unrelated local files
    # into a public archive by recursively enumerating source directories.
    $tracked = & git -C $root -c core.quotepath=false ls-files
    if ($LASTEXITCODE -ne 0 -or -not $tracked) { throw 'Cannot list tracked source files.' }
    foreach ($entry in $tracked) {
        $file = Get-Item -LiteralPath (Join-Path $root $entry)
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $file.FullName, $entry, [IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally { $archive.Dispose() }
Write-Output $output
