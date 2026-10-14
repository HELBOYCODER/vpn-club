#!/usr/bin/env bash
# ساخت موتور سایفون (ConsoleClient) به‌صورت برنامه‌ی مستقل برای اندروید.
#
# فورک «شیر و خورشید» (CluvexStudio/psiphon-tunnel-core، شاخه‌ی shirokhorshid) — همان که PattNG/Aether
# می‌سازد: پروتکل‌های FRONTED-MEEK-CDN و اسکن لبه‌های CDN (FrontedMeekCDNScan*) برای اینترنت ایران.
#
# چرا برنامه‌ی جدا و نه ca.psiphon.aar؟ هر دو AARِ سایفون و Xray با gomobile ساخته شده‌اند و
# کلاس go.Seq و libgojni.so هم‌نام دارند — در یک اپ با هم جمع نمی‌شوند.
# باینری استاتیک linux (CGO_ENABLED=0) — مثل ریلیزهای اندروید Aether — روی هسته‌ی اندروید اجرا می‌شود.
# خروجی: app/src/main/jniLibs/<abi>/libpsiphon.so (از nativeLibraryDir اجرا می‌شود)
# مجوز psiphon-tunnel-core: GPL-3.0
set -euo pipefail
REPO="${PSIPHON_REPO:-https://github.com/CluvexStudio/psiphon-tunnel-core.git}"
BRANCH="${PSIPHON_BRANCH:-shirokhorshid}"
REV="${PSIPHON_REV:-83aa73b9b982e7421e00117f5b0c5aceb5dda452}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="${TMPDIR:-/tmp}/psiphon-src-sok"
if [ ! -d "$WORK/.git" ]; then
  git -c core.longpaths=true clone --quiet --depth 50 --branch "$BRANCH" "$REPO" "$WORK"
fi
git -C "$WORK" checkout --quiet "$REV" 2>/dev/null || {
  git -C "$WORK" fetch --quiet --depth 50 origin "$BRANCH" && git -C "$WORK" checkout --quiet "$REV"
} || { echo "build_psiphon: pinned commit unavailable; refusing an unverified branch head" >&2; exit 1; }
cd "$WORK"
export GOFLAGS=-mod=vendor CGO_ENABLED=0
LD="-s -w"
mkdir -p "$ROOT/app/src/main/jniLibs/arm64-v8a" "$ROOT/app/src/main/jniLibs/armeabi-v7a"
GOOS=linux GOARCH=arm64 go build -trimpath -ldflags "$LD" -o "$ROOT/app/src/main/jniLibs/arm64-v8a/libpsiphon.so" ./ConsoleClient
GOOS=linux GOARCH=arm GOARM=7 go build -trimpath -ldflags "$LD" -o "$ROOT/app/src/main/jniLibs/armeabi-v7a/libpsiphon.so" ./ConsoleClient
ls -la "$ROOT"/app/src/main/jniLibs/*/libpsiphon.so
