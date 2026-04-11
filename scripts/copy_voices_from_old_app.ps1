# Копирует Piper VITS голоса из old_app_midnightdancer в assets/voices для Flutter.
# Запуск: из корня проекта Midnight_Dancer: .\scripts\copy_voices_from_old_app.ps1

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $PSCommandPath)
$oldVoices = Join-Path $root "old_app_midnightdancer\voices"
$assetsVoices = Join-Path $root "assets\voices"

if (-not (Test-Path $oldVoices)) {
    Write-Error "Папка не найдена: $oldVoices"
}

function Copy-VoiceFiles {
    param($sourceDir, $onnxName, $targetDirs)
    if (-not (Test-Path $sourceDir)) {
        Write-Warning "Пропуск: $sourceDir не найден"
        return
    }
    # В старом приложении файл токенов может называться "tokens" или "tokens.txt"
    $tokensSrc = $null
    if (Test-Path (Join-Path $sourceDir "tokens")) { $tokensSrc = Join-Path $sourceDir "tokens" }
    elseif (Test-Path (Join-Path $sourceDir "tokens.txt")) { $tokensSrc = Join-Path $sourceDir "tokens.txt" }
    $onnxSrc = Join-Path $sourceDir $onnxName
    if (-not $tokensSrc -or -not (Test-Path $onnxSrc)) {
        Write-Warning "Пропуск: нет tokens/tokens.txt или $onnxName в $sourceDir"
        return
    }
    foreach ($dest in $targetDirs) {
        $fullDest = Join-Path $assetsVoices $dest
        New-Item -ItemType Directory -Force -Path $fullDest | Out-Null
        Copy-Item $onnxSrc (Join-Path $fullDest (Split-Path $onnxName -Leaf)) -Force
        Copy-Item $tokensSrc (Join-Path $fullDest "tokens.txt") -Force
        Write-Host "OK: $dest"
    }
}

# Ruslan -> lite/ruslan, standard/ruslan, full/ruslan
Copy-VoiceFiles -sourceDir (Join-Path $oldVoices "vits-piper-ru_RU-ruslan-medium") `
    -onnxName "ru_RU-ruslan-medium.onnx" `
    -targetDirs @("lite\ruslan", "standard\ruslan", "full\ruslan")

# Irina -> standard/irina, full/irina
Copy-VoiceFiles -sourceDir (Join-Path $oldVoices "vits-piper-ru_RU-irina-medium") `
    -onnxName "ru_RU-irina-medium.onnx" `
    -targetDirs @("standard\irina", "full\irina")

# Kamila (en-US) -> full/kamila
Copy-VoiceFiles -sourceDir (Join-Path $oldVoices "vits-piper-en_US-libritts_r-medium") `
    -onnxName "en_US-libritts_r-medium.onnx" `
    -targetDirs @("full\kamila")

Write-Host "Done. Rebuild the app (flutter run / build)."