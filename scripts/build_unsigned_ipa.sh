#!/bin/bash
set -euo pipefail

echo "=========================================="
echo "=== SLS Assistant - Unsigned IPA Build ==="
echo "=========================================="

echo ""
echo "=== Flutter version ==="
flutter --version

echo ""
echo "=== Xcode version ==="
xcodebuild -version

echo ""
echo "=== Cleaning and preparing Flutter ==="

flutter clean
flutter pub get
flutter precache --ios

echo ""
echo "=== Preparing iOS configuration ==="

python3 <<'PY'
from pathlib import Path

print("Checking iOS configuration files...")

files = [
    Path("ios/Podfile"),
    Path("ios/Runner.xcodeproj/project.pbxproj"),
]

for path in files:
    if not path.exists():
        print(f"WARNING: File does not exist: {path}")
        continue

    data = path.read_bytes()

    if data.startswith(b"\xef\xbb\xbf"):
        path.write_bytes(data[3:])
        print(f"Removed UTF-8 BOM from: {path}")
