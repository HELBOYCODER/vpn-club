#!/bin/bash
# Puts the Aether core of a PattNG build into this project.
#
#   bash tools/update-aether-core.sh /path/to/PattNG.apk
#
# An APK is a zip file. The core (libaether.so) and the two programs it runs (libpsiphon-tunnel-core.so for Psiphon,
# liblyrebird.so for Tor bridges) sit in its lib/<abi>/ folders; this copies them, for every ABI the APK carries,
# into app/src/main/jniLibs/<abi>/, where the build picks them up. Rebuild the app afterwards.
set -o errexit
set -o pipefail
set -o nounset

__dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK="${1:-}"
[[ -f "$APK" ]] || { echo "usage: bash tools/update-aether-core.sh /path/to/PattNG.apk"; exit 1; }
command -v unzip >/dev/null 2>&1 || { echo "unzip is not installed"; exit 1; }

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
unzip -q -o "$APK" 'lib/*' -d "$work" || { echo "no lib/ folder in $APK"; exit 1; }

copied=0
for abi_dir in "$work"/lib/*/; do
  abi="$(basename "$abi_dir")"
  if [[ ! -f "$abi_dir/libaether.so" ]]; then
    echo "[skip] $abi: no libaether.so"
    continue
  fi
  target="$__dir/app/src/main/jniLibs/$abi"
  mkdir -p "$target"
  for name in libaether.so libpsiphon-tunnel-core.so liblyrebird.so; do
    if [[ -f "$abi_dir/$name" ]]; then
      cp "$abi_dir/$name" "$target/$name"
      echo "[ok] $abi/$name ($(wc -c < "$abi_dir/$name") bytes)"
      copied=$((copied + 1))
    else
      echo "[keep] $abi/$name is not in the APK; the one of the project stays"
    fi
  done
done

[[ "$copied" -gt 0 ]] || { echo "nothing copied: the APK has no libaether.so (is it a PattNG build with Aether?)"; exit 1; }
echo "done. Rebuild the app: ./gradlew assembleDebug"
