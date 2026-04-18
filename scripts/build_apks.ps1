# Сборка четырёх APK (Lite, Standard, Full, English).
# Перед каждой сборкой в pubspec подставляются только Piper-голоса этого flavor (см. set_pubspec_voice_assets.ps1).
# Запуск из корня проекта: .\scripts\build_apks.ps1
# Результат: build\app\outputs\flutter-apk\app-*-release.apk

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$env:ANDROID_HOME = 'D:\Applications\Android\Sdk'

$pubspecPath = Join-Path $root 'pubspec.yaml'
$backupPath = Join-Path $root 'pubspec.yaml.bak'

Copy-Item $pubspecPath $backupPath -Force

try {
    $flavors = @('lite', 'standard', 'full', 'english')
    foreach ($f in $flavors) {
        Write-Host "Building $f (Piper assets for this flavor only)..." -ForegroundColor Cyan
        & "$PSScriptRoot\set_pubspec_voice_assets.ps1" -Flavor $f
        flutter pub get
        if ($LASTEXITCODE -ne 0) { throw "flutter pub get failed for $f" }
        flutter build apk --flavor $f --release --dart-define=FLAVOR=$f
        if ($LASTEXITCODE -ne 0) { throw "Build failed for flavor: $f" }
    }
}
finally {
    if (Test-Path $backupPath) {
        Copy-Item $backupPath $pubspecPath -Force
        Remove-Item $backupPath -Force
        Write-Host 'Restored pubspec.yaml from backup.' -ForegroundColor Gray
        flutter pub get
    }
}

Write-Host 'Done. APK files in: build\app\outputs\flutter-apk' -ForegroundColor Green
