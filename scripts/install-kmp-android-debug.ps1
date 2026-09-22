# Install KMP Stage 1 debug APK on a ready Android device/emulator.
# Does NOT start Flutter. Compatible with scripts/ensure-client-devices.ps1 (run that first).

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$kmp = Join-Path $root "kmp"
$apk = Join-Path $kmp "androidApp\build\outputs\apk\debug\androidApp-debug.apk"

Write-Host "Building :androidApp assembleDebug ..."
Push-Location $kmp
try {
    & .\gradlew.bat :androidApp:assembleDebug --quiet
    if ($LASTEXITCODE -ne 0) { throw "Gradle assembleDebug failed: $LASTEXITCODE" }
} finally {
    Pop-Location
}

if (-not (Test-Path $apk)) {
    throw "APK not found: $apk"
}

$adb = $env:ANDROID_HOME
if ($adb) {
    $adb = Join-Path $adb "platform-tools\adb.exe"
} else {
    $adb = "adb"
}

Write-Host "Installing $apk ..."
& $adb install -r $apk
if ($LASTEXITCODE -ne 0) { throw "adb install failed" }

Write-Host "Launching com.midnightdancer.app ..."
& $adb shell am start -n com.midnightdancer.app/.MainActivity
Write-Host "KMP_ANDROID_INSTALLED"
