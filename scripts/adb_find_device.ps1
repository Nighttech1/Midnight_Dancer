# ADB USB debug diagnostics. Run on YOUR PC with phone connected:
#   .\scripts\adb_find_device.ps1
# Encoding: ASCII-friendly (avoid PowerShell 5.1 mojibake).

$ErrorActionPreference = "Continue"
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "D:\Applications\Android\Sdk" }
$adb = Join-Path $sdk "platform-tools\adb.exe"

function Write-Step([string]$id, [string]$msg) {
    Write-Host ""
    Write-Host "========== $id : $msg ==========" -ForegroundColor Cyan
}

Write-Host "ANDROID_HOME = $sdk" -ForegroundColor Gray

Write-Step "H2" "platform-tools path"
if (-not (Test-Path $adb)) {
    Write-Host "FAIL: adb not found: $adb" -ForegroundColor Red
    exit 1
}
Write-Host "OK: $adb" -ForegroundColor Green

Write-Step "H3" "PATH conflict (other adb.exe)"
$which = Get-Command adb -ErrorAction SilentlyContinue
if ($null -ne $which -and $which.Source -ne $adb) {
    Write-Host "WARN: different adb in PATH:" $which.Source -ForegroundColor Yellow
    Write-Host "      This script uses SDK adb only."
} else {
    Write-Host "OK: no conflicting adb (or same as SDK)."
}

Write-Step "H1" "adb kill-server / start-server"
& $adb kill-server 2>$null
Start-Sleep -Milliseconds 800
& $adb start-server 2>$null
Start-Sleep -Milliseconds 500

Write-Step "H4" "adb devices -l (before reconnect)"
& $adb devices -l

Write-Step "H4b" "adb reconnect"
& $adb reconnect 2>$null
Start-Sleep -Seconds 2
& $adb devices -l

Write-Step "H5" "status hints"
$out = & $adb devices 2>$null | Out-String
if ($out -match "unauthorized") {
    Write-Host "FAIL: unauthorized - tap Allow USB debugging on phone (RSA)." -ForegroundColor Red
}
if ($out -match "offline") {
    Write-Host "FAIL: offline - replug cable, toggle USB debugging." -ForegroundColor Red
}
if ($out -match "(?m)^\S+\s+device\s*$") {
    Write-Host "OK: at least one device in 'device' state." -ForegroundColor Green
}

Write-Step "H6" "Windows PnP (Android / MTP / composite)"
try {
    $usb = Get-PnpDevice -PresentOnly -ErrorAction SilentlyContinue |
        Where-Object { $_.FriendlyName -match 'ADB|Android|Composite|Adb Interface|MTP|Portable' }
    if ($usb) {
        $usb | Select-Object Status, Class, FriendlyName | Format-Table -AutoSize
    } else {
        Write-Host "No obvious Android devices in PnP (or need admin)." -ForegroundColor Yellow
    }
} catch {
    Write-Host "PnP query failed: $_" -ForegroundColor Yellow
}

Write-Step "H7" "If still empty: try on phone"
Write-Host "- Revoke USB debugging authorizations, replug, Allow again."
Write-Host "- USB file transfer mode, not charge-only."
Write-Host "- Try another USB port (motherboard rear ports)."
Write-Host "- Wireless: adb pair IP:PORT then adb connect IP:PORT"

Write-Step "H8" "flutter devices"
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    $fd = flutter devices 2>&1 | Out-String
    Write-Host $fd
    if ($fd -match "android") {
        Write-Host ""
        Write-Host "OK: Flutter sees Android. Run:" -ForegroundColor Green
        Write-Host "  .\scripts\run_android_debug.ps1 -Flavor full" -ForegroundColor White
    }
} finally {
    Pop-Location
}
