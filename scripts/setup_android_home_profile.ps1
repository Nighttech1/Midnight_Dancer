# Один раз: добавляет ANDROID_HOME в профиль PowerShell и в переменные среды пользователя Windows.
# Запуск: из корня проекта  .\scripts\setup_android_home_profile.ps1

$ErrorActionPreference = "Stop"
$sdk = "D:\Applications\Android\Sdk"

# 1) Постоянно для всех программ (новые окна терминала, Android Studio и т.д.)
[Environment]::SetEnvironmentVariable("ANDROID_HOME", $sdk, "User")
$env:ANDROID_HOME = $sdk
Write-Host "User ANDROID_HOME = $sdk" -ForegroundColor Green

# 2) Строка в профиле PowerShell (на случай сброса сессии / другой логики)
$profilePath = $PROFILE
$dir = Split-Path $profilePath -Parent
if (-not (Test-Path $dir)) {
    New-Item -ItemType Directory -Path $dir -Force | Out-Null
}

$snippet = @"

# Midnight Dancer - Android SDK (flutter debug / build)
`$env:ANDROID_HOME = "D:\Applications\Android\Sdk"
"@

if (-not (Test-Path $profilePath)) {
    Set-Content -Path $profilePath -Value $snippet.TrimStart() -Encoding UTF8
    Write-Host "Created profile: $profilePath" -ForegroundColor Green
} else {
    $raw = Get-Content $profilePath -Raw -ErrorAction SilentlyContinue
    if ($raw -match 'D:\\Applications\\Android\\Sdk' -and $raw -match 'ANDROID_HOME') {
        Write-Host "Profile already contains ANDROID_HOME for this SDK." -ForegroundColor Gray
    } else {
        Add-Content -Path $profilePath -Value $snippet -Encoding UTF8
        Write-Host "Appended ANDROID_HOME to profile: $profilePath" -ForegroundColor Green
    }
}

Write-Host "Done. Open a new terminal or run: . `$PROFILE" -ForegroundColor Cyan
