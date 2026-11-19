#!/usr/bin/env bash
set -euo pipefail

# 从 Termux 官方仓库获取arm64二进制，并以 Android 可执行的 .so 名称放入 jniLibs。
# 依赖：curl、ar、tar、sha256sum、patchelf

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="$ROOT_DIR/app/src/main/jniLibs/arm64-v8a"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

BASE_URL="https://packages.termux.dev/apt/termux-main"

declare -A PACKAGES
PACKAGES[proot]="pool/main/p/proot/proot_5.1.107.102_aarch64.deb|10a394b585c2fc2b2c8502544f4e5f1be581dc8bfff79e8ba62f16207423e030"
PACKAGES[libtalloc]="pool/main/libt/libtalloc/libtalloc_2.5.0_aarch64.deb|556591f43bb773ad8777e1a29522640866a55f95dab71914418b94a8c58ad5a7"
PACKAGES[libandroid-shmem]="pool/main/liba/libandroid-shmem/libandroid-shmem_0.7_aarch64.deb|0da3a24d558b93c92bcf8d611e0826a99ff96e396b148e6cdf33b47c47c57ff6"

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
