#!/usr/bin/env bash
# Build unsigned IPA for Midnight Dancer KMP iosApp (Stage 4 / C-35).
# Requires: macOS, Xcode CLT, JDK 17+, Homebrew xcodegen.
# Output: ios-build/artifacts/current/MidnightDancer-stage4.ipa
# Docs: ios-build/kmp/MAC_LOCAL.md
# Override: IPA_NAME=MidnightDancer-custom.ipa ./scripts/build-kmp-ios-ipa.sh
#
# Usage:
#   ./scripts/build-kmp-ios-ipa.sh
#   ./scripts/build-kmp-ios-ipa.sh iphoneos          # default — real device / Mobile Next Cloud
#   ./scripts/build-kmp-ios-ipa.sh iphonesimulator   # simulator arm64 (not for cloud iPhone)

set -euo pipefail

SDK="${1:-iphoneos}"
CONFIGURATION="${CONFIGURATION:-Debug}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KMP="${ROOT}/kmp"
IOS_APP="${KMP}/iosApp"
DIST="${ROOT}/ios-build/artifacts/current"
DERIVED="${IOS_APP}/build/DerivedData"
PAYLOAD_DIR="${ROOT}/ios-build/artifacts/.staging"
IPA_NAME="${IPA_NAME:-MidnightDancer-stage4.ipa}"

echo "==> Repo: ${ROOT}"
echo "==> SDK: ${SDK}  configuration: ${CONFIGURATION}"

command -v java >/dev/null || { echo "JDK required"; exit 1; }
command -v xcodebuild >/dev/null || { echo "Xcode / xcodebuild required"; exit 1; }
command -v xcodegen >/dev/null || {
  echo "xcodegen missing — installing via Homebrew..."
  brew install xcodegen
}

chmod +x "${KMP}/gradlew"

case "${SDK}" in
  iphoneos)
    GRADLE_TASK=":shared:linkDebugFrameworkIosArm64"
    FRAMEWORK_HINT="${KMP}/shared/build/bin/iosArm64/debugFramework/Shared.framework"
    XCODE_SDK="iphoneos"
    DESTINATION="generic/platform=iOS"
    PRODUCTS_DIR="Debug-iphoneos"
    ;;
  iphonesimulator)
    GRADLE_TASK=":shared:linkDebugFrameworkIosSimulatorArm64"
    FRAMEWORK_HINT="${KMP}/shared/build/bin/iosSimulatorArm64/debugFramework/Shared.framework"
    XCODE_SDK="iphonesimulator"
    DESTINATION="generic/platform=iOS Simulator"
    PRODUCTS_DIR="Debug-iphonesimulator"
    ;;
  *)
    echo "Unknown SDK '${SDK}'. Use iphoneos or iphonesimulator."
    exit 1
    ;;
esac

if [[ "${CONFIGURATION}" == "Release" ]]; then
  case "${SDK}" in
    iphoneos) GRADLE_TASK=":shared:linkReleaseFrameworkIosArm64"
              FRAMEWORK_HINT="${KMP}/shared/build/bin/iosArm64/releaseFramework/Shared.framework"
              PRODUCTS_DIR="Release-iphoneos" ;;
    iphonesimulator) GRADLE_TASK=":shared:linkReleaseFrameworkIosSimulatorArm64"
              FRAMEWORK_HINT="${KMP}/shared/build/bin/iosSimulatorArm64/releaseFramework/Shared.framework"
              PRODUCTS_DIR="Release-iphonesimulator" ;;
  esac
fi

echo "==> Gradle ${GRADLE_TASK}"
(
  cd "${KMP}"
  ./gradlew "${GRADLE_TASK}" --no-daemon
)

if [[ ! -d "${FRAMEWORK_HINT}" ]]; then
  echo "ERROR: Shared.framework not found at ${FRAMEWORK_HINT}"
  exit 1
fi
echo "==> Shared.framework OK: ${FRAMEWORK_HINT}"

# Stage for XcodeGen FRAMEWORK_SEARCH_PATHS:
#   1) iosApp/Frameworks
#   2) shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)
SDK_VERSION="$(xcrun --sdk "${XCODE_SDK}" --show-sdk-version)"
XCODE_SDK_NAME="${XCODE_SDK}${SDK_VERSION}"
SYNC_DIR="${KMP}/shared/build/xcode-frameworks/${CONFIGURATION}/${XCODE_SDK_NAME}"
STAGE_DIR="${IOS_APP}/Frameworks"
mkdir -p "${SYNC_DIR}" "${STAGE_DIR}"
rm -rf "${SYNC_DIR}/Shared.framework" "${STAGE_DIR}/Shared.framework"
cp -R "${FRAMEWORK_HINT}" "${SYNC_DIR}/Shared.framework"
cp -R "${FRAMEWORK_HINT}" "${STAGE_DIR}/Shared.framework"
echo "==> Synced framework → ${SYNC_DIR}/Shared.framework"
echo "==> Staged framework  → ${STAGE_DIR}/Shared.framework"

echo "==> XcodeGen"
(
  cd "${IOS_APP}"
  xcodegen generate --spec project.yml
)

echo "==> xcodebuild (unsigned)"
rm -rf "${DERIVED}"
mkdir -p "${DERIVED}"

set +e
xcodebuild \
  -project "${IOS_APP}/MidnightDancerIos.xcodeproj" \
  -scheme iosApp \
  -configuration "${CONFIGURATION}" \
  -sdk "${XCODE_SDK}" \
  -destination "${DESTINATION}" \
  -derivedDataPath "${DERIVED}" \
  CODE_SIGNING_ALLOWED=NO \
  CODE_SIGNING_REQUIRED=NO \
  CODE_SIGN_IDENTITY= \
  DEVELOPMENT_TEAM= \
  ONLY_ACTIVE_ARCH=NO \
  build
XCODE_STATUS=$?
set -e

if [[ "${XCODE_STATUS}" -ne 0 ]]; then
  echo "==> Unsigned build failed (exit ${XCODE_STATUS}); retrying ad-hoc CODE_SIGN_IDENTITY=-"
  rm -rf "${DERIVED}"
  mkdir -p "${DERIVED}"
  xcodebuild \
    -project "${IOS_APP}/MidnightDancerIos.xcodeproj" \
    -scheme iosApp \
    -configuration "${CONFIGURATION}" \
    -sdk "${XCODE_SDK}" \
    -destination "${DESTINATION}" \
    -derivedDataPath "${DERIVED}" \
    CODE_SIGN_IDENTITY=- \
    CODE_SIGNING_REQUIRED=NO \
    DEVELOPMENT_TEAM= \
    ONLY_ACTIVE_ARCH=NO \
    build
fi
APP_PATH="${DERIVED}/Build/Products/${PRODUCTS_DIR}/MidnightDancer.app"
if [[ ! -d "${APP_PATH}" ]]; then
  echo "ERROR: .app not found at ${APP_PATH}"
  echo "Products tree:"
  find "${DERIVED}/Build/Products" -maxdepth 3 -type d -print || true
  exit 1
fi

echo "==> Package IPA"
rm -rf "${PAYLOAD_DIR}"
mkdir -p "${PAYLOAD_DIR}/Payload"
cp -R "${APP_PATH}" "${PAYLOAD_DIR}/Payload/"

mkdir -p "${DIST}"
IPA_PATH="${DIST}/${IPA_NAME}"
rm -f "${IPA_PATH}"
(
  cd "${PAYLOAD_DIR}"
  zip -qry "${IPA_PATH}" Payload
)

echo "==> IPA ready: ${IPA_PATH}"
ls -lh "${IPA_PATH}"
echo "Upload via Mobile Next: mobilenext_create_upload → PUT → mobilenext_install_app"
