#!/usr/bin/env bash
# Build-time source integration of MSN-GUARD into the Hellboy APK.
set -euo pipefail

UPSTREAM=https://github.com/mbm110/MSN-GUARD.git
TAG=v3.0.0
COMMIT=5fc12b8e17dba95741507dcb8303456ec5b4c65f
RELEASE=https://github.com/mbm110/MSN-GUARD/releases/download/${TAG}
APK=MSN-GUARD-3.0.0-universal.apk
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

git clone --quiet --depth 1 --branch "$TAG" "$UPSTREAM" "$TMP/upstream"
ACTUAL="$(git -C "$TMP/upstream" rev-parse HEAD)"
if [[ "$ACTUAL" != "$COMMIT" ]]; then
  echo "Pinned MSN-GUARD commit mismatch ($ACTUAL != $COMMIT)" >&2
  exit 1
fi

mkdir -p "$ROOT/msnengine/src/main" "$ROOT/app/libs"
SOURCE="$TMP/upstream/app/src/main"
for dir in java res assets; do
  test -d "$SOURCE/$dir"
  cp -a "$SOURCE/$dir" "$ROOT/msnengine/src/main/"
done
# Hellboy already embeds hev-htproxy.aar, whose JNI binding class is
# hev.htproxy.TProxyService. A duplicate copy from MSN makes D8 merge fail.
# Keep the original Hellboy AAR so both engines bind to the same Java type.
rm -f "$ROOT/msnengine/src/main/java/hev/htproxy/TProxyService.java"
cp "$SOURCE/AndroidManifest.xml" "$ROOT/msnengine/src/main/AndroidManifest.xml"
cp "$TMP/upstream/app/libs/psiphon.aar" "$ROOT/app/libs/psiphon-msn.aar"
mkdir -p "$ROOT/app/src/main/assets/licenses"
cp "$TMP/upstream/LICENSE" "$ROOT/app/src/main/assets/licenses/MSN-GUARD-AGPL-3.0.txt"

# Keep MSN's *transport services*; remove MSN's standalone UI/tile.
# Hellboy owns the only launcher, settings, status and quick tile.
python3 - "$ROOT/msnengine/src/main/AndroidManifest.xml" <<'PY'
import sys
import xml.etree.ElementTree as ET
path = sys.argv[1]
uri = "http://schemas.android.com/apk/res/android"
ET.register_namespace("android", uri)
tree = ET.parse(path)
root = tree.getroot()
app = root.find("application")
# The main Hellboy manifest exclusively owns app name, branding, and theme.
for key in ("label", "icon", "roundIcon", "theme"):
    app.attrib.pop("{%s}%s" % (uri, key), None)
# The native VpnService, background jobs and JNI resources stay intact.
# Remove standalone MSN Activity and its tile (Hellboy has its own).
for activity in list(app.findall("activity")):
    if activity.get("{%s}name" % uri) in (".MainActivity", ".ShadeCollapseActivity"):
        app.remove(activity)
for service in list(app.findall("service")):
    if service.get("{%s}name" % uri) == ".MsnGuardTileService":
        app.remove(service)

# Different authorities require different provider component names.
for provider in app.findall("provider"):
    if provider.get("{%s}name" % uri) == "androidx.core.content.FileProvider":
        provider.set("{%s}name" % uri, "com.msnguard.vpn.MsnGuardFileProvider")
tree.write(path, encoding="utf-8", xml_declaration=True)
provider_class = __import__("pathlib").Path(path).parent / "java/com/msnguard/vpn/MsnGuardFileProvider.kt"
provider_class.write_text(
    "package com.msnguard.vpn\n\n"
    "/** Dedicated provider so Hellboy log shares do not shadow MSN shares. */\n"
    "class MsnGuardFileProvider : androidx.core.content.FileProvider()\n"
)
# Native notifications must open Hellboy instead of the removed MSN activity.
service_file = __import__("pathlib").Path(path).parent / "java/com/msnguard/vpn/MsnGuardVpnService.kt"
source = service_file.read_text()
old = "Intent(this, MainActivity::class.java).apply {"
new = "(packageManager.getLaunchIntentForPackage(packageName) ?: Intent(Intent.ACTION_MAIN).setPackage(packageName)).apply {"
assert old in source, "MSN notification launcher hook moved upstream"
source = source.replace(old, new)
source = source.replace('setSession("MSN-GUARD")', 'setSession("Hellboy VPN")')
source = source.replace('setSession("MSN-GUARD — Kill Switch")', 'setSession("Hellboy VPN — Kill Switch")')
source = source.replace('.setContentTitle(title)', '.setContentTitle("Hellboy VPN · " + title)')
source = source.replace('.setLargeIcon(appBadge())', '')  # Remove MSN artwork from the notification
service_file.write_text(source)
PY

# Use release-matched upstream binaries with verified upstream checksums.
curl --fail --show-error --silent --location --retry 3 "$RELEASE/SHA256SUMS.txt" -o "$TMP/SHA256SUMS.txt"
curl --fail --show-error --silent --location --retry 3 "$RELEASE/$APK" -o "$TMP/$APK"
(cd "$TMP" && grep -F "$APK" SHA256SUMS.txt | sha256sum --check --status) || {
    echo "MSN-GUARD APK checksum verification failed" >&2
    exit 1
}
python3 - "$TMP/$APK" "$ROOT/msnengine/src/main/jniLibs" <<'PY'
import sys, zipfile, pathlib
archive, outdir = sys.argv[1:]
required = {"libaether.so", "libaether_jni.so", "libtun2socks.so", "libtor.so", "libobfs4proxy.so"}
abis = {"arm64-v8a", "armeabi-v7a", "x86_64"}
with zipfile.ZipFile(archive) as z:
    extracted = {}
    for member in z.namelist():
        parts = member.split("/")
        if len(parts) != 3 or parts[0] != "lib" or parts[1] not in abis or not member.endswith(".so"):
            continue
        # Already supplied by Hellboy's hev AAR.
        if parts[2] in {"libhev-socks5-tunnel.so", "libgojni.so"}:
            continue
        dest = pathlib.Path(outdir) / parts[1] / parts[2]
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(z.read(member))
        extracted.setdefault(parts[1], set()).add(parts[2])
    for abi in abis:
        missing = required - extracted.get(abi, set())
        if missing:
            raise SystemExit(f"Missing native engines for {abi}: {', '.join(sorted(missing))}")
    print("MSN-GUARD JNI packaged:", {key: len(value) for key, value in extracted.items()})
PY

# One GoMobile library remains (Psiphon). Xray runs as the independent
# libxray.so executable, which has its own process and own Go runtime.
# Refuse to stage a second GoJNI copy: pickFirst is not a safe solution.
python3 - "$ROOT/msnengine/src/main/jniLibs" "$ROOT/app/libs/psiphon-msn.aar" <<'PYVERIFY'
import sys, pathlib, zipfile
staged = pathlib.Path(sys.argv[1])
assert not list(staged.rglob("libgojni.so")), "Duplicate libgojni was staged"
with zipfile.ZipFile(sys.argv[2]) as a:
    for abi in ("arm64-v8a", "armeabi-v7a", "x86_64"):
        assert f"jni/{abi}/libgojni.so" in a.namelist(), f"Psiphon gojni missing: {abi}"
print("GoMobile collision resolved by a single Psiphon runtime and process-isolated Xray")
PYVERIFY

echo "MSN-GUARD $TAG / $COMMIT staged into Hellboy Android library."
