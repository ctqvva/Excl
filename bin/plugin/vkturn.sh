#!/usr/bin/env bash
set -euo pipefail

repo="${VKTURN_RELEASE_REPO:-cacggghp/vk-turn-proxy}"
tag="${VKTURN_RELEASE_TAG:-v1.8.3}"
base="https://github.com/${repo}/releases/download/${tag}"
root="app/src/main/jniLibs"

download() {
  abi="$1"
  asset="$2"
  out="${root}/${abi}/libvkturn.so"
  mkdir -p "${root}/${abi}"
  curl -fL "${base}/${asset}" -o "${out}"
  chmod 755 "${out}"
}

download armeabi-v7a client-android-arm
download arm64-v8a client-android-arm64

if [ -n "${VKTURN_ASSET_X86:-}" ]; then
  download x86 "${VKTURN_ASSET_X86}"
else
  echo "No upstream Android x86 client asset is published for ${repo}@${tag}; skipping x86." >&2
fi

if [ -n "${VKTURN_ASSET_X86_64:-}" ]; then
  download x86_64 "${VKTURN_ASSET_X86_64}"
else
  echo "No upstream Android x86_64 client asset is published for ${repo}@${tag}; skipping x86_64." >&2
fi
