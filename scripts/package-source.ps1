$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$output = Join-Path $root 'dist\yanjing-1.2.1-source.zip'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
New-Item -ItemType Directory -Force -Path (Join-Path $root 'dist') | Out-Null
if (Test-Path -LiteralPath $output) { Remove-Item -LiteralPath $output }
$archive = [IO.Compression.ZipFile]::Open($output, [IO.Compression.ZipArchiveMode]::Create)
try {
    $include = @('.gitignore','.gitattributes','README.md','LICENSE','build.gradle.kts','settings.gradle.kts','gradle.properties','gradlew','gradlew.bat','package.json','package-lock.json','gradle','scripts','docs','data\cuv-verse-mappings.json','third_party','app\build.gradle.kts','app\proguard-rules.pro','app\src')
    foreach ($relative in $include) {
        $item = Get-Item -LiteralPath (Join-Path $root $relative)
        $files = if ($item.PSIsContainer) { Get-ChildItem -LiteralPath $item.FullName -Recurse -File } else { @($item) }
        foreach ($file in $files) {
            $entry = [IO.Path]::GetRelativePath($root, $file.FullName).Replace('\','/')
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $file.FullName, $entry, [IO.Compression.CompressionLevel]::Optimal) | Out-Null
        }
    }
} finally { $archive.Dispose() }
Write-Output $output
