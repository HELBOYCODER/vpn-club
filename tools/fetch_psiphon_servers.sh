#!/usr/bin/env bash
# دریافت لیست عمومی سرورهای سایفون (همان لیست امضاشده‌ای که خود کلاینت سایفون دانلود می‌کند) و
# بررسی امضای RSA آن با کلید عمومی سایفون — مثل fetch-psiphon-servers.sh در PattNG.
# خروجی: app/src/main/assets/psiphon_servers.dat (فشرده‌ی اصلی؛ اپ هنگام اجرا بازش می‌کند)
# اگر دانلود یا امضا ناموفق بود، فایل قبلی دست نمی‌خورد (خطا نمی‌دهد مگر STRICT=1).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
URL="https://s3.amazonaws.com//psiphon/web/mjr4-p23r-puwl/server_list_compressed"
TARGET="$ROOT/app/src/main/assets/psiphon_servers.dat"
KEY="MIICIDANBgkqhkiG9w0BAQEFAAOCAg0AMIICCAKCAgEAt7Ls+/39r+T6zNW7GiVpJfzq/xvL9SBH5rIFnk0RXYEYavax3WS6HOD35eTAqn8AniOwiH+DOkvgSKF2caqk/y1dfq47Pdymtwzp9ikpB1C5OfAysXzBiwVJlCdajBKvBZDerV1cMvRzCKvKwRmvDmHgphQQ7WfXIGbRbmmk6opMBh3roE42KcotLFtqp0RRwLtcBRNtCdsrVsjiI1Lqz/lH+T61sGjSjQ3CHMuZYSQJZo/KrvzgQXpkaCTdbObxHqb6/+i1qaVOfEsvjoiyzTxJADvSytVtcTjijhPEV6XskJVHE1Zgl+7rATr/pDQkw6DPCNBS1+Y6fy7GstZALQXwEDN/qhQI9kWkHijT8ns+i1vGg00Mk/6J75arLhqcodWsdeG/M/moWgqQAnlZAGVtJI1OgeF5fsPpXu4kctOfuZlGjVZXQNW34aOzm8r8S0eVZitPlbhcPiR4gT/aSMz/wd8lZlzZYsje/Jr8u/YtlwjjreZrGRmG8KMOzukV3lLmMppXFMvl4bxv6YFEmIuTsOhbLTwFgh7KYNjodLj/LsqRVfwz31PgWQFTEPICV7GCvgVlPRxnofqKSjgTWI4mxDhBpVcATvaoBl1L/6WLbFvBsoAUBItWwctO2xalKxF5szhGm8lccoc5MZr8kfE0uxMgsxz4er68iCID+rsCAQM="
work="$(mktemp -d)"; trap 'rm -rf "$work"' EXIT
fail() { echo "[psiphon-servers] $1"; if [ "${STRICT:-0}" = 1 ]; then exit 1; else exit 0; fi; }
curl -fsSL --retry 3 --retry-delay 5 --max-time 180 -o "$work/list" "$URL" || fail "download failed — keeping bundled list"
python3 -I - "$work" "$KEY" <<'PY' || fail "list unpack/check failed — keeping bundled list"
import base64, hashlib, json, sys, zlib
work, key = sys.argv[1], sys.argv[2]
p = json.loads(zlib.decompress(open(work + "/list", "rb").read()))
assert base64.b64decode(p["signingPublicKeyDigest"]) == hashlib.sha256(key.encode()).digest(), "foreign key"
entries = [l for l in p["data"].split("\n") if l.strip()]
assert len(entries) >= 50, "too few entries: %d" % len(entries)
open(work + "/data.bin", "wb").write(p["data"].encode())
open(work + "/sig.bin", "wb").write(base64.b64decode(p["signature"]))
open(work + "/key.pem", "w").write("-----BEGIN PUBLIC KEY-----\n" + "\n".join(key[i:i+64] for i in range(0, len(key), 64)) + "\n-----END PUBLIC KEY-----\n")
print("[psiphon-servers] %d entries" % len(entries))
PY
openssl dgst -sha256 -verify "$work/key.pem" -signature "$work/sig.bin" "$work/data.bin" || fail "signature check failed — keeping bundled list"
mkdir -p "$(dirname "$TARGET")"; cp "$work/list" "$TARGET"; ls -la "$TARGET"
