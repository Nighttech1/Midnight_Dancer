# Debug на подключённом Android-устройстве (USB / беспроводная отладка).
# Путь к SDK совпадает с android/local.properties (sdk.dir = ...\Android\Sdk).
# Запуск из корня проекта: .\scripts\run_android_debug.ps1
# Пример с flavor: .\scripts\run_android_debug.ps1 -Flavor standard

param(
    [ValidateSet("lite", "standard", "full", "english")]
    [string]$Flavor = "full"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $PSCommandPath)
Set-Location $root

$env:ANDROID_HOME = "D:\Applications\Android\Sdk"

& "$PSScriptRoot\set_pubspec_voice_assets.ps1" -Flavor $Flavor
flutter pub get
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "ANDROID_HOME=$env:ANDROID_HOME" -ForegroundColor Gray
Write-Host "flutter run --flavor $Flavor --debug --dart-define=FLAVOR=$Flavor" -ForegroundColor Cyan

flutter run --flavor $Flavor --debug --dart-define=FLAVOR=$Flavor
