#!/usr/bin/env bash
set -euo pipefail
mkdir -p ui-evidence
APP_ID="${APP_ID:-com.helboy.vpnclub.preview440}"
adb install -r "${APK_PATH:-app/build/outputs/apk/debug/app-debug.apk}"
adb shell pm grant "$APP_ID" android.permission.POST_NOTIFICATIONS
adb shell appops set "$APP_ID" ACTIVATE_VPN allow
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
nodes = [n for n in ET.parse('ui-evidence/current.xml').iter('node') if n.get('text') == sys.argv[1] or n.get('content-desc') == sys.argv[1]]
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
# MSN native transports must live entirely in Hellboy; no second
# launcher or MSN Activity may appear. Select a native mode using Hellboy UI.
click_label 'WireGuard'
sleep 2
adb shell uiautomator dump /sdcard/hellboy-ui.xml
adb pull /sdcard/hellboy-ui.xml ui-evidence/native-selected.xml
grep -q 'WireGuard' ui-evidence/native-selected.xml
if adb shell am start -W -n "$APP_ID/com.msnguard.vpn.MainActivity" > ui-evidence/forbidden-msn-activity.txt 2>&1; then
  echo 'Standalone MSN Activity was still launchable'; exit 1
fi
click_label 'خانه'
sleep 2
adb exec-out screencap -p > ui-evidence/native-home.png
adb shell uiautomator dump /sdcard/hellboy-ui.xml
adb pull /sdcard/hellboy-ui.xml ui-evidence/native-home.xml
grep -q 'WireGuard' ui-evidence/native-home.xml
# Exercise the real native service from the same Hellboy dial.
click_label 'اتصال هوشمند'
sleep 3
adb shell dumpsys activity services "$APP_ID" > ui-evidence/native-services.txt
if ! grep -q 'MsnGuardVpnService' ui-evidence/native-services.txt; then
  adb logcat -d -s MsnGuardVpnService:E AndroidRuntime:E > ui-evidence/native-service-errors.txt
  echo 'Hellboy dial did not launch embedded native service'; exit 1
fi
adb exec-out screencap -p > ui-evidence/native-connecting.png
# Stop native service with the same Hellboy button, then go back to Xray.
adb shell uiautomator dump /sdcard/hellboy-ui.xml
adb pull /sdcard/hellboy-ui.xml ui-evidence/native-connecting.xml
if grep -q 'لغو اتصال' ui-evidence/native-connecting.xml; then
  click_label 'لغو اتصال'
elif grep -q 'قطع اتصال' ui-evidence/native-connecting.xml; then
  click_label 'قطع اتصال'
else
  echo 'Native connection has no stop action'; exit 1
fi
sleep 2
click_label 'تنظیمات'
click_label 'خودکار Xray'
click_label 'خانه'
sleep 2
# Exercise the real search foreground service, then stop it during discovery.
click_label 'خانه'
click_label 'اتصال هوشمند'
sleep 3
adb exec-out screencap -p > ui-evidence/searching.png
adb shell uiautomator dump /sdcard/hellboy-ui.xml
adb pull /sdcard/hellboy-ui.xml ui-evidence/searching.xml
python3 - <<'PYASSERT'
import xml.etree.ElementTree as ET
root = ET.parse('ui-evidence/searching.xml')
labels = {n.get('content-desc','') for n in root.iter('node')}
assert 'لغو اتصال' in labels or 'قطع اتصال' in labels, 'Automatic search session did not start'
PYASSERT
if grep -q 'لغو اتصال' ui-evidence/searching.xml; then click_label 'لغو اتصال'; else click_label 'قطع اتصال'; fi
sleep 3
adb shell uiautomator dump /sdcard/hellboy-ui.xml
adb pull /sdcard/hellboy-ui.xml ui-evidence/stopped.xml
python3 - <<'PYASSERT'
import xml.etree.ElementTree as ET
labels = {n.get('content-desc','') for n in ET.parse('ui-evidence/stopped.xml').iter('node')}
assert 'اتصال هوشمند' in labels, 'Stop did not cancel the automatic search session'
PYASSERT
adb shell dumpsys activity services "$APP_ID" > ui-evidence/stopped-services.txt
if grep -q 'isForeground=true' ui-evidence/stopped-services.txt; then echo 'Foreground search still running after stop'; exit 1; fi
adb logcat -d > ui-evidence/logcat.txt
if grep -q 'FATAL EXCEPTION' ui-evidence/logcat.txt; then
  echo 'App crash detected'; exit 1
fi
