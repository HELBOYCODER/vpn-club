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
# Locate actual navigation labels rather than guessing density-dependent coordinates.
click_label() {
  adb shell uiautomator dump /sdcard/hellboy-ui.xml
  adb pull /sdcard/hellboy-ui.xml ui-evidence/current.xml
  python3 - "$1" <<'PYCLICK'
import re, subprocess, sys, xml.etree.ElementTree as ET
nodes = [n for n in ET.parse('ui-evidence/current.xml').iter('node') if n.get('text') == sys.argv[1]]
assert nodes, 'Navigation label not found: ' + sys.argv[1]
node = max(nodes, key=lambda n: int(re.findall(r'[0-9]+', n.get('bounds'))[1]))
x1,y1,x2,y2 = map(int, re.findall(r'[0-9]+', node.get('bounds')))
subprocess.run(['adb','shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)], check=True)
PYCLICK
}
click_label 'سرورها'
sleep 2
adb exec-out screencap -p > ui-evidence/servers.png
click_label 'تنظیمات'
sleep 2
adb exec-out screencap -p > ui-evidence/settings.png
adb logcat -d > ui-evidence/logcat.txt
if grep -q 'FATAL EXCEPTION' ui-evidence/logcat.txt; then
  echo 'App crash detected'; exit 1
fi
