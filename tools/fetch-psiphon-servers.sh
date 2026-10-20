#!/bin/bash
set -o errexit
set -o pipefail
set -o nounset
__dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Fetches the newest Psiphon server list for the build: the signed, compressed list Psiphon's client downloads for
# itself. It goes into the app's assets as psiphon_servers.dat; at start the app (PsiphonServerList) checks it against
# Psiphon's public key, unpacks it, and hands it to the Aether core as --psiphon-server-entries, so a fresh install
# has servers before it can reach the list on its own. A list copied into the app's files folder under the same
# name takes the place of the bundled one.
#
# The list is checked here against the same key before it ships, so a broken or foreign download never does.
# Ported from PattNG's fetch-psiphon-servers.sh.

KEY_SOURCE="$__dir/app/src/main/java/dev/valid7996/narcicgeteway/core/PsiphonServerList.kt"
TARGET="$__dir/app/src/main/assets/psiphon_servers.dat"
URL="${PSIPHON_SERVERS_URL:-https://s3.amazonaws.com//psiphon/web/mjr4-p23r-puwl/server_list_compressed}"

PY=""
for candidate in python3 python; do
  if "$candidate" -c "import zlib" >/dev/null 2>&1; then PY="$candidate"; break; fi
done
[[ -n "$PY" ]] || { echo "python not found"; exit 1; }

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

echo "[psiphon-servers] fetching $URL"
curl -fsSL --retry 3 --retry-delay 5 --max-time 180 -o "$work/list" "$URL"

"$PY" - "$work" "$KEY_SOURCE" <<'PYEOF'
import base64, hashlib, json, re, sys, zlib
work, key_source = sys.argv[1], sys.argv[2]
source = open(key_source, encoding="utf-8").read()
found = re.search(r'SIGNING_KEY =\s*(.*?)\n\n', source, re.S)
if not found:
    sys.exit("the signing key was not found in " + key_source)
key = "".join(re.findall(r'"([^"]*)"', found.group(1)))
package = json.loads(zlib.decompress(open(work + "/list", "rb").read()))
if base64.b64decode(package["signingPublicKeyDigest"]) != hashlib.sha256(key.encode()).digest():
    sys.exit("the list names another key than the one the app carries")
data = package["data"]
entries = [line for line in data.split("\n") if line.strip()]
if len(entries) < 50:
    sys.exit("only %d server entries in the list; that is not the list" % len(entries))
open(work + "/data.bin", "wb").write(data.encode("utf-8"))
open(work + "/sig.bin", "wb").write(base64.b64decode(package["signature"]))
pem = "-----BEGIN PUBLIC KEY-----\n" + "\n".join(key[i:i + 64] for i in range(0, len(key), 64)) + "\n-----END PUBLIC KEY-----\n"
open(work + "/key.pem", "w").write(pem)
print("[psiphon-servers] %d server entries" % len(entries))
PYEOF

# RSA PKCS#1 v1.5 with SHA-256 over the entry text, as the client checks it.
openssl dgst -sha256 -verify "$work/key.pem" -signature "$work/sig.bin" "$work/data.bin"

mkdir -p "$(dirname "$TARGET")"
cp "$work/list" "$TARGET"
echo "[psiphon-servers] staged:"
ls -la "$TARGET"
