#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
npm ci
node scripts/generate-localizations.mjs
node scripts/prepare-ios.mjs
command -v xcodegen >/dev/null || brew install xcodegen
cd ios
xcodegen generate
printf 'Open ios/YanJing.xcodeproj in Xcode.\n'
