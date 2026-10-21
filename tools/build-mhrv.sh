#!/usr/bin/env bash
# Builds libmhrv_rs.so (the MasterHttpRelayVPN relay core, MIT) for the three Android ABIs from a pinned
# source revision and drops it into app/src/main/jniLibs. Needs: rustup/cargo with the three android targets,
# and an Android NDK (ANDROID_NDK_HOME or the newest one under $ANDROID_HOME/ndk).
set -e -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO=https://github.com/therealaleph/MasterHttpRelayVPN-RUST.git
REV=b32f65f
SRC="$ROOT/reference/MasterHttpRelayVPN-RUST"
API=21

ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
NDK="${ANDROID_NDK_HOME:-$(ls -d "$ANDROID_HOME"/ndk/*/ | sort -V | tail -1 | sed 's:/*$::')}"
TC="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
[ -d "$TC" ] || { echo "no NDK toolchain at $TC" >&2; exit 1; }

rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android >/dev/null

if [ ! -d "$SRC/.git" ]; then
  mkdir -p "$(dirname "$SRC")"
  git clone -q --filter=blob:none "$REPO" "$SRC"
fi
git -C "$SRC" fetch -q origin "$REV" 2>/dev/null || git -C "$SRC" fetch -q origin
git -C "$SRC" checkout -q "$REV"

build() { # <rust target> <clang prefix> <abi dir>
  local target="$1" clang="$2" abi="$3"
  local up; up="$(echo "$target" | tr 'a-z-' 'A-Z_')"
  local lc; lc="$(echo "$target" | tr '-' '_')"
  export "CARGO_TARGET_${up}_LINKER=$TC/${clang}${API}-clang"
  export "CC_${lc}=$TC/${clang}${API}-clang"
  export "AR_${lc}=$TC/llvm-ar"
  export RUSTFLAGS="-C link-arg=-Wl,-z,max-page-size=16384"
  ( cd "$SRC" && cargo build --release --lib --target "$target" )
  mkdir -p "$ROOT/app/src/main/jniLibs/$abi"
  cp "$SRC/target/$target/release/libmhrv_rs.so" "$ROOT/app/src/main/jniLibs/$abi/libmhrv_rs.so"
  echo "built $abi: $(stat -c %s "$ROOT/app/src/main/jniLibs/$abi/libmhrv_rs.so") bytes"
}

TARGETS="${TARGETS:-arm64-v8a armeabi-v7a x86_64}"
for abi in $TARGETS; do
  case "$abi" in
    arm64-v8a)   build aarch64-linux-android aarch64-linux-android arm64-v8a ;;
    armeabi-v7a) build armv7-linux-androideabi armv7a-linux-androideabi armeabi-v7a ;;
    x86_64)      build x86_64-linux-android x86_64-linux-android x86_64 ;;
  esac
done
