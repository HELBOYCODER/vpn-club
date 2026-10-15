#!/usr/bin/env bash
set -euo pipefail
mkdir -p ui-evidence
# Keep the previous public preview installed to catch certificate/package collisions.
curl --fail --location --retry 3 'https://github.com/HELBOYCODER/vpn-club/releases/download/v4.3.1-preview.1/HellboyVPN-v4.3.1-preview.1.apk' -o "$RUNNER_TEMP/previous-hellboy.apk"
adb install -r "$RUNNER_TEMP/previous-hellboy.apk" | tee ui-evidence/previous-install.txt
# Instrumentation installed a debug build. Exercise the exact release APK next.
adb uninstall "$APP_ID"
export APK_PATH="$(find app/build/outputs/apk/release -maxdepth 1 -name '*.apk' -print -quit)"
test -n "$APK_PATH"
bash tools/ui_smoke.sh
adb install -r "$APK_PATH" | tee ui-evidence/release-reinstall.txt
adb shell pm path com.helboy.vpnclub.preview43 | tee ui-evidence/previous-package.txt
adb shell pm path "$APP_ID" | tee ui-evidence/release-package.txt
grep -q 'package:' ui-evidence/previous-package.txt
grep -q 'package:' ui-evidence/release-package.txt
sha256sum "$APK_PATH" > ui-evidence/tested-release-sha256.txt
