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
cp "$SOURCE/AndroidManifest.xml" "$ROOT/msnengine/src/main/AndroidManifest.xml"
cp "$TMP/upstream/app/libs/psiphon.aar" "$ROOT/app/libs/psiphon-msn.aar"
mkdir -p "$ROOT/app/src/main/assets/licenses"
cp "$TMP/upstream/LICENSE" "$ROOT/app/src/main/assets/licenses/MSN-GUARD-AGPL-3.0.txt"

# Keep all original MSN transport services. Remove duplicate launcher icon.
python3 - "$ROOT/msnengine/src/main/AndroidManifest.xml" <<'PY'
import sys
import xml.etree.ElementTree as ET
path = sys.argv[1]
uri = "http://schemas.android.com/apk/res/android"
ET.register_namespace("android", uri)
tree = ET.parse(path)
root = tree.getroot()
for activity in root.findall(".//activity"):
    if activity.get("{%s}name" % uri) != ".MainActivity":
        continue
    activity.set("{%s}exported" % uri, "false")
    for intent_filter in list(activity.findall("intent-filter")):
        categories = [x.get("{%s}name" % uri) for x in intent_filter.findall("category")]
        if "android.intent.category.LAUNCHER" in categories or "android.intent.category.LEANBACK_LAUNCHER" in categories:
            activity.remove(intent_filter)
tree.write(path, encoding="utf-8", xml_declaration=True)
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
        if parts[2] == "libhev-socks5-tunnel.so":
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
echo "MSN-GUARD $TAG / $COMMIT staged into Hellboy Android library."
