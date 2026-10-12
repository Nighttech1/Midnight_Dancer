# Запуск Android-эмулятора для приёмки Клиентом
# Usage:
#   .\scripts\start-android-emulator.ps1
#   .\scripts\start-android-emulator.ps1 -Avd Pixel_7_Pro
#   .\scripts\start-android-emulator.ps1 -Avd Medium_Phone_API_36.1 -Headless

param(
  [string]$Avd = "Pixel_7_Pro",
  [switch]$Headless,
  [switch]$ListOnly
)

$ErrorActionPreference = "Stop"
$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = "D:\Applications\Android\Sdk" }
$emulator = Join-Path $sdk "emulator\emulator.exe"
$adb = Join-Path $sdk "platform-tools\adb.exe"

if (-not (Test-Path $emulator)) { throw "emulator.exe not found at $emulator" }

Write-Host "Available AVDs:"
& $emulator -list-avds
if ($ListOnly) { exit 0 }

$running = & $adb devices | Select-String "emulator-"
if ($running) {
  Write-Host "Already running:`n$(& $adb devices -l)"
  exit 0
}

$args = @("-avd", $Avd, "-netdelay", "none", "-netspeed", "full")
if ($Headless) { $args += @("-no-window", "-no-audio") }

Write-Host "Starting AVD: $Avd ..."
Start-Process -FilePath $emulator -ArgumentList $args -WindowStyle Normal

Write-Host "Waiting for device boot..."
& $adb wait-for-device
$deadline = (Get-Date).AddMinutes(3)
do {
  $boot = (& $adb shell getprop sys.boot_completed 2>$null | Out-String).Trim()
  if ($boot -eq "1") { break }
  Start-Sleep -Seconds 3
} while ((Get-Date) -lt $deadline)

Write-Host "Boot status: $(& $adb shell getprop sys.boot_completed 2>$null)"
& $adb devices -l
Write-Host "OK. Client can use mobile-mcp / maestro now."
