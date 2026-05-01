# Сборка app-full-release.apk с релизной подписью (keystore из Ключи/upload-keystore.jks).
# Перед запуском задайте пароли — иначе Gradle подставит debug-подпись.
#
# Вариант A (PowerShell, рекомендуется):
#   $env:MIDNIGHT_UPLOAD_STORE_PASSWORD = '...'
#   $env:MIDNIGHT_UPLOAD_KEY_PASSWORD = '...'
#   .\scripts\build_full_release_signed.ps1
#
# Вариант B: заполните android/key.properties (не коммитить).

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not $env:MIDNIGHT_UPLOAD_STORE_PASSWORD -or -not $env:MIDNIGHT_UPLOAD_KEY_PASSWORD) {
    if (-not (Test-Path (Join-Path $root 'android\key.properties'))) {
        Write-Host 'Задайте $env:MIDNIGHT_UPLOAD_STORE_PASSWORD и MIDNIGHT_UPLOAD_KEY_PASSWORD или создайте android/key.properties (см. android/key.properties.example и BUILD.md).' -ForegroundColor Yellow
        exit 1
    }
}

$pubspecPath = Join-Path $root 'pubspec.yaml'
$backupPath = Join-Path $root 'pubspec.yaml.bak'
Copy-Item $pubspecPath $backupPath -Force

try {
    & "$PSScriptRoot\set_pubspec_voice_assets.ps1" -Flavor full
    flutter pub get
    if ($LASTEXITCODE -ne 0) { throw 'flutter pub get failed' }
    flutter build apk --flavor full --release --dart-define=FLAVOR=full
    if ($LASTEXITCODE -ne 0) { throw 'flutter build apk failed' }
}
finally {
    if (Test-Path $backupPath) {
        Copy-Item $backupPath $pubspecPath -Force
        Remove-Item $backupPath -Force
        flutter pub get
    }
}

Write-Host 'APK: build\app\outputs\flutter-apk\app-full-release.apk' -ForegroundColor Green
Write-Host 'Проверка: apksigner verify --print-certs build\app\outputs\flutter-apk\app-full-release.apk' -ForegroundColor Gray
