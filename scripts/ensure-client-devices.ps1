# Клиент сам поднимает устройства для приёмки (без участия человека)
# Usage:
#   .\scripts\ensure-client-devices.ps1
#   .\scripts\ensure-client-devices.ps1 -Platform android
#   .\scripts\ensure-client-devices.ps1 -Platform android -Headless

param(
  [ValidateSet("android", "all")]
  [string]$Platform = "android",
  [string]$Avd = "Pixel_7_Pro",
  [switch]$Headless
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

Write-Host "=== ensure-client-devices ($Platform) ==="

if ($Platform -eq "android" -or $Platform -eq "all") {
  $args = @("-Avd", $Avd)
  if ($Headless) { $args += "-Headless" }
  & "$PSScriptRoot\start-android-emulator.ps1" @args
  if ($LASTEXITCODE -ne 0) { throw "Android emulator failed" }
  Write-Host "ANDROID_READY=1"
}

Write-Host @"

iOS: локального Simulator на Windows нет.
Клиент использует MCP «mobilenext-cloud» (https://app.mobilenext.ai/mcp):
  1) авторизация в браузере (один раз на аккаунт)
  2) allocate iPhone
  3) install IPA / screenshot / gestures
  4) скрины пользователю в чат + memory/tickets/.../visual/

IOS_CLOUD=mobilenext-cloud
"@
