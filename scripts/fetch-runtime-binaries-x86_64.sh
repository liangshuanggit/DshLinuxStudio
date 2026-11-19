#!/usr/bin/env bash
set -euo pipefail

# 从 Termux 官方仓库获取 x86_64 二进制，并以 Android 可执行的 .so 名称放入 jniLibs。
# 用途：在 x86_64 模拟器（如 InstaBox 云电脑）里调试本应用，真机仍用 arm64 版。
# 依赖：curl、ar、tar、sha256sum、patchelf

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="$ROOT_DIR/app/src/main/jniLibs/x86_64"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

BASE_URL="https://packages.termux.dev/apt/termux-main"

declare -A PACKAGES
PACKAGES[proot]="pool/main/p/proot/proot_5.1.107.102_x86_64.deb|2497064226c15971be8131c7facead94a06c225347796326a383c832dca57758"
PACKAGES[libtalloc]="pool/main/libt/libtalloc/libtalloc_2.5.0_x86_64.deb|b8c6d95f20075dc1f9ec6573575b2444e8d526e48e0d8d6d5cf4e071e6e06530"
PACKAGES[libandroid-shmem]="pool/main/liba/libandroid-shmem/libandroid-shmem_0.7_x86_64.deb|ffa9e4c87467b158b148d0ff92dda796aa038276c2075af3269cdcdb06f25797"

mkdir -p "$OUT_DIR"

for name in proot libtalloc libandroid-shmem; do
  entry="${PACKAGES[$name]}"
  path="${entry%%|*}"
  sha="${entry##*|}"
  file="$TMP_DIR/$name.deb"
  echo "下载 $name ..."
  curl -fsSL "$BASE_URL/$path" -o "$file"
  echo "$sha  $file" | sha256sum -c -
  mkdir -p "$TMP_DIR/$name"
  (cd "$TMP_DIR/$name" && ar x "$file" && tar -xJf data.tar.xz)
done

PROOT="$TMP_DIR/proot/data/data/com.termux/files/usr/bin/proot"
LOADER="$TMP_DIR/proot/data/data/com.termux/files/usr/libexec/proot/loader"
TALLOC="$TMP_DIR/libtalloc/data/data/com.termux/files/usr/lib/libtalloc.so.2.5.0"
SHMEM="$TMP_DIR/libandroid-shmem/data/data/com.termux/files/usr/lib/libandroid-shmem.so"

for f in "$PROOT" "$LOADER" "$TALLOC" "$SHMEM"; do
  [ -f "$f" ] || { echo "缺少文件：$f" >&2; exit 1; }
done

# Android 包管理器只识别 lib*.so；把 libtalloc.so.2 改名为 libtalloc2.so。
patchelf --replace-needed libtalloc.so.2 libtalloc2.so "$PROOT"

cp "$PROOT" "$OUT_DIR/libproot.so"
cp "$LOADER" "$OUT_DIR/libprootloader.so"
cp "$TALLOC" "$OUT_DIR/libtalloc2.so"
cp "$SHMEM" "$OUT_DIR/libandroid-shmem.so"
chmod 0644 "$OUT_DIR"/*.so

echo "完成：$OUT_DIR"
file "$OUT_DIR"/*.so || true
