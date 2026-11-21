#!/usr/bin/env bash
# Manual Codemagic simulator-only evidence. No signing, uploads, accounts, or real devices.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
OUT="$ROOT/ios-build/visual-qa/$(date -u +%Y%m%dT%H%M%SZ)-$$"
mkdir -p "$OUT"
SIM_ID=""
RECORD_PID=""
stop_recording() {
  if [[ -n "$RECORD_PID" ]]; then
    kill -INT "$RECORD_PID" 2>/dev/null || true
    for _ in {1..20}; do
      kill -0 "$RECORD_PID" 2>/dev/null || break
      sleep 0.25
    done
    kill -TERM "$RECORD_PID" 2>/dev/null || true
    wait "$RECORD_PID" 2>/dev/null || true
    RECORD_PID=""
  fi
}
cleanup() {
  stop_recording
  # SIM_ID belongs only to the disposable simulator created below.
  if [[ -n "$SIM_ID" ]]; then
    xcrun simctl shutdown "$SIM_ID" >/dev/null 2>&1 || true
    xcrun simctl delete "$SIM_ID" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT
{
  git rev-parse HEAD
  xcodebuild -version
  xcrun --sdk iphonesimulator --show-sdk-version
  java -version
} 2>&1 | tee "$OUT/toolchain.log"
xcrun simctl list devices available --json > "$OUT/available-devices.json"
xcrun simctl list devicetypes --json > "$OUT/device-types.json"
# Discover an installed iPhone/runtime pair, then create our own fresh device of that type.
python3 - "$OUT" > "$OUT/selected-simulator.tsv" <<'PY'
import json, re, sys
from pathlib import Path
out=Path(sys.argv[1])
devices=json.loads((out/'available-devices.json').read_text())['devices']
types=json.loads((out/'device-types.json').read_text())['devicetypes']
by_name={d['name']:d['identifier'] for d in types}
pairs=[]
for runtime, rows in devices.items():
    if '.iOS-' not in runtime: continue
    for device in rows:
        if device.get('isAvailable') and device['name'].startswith('iPhone'):
            kind=device.get('deviceTypeIdentifier') or by_name.get(device['name'])
            if kind: pairs.append((runtime, kind))
if not pairs: raise SystemExit('No installed available iPhone simulator/runtime; inspect available-devices.json')
runtime,kind=max(pairs,key=lambda p:tuple(int(x) for x in re.findall(r'\d+',p[0])))
print(kind+'\t'+runtime)
PY
IFS=$'\t' read -r DEVICE_TYPE RUNTIME < "$OUT/selected-simulator.tsv"
SIM_ID="$(xcrun simctl create "MidnightDancer-VisualQA-$$" "$DEVICE_TYPE" "$RUNTIME")"
printf '%s\n' "$SIM_ID" > "$OUT/simulator-id.txt"
xcrun simctl boot "$SIM_ID"
xcrun simctl bootstatus "$SIM_ID" -b
chmod +x scripts/build-kmp-ios-ipa.sh kmp/gradlew
CONFIGURATION=Debug PREPARE_ONLY=1 ./scripts/build-kmp-ios-ipa.sh iphonesimulator 2>&1 | tee "$OUT/prepare.log"
PROJECT="$ROOT/kmp/iosApp/MidnightDancerIos.xcodeproj"
DERIVED="$OUT/DerivedData"
xcodebuild -project "$PROJECT" -scheme iosApp -configuration Debug \
  -destination "platform=iOS Simulator,id=$SIM_ID" -derivedDataPath "$DERIVED" \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO \
  -parallel-testing-enabled NO build-for-testing 2>&1 | tee "$OUT/build-for-testing.log"
# Recording starts before the UI test runner launches the app, preserving the real cold motion.
xcrun simctl io "$SIM_ID" recordVideo --codec=h264 "$OUT/cold-and-navigation.mp4" > "$OUT/recording.log" 2>&1 &
RECORD_PID=$!
sleep 1
set +e
xcodebuild -project "$PROJECT" -scheme iosApp -configuration Debug \
  -destination "platform=iOS Simulator,id=$SIM_ID" -derivedDataPath "$DERIVED" \
  -resultBundlePath "$OUT/VisualSmoke.xcresult" -parallel-testing-enabled NO \
  -only-testing:iosAppUITests/VisualSmokeTests/testColdSplashTransitionAndFiveTabsAndSettings \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO \
  test-without-building 2>&1 | tee "$OUT/tests.log"
TEST_STATUS=$?
set -e
stop_recording
if [[ -d "$OUT/VisualSmoke.xcresult" ]]; then
  if ! xcrun xcresulttool export attachments --path "$OUT/VisualSmoke.xcresult" \
      --output-path "$OUT/screenshots" > "$OUT/attachment-export.log" 2>&1; then
    echo "Attachment export failed; retained xcresult contains the authoritative screenshots." | tee -a "$OUT/attachment-export.log"
    if [[ "$TEST_STATUS" -eq 0 ]]; then TEST_STATUS=65; fi
  fi
fi
if [[ "$TEST_STATUS" -eq 0 ]]; then
  PNG_COUNT="$(find "$OUT/screenshots" -type f -iname '*.png' | wc -l | tr -d ' ')"
  if [[ "$PNG_COUNT" -lt 10 ]]; then
    echo "Expected ten named screenshots; only $PNG_COUNT PNG attachments exported." | tee -a "$OUT/attachment-export.log"
    TEST_STATUS=65
  fi
fi
printf 'XCUITest exit status: %s\n' "$TEST_STATUS" | tee "$OUT/status.txt"
exit "$TEST_STATUS"
