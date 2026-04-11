# Сборка четырёх APK (Lite, Standard, Full, English) для Midnight Dancer.
# В каждый APK попадают только голоса своего flavor (меньший размер).
# English — интерфейс на английском, два голоса: Kamila и Ruslan (из full).
# Запуск: из корня проекта Midnight_Dancer: .\scripts\build_apks.ps1
# Результат: android\app\build\outputs\flutter-apk\app-*-release.apk

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $PSCommandPath)
Set-Location $root

# Android SDK (синхронно с android/local.properties)
$env:ANDROID_HOME = "D:\Applications\Android\Sdk"

$pubspecPath = Join-Path $root "pubspec.yaml"
$backupPath = Join-Path $root "pubspec.yaml.bak"

# Блок голосов в pubspec для каждого flavor (только свои папки)
$voiceBlockLite = @"
    # Piper-голоса (flavor: lite)
    - assets/voices/lite/ruslan/
"@
$voiceBlockStandard = @"
    # Piper-голоса (flavor: standard)
    - assets/voices/standard/ruslan/
    - assets/voices/standard/irina/
"@
$voiceBlockFull = @"
    # Piper-голоса (flavor: full)
    - assets/voices/full/ruslan/
    - assets/voices/full/irina/
    - assets/voices/full/kamila/
"@
$voiceBlockEnglish = @"
    # Piper-голоса (flavor: english — Kamila + Ruslan)
    - assets/voices/full/kamila/
    - assets/voices/full/ruslan/
"@

# Находим и заменяем блок голосов по строкам (надёжнее regex при разных кодировках/переносах).
function Replace-VoiceBlock {
    param([string]$content, [string]$newBlock)
    $lines = $content -split "`r?`n"
    $start = -1
    $end = -1
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match 'assets/voices/lite/ruslan') { $start = $i - 1; break }  # comment above
    }
    if ($start -lt 0) { return $content }
    $end = $start
    for ($j = $start + 1; $j -lt $lines.Count; $j++) {
        if ($lines[$j] -match '^\s+- assets/voices/(lite|standard|full)/') { $end = $j } else { break }
    }
    $before = $lines[0..($start - 1)] -join "`n"
    $afterStart = $end + 1
    $after = if ($afterStart -lt $lines.Count) { $lines[$afterStart..($lines.Count - 1)] -join "`n" } else { "" }
    $blockLines = $newBlock.TrimEnd() -split "`r?`n"
    $middle = $blockLines -join "`n"
    return $before + "`n" + $middle + "`n" + $after
}

try {
    $pubspecContent = Get-Content $pubspecPath -Raw -Encoding UTF8
    Copy-Item $pubspecPath $backupPath -Force

    $flavors = @(
        @{ Name = "lite"; Block = $voiceBlockLite },
        @{ Name = "standard"; Block = $voiceBlockStandard },
        @{ Name = "full"; Block = $voiceBlockFull },
        @{ Name = "english"; Block = $voiceBlockEnglish }
    )
    foreach ($flavor in $flavors) {
        $f = $flavor.Name
        Write-Host "Building $f (only $f voices in assets)..." -ForegroundColor Cyan
        $newContent = Replace-VoiceBlock -content $pubspecContent -newBlock $flavor.Block
        if ($newContent -eq $pubspecContent) {
            Write-Warning "Voice block was not replaced. Building with current pubspec."
        } else {
            [System.IO.File]::WriteAllText($pubspecPath, $newContent, [System.Text.UTF8Encoding]::new($false))
        }
        flutter build apk --flavor $f --release --dart-define=FLAVOR=$f
        if ($LASTEXITCODE -ne 0) {
            throw "Build failed for flavor: $f"
        }
    }
} finally {
    if (Test-Path $backupPath) {
        Copy-Item $backupPath $pubspecPath -Force
        Remove-Item $backupPath -Force
        Write-Host "Restored pubspec.yaml" -ForegroundColor Gray
    }
}

Write-Host "Done. APK files in: android\app\build\outputs\flutter-apk" -ForegroundColor Green
