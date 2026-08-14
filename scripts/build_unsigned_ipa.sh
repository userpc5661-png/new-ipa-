#!/bin/bash
set -euo pipefail

echo "=== Cleaning and preparing Flutter ==="
flutter clean
flutter pub get
flutter precache --ios

echo "=== Removing UTF-8 BOM and fixing iOS target ==="
python3 <<'PY'
from pathlib import Path

files = [
    Path("ios/Podfile"),
    Path("ios/Runner.xcodeproj/project.pbxproj"),
]

for path in files:
    data = path.read_bytes()

    if data.startswith(b"\xef\xbb\xbf"):
        path.write_bytes(data[3:])
        print(f"Removed BOM from: {path}")
    else:
        print(f"No BOM found in: {path}")

podfile = Path("ios/Podfile")
text = podfile.read_text(encoding="utf-8")
text = text.replace(
    "config.build_settings['IPHONEOS_DEPLOYMENT_TARGET'] = '13.0'",
    "config.build_settings['IPHONEOS_DEPLOYMENT_TARGET'] = '15.5'",
)
podfile.write_text(text, encoding="utf-8", newline="\n")
print("Podfile deployment target set to 15.5")

# Create ios/Flutter directory if missing
flutter_dir = Path("ios/Flutter")
flutter_dir.mkdir(parents=True, exist_ok=True)

# Re-create Release.xcconfig required by Xcode
release_xcconfig = flutter_dir / "Release.xcconfig"

release_xcconfig.write_text(
    '#include? "Pods/Target Support Files/Pods-Runner/Pods-Runner.release.xcconfig"\n'
    '#include "Generated.xcconfig"\n',
    encoding="utf-8",
    newline="\n",
)

print("Created ios/Flutter/Release.xcconfig")
PY

echo "=== Installing CocoaPods ==="
cd ios

rm -rf Pods
rm -f Podfile.lock

pod install --repo-update

cd ..

echo "=== Building unsigned Runner.app with Xcode ==="

rm -rf build/ios/DerivedData
rm -rf build/ios/ipa

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
  clean build

echo "=== Locating Runner.app ==="

APP_PATH="$(find build/ios/DerivedData/Build/Products/Release-iphoneos \
  -maxdepth 1 \
  -type d \
  -name '*.app' \
  | head -n 1)"

if [ -z "${APP_PATH}" ] || [ ! -d "${APP_PATH}" ]; then
  echo "ERROR: Runner.app was not found."

  echo "Available .app files:"
  find build/ios/DerivedData -type d -name "*.app" || true

  exit 1
fi

echo "Found app: ${APP_PATH}"

echo "=== Creating unsigned IPA ==="

mkdir -p build/ios/ipa/Payload

cp -R "${APP_PATH}" build/ios/ipa/Payload/Runner.app

cd build/ios/ipa

/usr/bin/zip -qry SLS_Assistant.ipa Payload

rm -rf Payload

echo "=== IPA created successfully ==="

ls -lh SLS_Assistant.ipa
