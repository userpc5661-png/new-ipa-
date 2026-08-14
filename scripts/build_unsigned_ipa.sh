#!/bin/bash
set -euo pipefail

echo "=== Flutter / Xcode ==="
flutter --version
xcodebuild -version

echo "=== Flutter prepare ==="
flutter clean
flutter pub get
flutter precache --ios

echo "=== Fix iOS configuration ==="

python3 <<'PY'
from pathlib import Path

# Remove BOM if present
for path in [
    Path("ios/Podfile"),
    Path("ios/Runner.xcodeproj/project.pbxproj"),
]:
    if path.exists():
        data = path.read_bytes()
        if data.startswith(b"\xef\xbb\xbf"):
            path.write_bytes(data[3:])
            print(f"Removed BOM: {path}")

# Ensure Flutter xcconfig files exist
flutter_dir = Path("ios/Flutter")
flutter_dir.mkdir(parents=True, exist_ok=True)

(flutter_dir / "Release.xcconfig").write_text(
    '#include? "Pods/Target Support Files/Pods-Runner/Pods-Runner.release.xcconfig"\n'
    '#include "Generated.xcconfig"\n',
    encoding="utf-8"
)

(flutter_dir / "Debug.xcconfig").write_text(
    '#include? "Pods/Target Support Files/Pods-Runner/Pods-Runner.debug.xcconfig"\n'
    '#include "Generated.xcconfig"\n',
    encoding="utf-8"
)

(flutter_dir / "Profile.xcconfig").write_text(
    '#include? "Pods/Target Support Files/Pods-Runner/Pods-Runner.profile.xcconfig"\n'
    '#include "Generated.xcconfig"\n',
    encoding="utf-8"
)

print("xcconfig files ready")
PY

echo "=== CocoaPods ==="
cd ios
rm -rf Pods
rm -f Podfile.lock
pod install --repo-update
cd ..

echo "=== Build Runner.app ==="

rm -rf build/ios/DerivedData
rm -rf build/ios/ipa

set +e

xcodebuild \
  -workspace ios/Runner.xcworkspace \
  -scheme Runner \
  -configuration Release \
  -sdk iphoneos \
  -destination "generic/platform=iOS" \
  -derivedDataPath build/ios/DerivedData \
  CODE_SIGNING_ALLOWED=NO \
  CODE_SIGNING_REQUIRED=NO \
  CODE_SIGN_IDENTITY="" \
  DEVELOPMENT_TEAM="" \
  clean build 2>&1 | tee /tmp/xcodebuild.log

BUILD_EXIT=${PIPESTATUS[0]}

set -e

if [ "$BUILD_EXIT" -ne 0 ]; then
  echo ""
  echo "========== REAL BUILD ERRORS =========="
  grep -Ei \
    "error:|fatal error:|PhaseScriptExecution failed|Target kernel_snapshot failed|BUILD FAILED|lib/.*\.dart:[0-9]+" \
    /tmp/xcodebuild.log \
    | tail -n 200 || true

  echo "======================================="
  exit "$BUILD_EXIT"
fi

echo "=== Find Runner.app ==="

APP_PATH="$(find build/ios/DerivedData/Build/Products/Release-iphoneos \
  -maxdepth 1 \
  -type d \
  -name "*.app" \
  | head -n 1)"

if [ -z "$APP_PATH" ] || [ ! -d "$APP_PATH" ]; then
  echo "ERROR: Runner.app not found"
  find build/ios/DerivedData -type d -name "*.app" || true
  exit 1
fi

echo "Found: $APP_PATH"

echo "=== Create IPA ==="

mkdir -p build/ios/ipa/Payload

cp -R "$APP_PATH" build/ios/ipa/Payload/Runner.app

rm -rf build/ios/ipa/Payload/Runner.app/_CodeSignature
rm -f build/ios/ipa/Payload/Runner.app/embedded.mobileprovision

(
  cd build/ios/ipa
  /usr/bin/zip -qry SLS_Assistant.ipa Payload
)

rm -rf build/ios/ipa/Payload

echo "=== Verify IPA ==="

if [ ! -f "build/ios/ipa/SLS_Assistant.ipa" ]; then
  echo "ERROR: IPA was not created"
  exit 1
fi

ls -lh build/ios/ipa/SLS_Assistant.ipa

echo "======================================"
echo "IPA CREATED SUCCESSFULLY"
echo "build/ios/ipa/SLS_Assistant.ipa"
echo "======================================"
