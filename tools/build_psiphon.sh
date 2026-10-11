#!/usr/bin/env bash
# ساخت موتور سایفون (psiphon-tunnel-core ConsoleClient) به‌صورت برنامه‌ی مستقل برای اندروید.
# چرا برنامه‌ی جدا و نه ca.psiphon.aar؟ هر دو AARِ سایفون و Xray با gomobile ساخته شده‌اند و
# کلاس go.Seq و libgojni.so هم‌نام دارند — در یک اپ با هم جمع نمی‌شوند.
# خروجی: app/src/main/jniLibs/<abi>/libpsiphon.so (از nativeLibraryDir اجرا می‌شود)
# مجوز psiphon-tunnel-core: GPL-3.0
set -euo pipefail
REV="${PSIPHON_REV:-d5b23279c4d21266a56e53f4ef3380dfa82f058d}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="${TMPDIR:-/tmp}/psiphon-src"
if [ ! -d "$WORK/.git" ]; then
  git clone --filter=blob:none https://github.com/Psiphon-Labs/psiphon-tunnel-core "$WORK"
fi
git -C "$WORK" fetch --depth 1 origin "$REV" && git -C "$WORK" checkout -q "$REV"
cd "$WORK"
export GOFLAGS=-mod=vendor CGO_ENABLED=0
LD="-s -w -checklinkname=0"
mkdir -p "$ROOT/app/src/main/jniLibs/arm64-v8a" "$ROOT/app/src/main/jniLibs/armeabi-v7a"
GOOS=android GOARCH=arm64 go build -trimpath -ldflags "$LD" -o "$ROOT/app/src/main/jniLibs/arm64-v8a/libpsiphon.so" ./ConsoleClient
# android/arm بدون cgo پشتیبانی نمی‌شود؛ باینری استاتیک لینوکس روی هسته‌ی اندروید اجرا می‌شود
GOOS=linux GOARCH=arm GOARM=7 go build -trimpath -ldflags "$LD" -o "$ROOT/app/src/main/jniLibs/armeabi-v7a/libpsiphon.so" ./ConsoleClient
ls -la "$ROOT"/app/src/main/jniLibs/*/libpsiphon.so
