$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$sdk = Join-Path $root '.tools\android-sdk'
$downloads = Join-Path $root '.tools\downloads'
foreach ($item in @(
    @{ File='emulator-windows_x64-16555280.zip'; Url='https://dl.google.com/android/repository/emulator-windows_x64-16555280.zip'; Folder='emulator' },
    @{ File='x86_64-35_r02.zip'; Url='https://dl.google.com/android/repository/sys-img/android/x86_64-35_r02.zip'; Folder='system-images\android-35\default\x86_64' }
)) {
    $dest = Join-Path $sdk $item.Folder
    if (Test-Path -LiteralPath (Join-Path $dest 'source.properties')) { continue }
    $zip = Join-Path $downloads $item.File
    Write-Output "Downloading $($item.File)"
    & curl.exe -sSL --fail --retry 2 --max-time 600 -o $zip $item.Url
    if ($LASTEXITCODE -ne 0) { throw 'Download failed' }
    $temp = Join-Path $downloads ([IO.Path]::GetFileNameWithoutExtension($item.File))
    Expand-Archive -LiteralPath $zip -DestinationPath $temp -Force
    $actual = Get-ChildItem -LiteralPath $temp -Directory | Select-Object -First 1
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $dest) | Out-Null
    Move-Item -LiteralPath $actual.FullName -Destination $dest
}
$avd = Join-Path $root '.tools\avd\YanJing.avd'
New-Item -ItemType Directory -Force -Path $avd | Out-Null
$imagePath = (Join-Path $sdk 'system-images\android-35\default\x86_64').Replace('\','/')
$config = @"
AvdId=YanJing
abi.type=x86_64
hw.cpu.arch=x86_64
hw.cpu.ncore=2
hw.ramSize=2048
hw.lcd.width=1080
hw.lcd.height=2400
hw.lcd.density=420
hw.keyboard=yes
hw.gpu.enabled=yes
hw.gpu.mode=swiftshader
disk.dataPartition.size=2G
image.sysdir.1=$imagePath/
tag.id=default
skin.name=1080x2400
"@
[IO.File]::WriteAllText((Join-Path $avd 'config.ini'), $config, [Text.Encoding]::ASCII)
$ini = "avd.ini.encoding=UTF-8`npath=$avd`ntarget=android-35`n"
[IO.File]::WriteAllText((Join-Path (Split-Path -Parent $avd) 'YanJing.ini'), $ini, [Text.Encoding]::ASCII)
& (Join-Path $sdk 'emulator\emulator.exe') -accel-check
