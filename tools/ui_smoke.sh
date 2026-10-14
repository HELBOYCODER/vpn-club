#!/usr/bin/env bash
set -euo pipefail
mkdir -p ui-evidence
APP_ID="${APP_ID:-com.helboy.vpnclub.preview}"
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant "$APP_ID" android.permission.POST_NOTIFICATIONS
adb logcat -c
adb shell am start -W -n "$APP_ID/com.helboy.vpnclub.MainActivity"
sleep 4
adb shell dumpsys activity activities > ui-evidence/activity.txt
grep -q "$APP_ID/com.helboy.vpnclub.MainActivity" ui-evidence/activity.txt
adb exec-out screencap -p > ui-evidence/home.png
# Pixel 2 emulator default display: obtain exact dimensions before navigation.
DIM=$(adb shell wm size | tr -d '\r' | sed -n 's/.*: \([0-9]*x[0-9]*\).*/\1/p' | tail -1)
W=${DIM%x*}; H=${DIM#*x}; Y=$((H - 95))
adb shell input tap $((W * 3 / 8)) "$Y"
sleep 2
adb exec-out screencap -p > ui-evidence/servers.png
adb shell input tap $((W / 8)) "$Y"
sleep 2
adb exec-out screencap -p > ui-evidence/settings.png
adb logcat -d > ui-evidence/logcat.txt
if grep -q 'FATAL EXCEPTION' ui-evidence/logcat.txt; then
  echo 'App crash detected'; exit 1
fi
