# Replaces Piper voice folders in pubspec.yaml between BEGIN/END markers (ASCII-only lines — UTF-8 safe on Windows).
# Usage from project root: .\scripts\set_pubspec_voice_assets.ps1 -Flavor full
# Then: flutter pub get

param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('lite', 'standard', 'full', 'english', 'playverify')]
    [string]$Flavor
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$pubspecPath = Join-Path $root 'pubspec.yaml'

$voiceBlocks = @{
    lite     = @(
        '    # Piper voices (flavor: lite)'
        '    - assets/voices/lite/ruslan/'
    )
    standard = @(
        '    # Piper voices (flavor: standard)'
        '    - assets/voices/standard/ruslan/'
        '    - assets/voices/standard/irina/'
    )
    full     = @(
        '    # Piper voices (flavor: full)'
        '    - assets/voices/full/ruslan/'
        '    - assets/voices/full/irina/'
        '    - assets/voices/full/kamila/'
    )
    english  = @(
        '    # Piper voices (flavor: english, Kamila + Ruslan from full/)'
        '    - assets/voices/full/ruslan/'
        '    - assets/voices/full/kamila/'
    )
    playverify = @(
        '    # Piper voices (flavor: playverify, Ruslan only — small APK for Play ownership check)'
        '    - assets/voices/full/ruslan/'
    )
}

$lines = [System.IO.File]::ReadAllLines($pubspecPath, [System.Text.UTF8Encoding]::new($false))
$begin = -1
$end = -1
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match '^\s*# BEGIN_PIPER_VOICE_ASSETS\s*$') { $begin = $i; continue }
    if ($begin -ge 0 -and $lines[$i] -match '^\s*# END_PIPER_VOICE_ASSETS\s*$') { $end = $i; break }
}
if ($begin -lt 0 -or $end -lt 0) {
    throw "pubspec.yaml: markers # BEGIN_PIPER_VOICE_ASSETS / # END_PIPER_VOICE_ASSETS not found"
}

$out = [System.Collections.Generic.List[string]]::new()
for ($i = 0; $i -lt $begin; $i++) { [void]$out.Add($lines[$i]) }
[void]$out.Add($lines[$begin])
foreach ($row in $voiceBlocks[$Flavor]) { [void]$out.Add($row) }
for ($i = $end; $i -lt $lines.Count; $i++) { [void]$out.Add($lines[$i]) }

$text = ($out -join "`n") + "`n"
[System.IO.File]::WriteAllText($pubspecPath, $text, [System.Text.UTF8Encoding]::new($false))
Write-Host "pubspec.yaml: Piper voice block set to flavor '$Flavor'." -ForegroundColor Green
