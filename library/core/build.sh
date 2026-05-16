#!/bin/bash

set -euo pipefail

export PATH="$PATH:$HOME/go/bin"

workdir="$(mktemp -d "${TMPDIR:-/tmp}/excl-core.XXXXXX")"
orig_mod="$workdir/original.go.mod"
orig_sum="$workdir/original.go.sum"
cp go.mod "$orig_mod"
cp go.sum "$orig_sum"
restore() {
  cp "$orig_mod" go.mod
  cp "$orig_sum" go.sum
  rm -rf "$workdir"
}
trap restore EXIT

amneziawg_go_version="${AMNEZIAWG_GO_VERSION:-v0.2.18}"
awg_src="$(go env GOMODCACHE)/github.com/amnezia-vpn/amneziawg-go@$amneziawg_go_version"
if [ ! -d "$awg_src" ]; then
  go mod download "github.com/amnezia-vpn/amneziawg-go@$amneziawg_go_version"
fi
awg_tmp="$workdir/wireguard"
cp -R "$awg_src"/. "$awg_tmp"
chmod -R u+w "$awg_tmp"
find "$awg_tmp" -type f \( -name '*.go' -o -name 'go.mod' \) -print0 \
  | xargs -0 perl -0pi -e 's#github.com/amnezia-vpn/amneziawg-go#golang.zx2c4.com/wireguard#g'

v2ray_src="$(go list -m -f '{{.Dir}}' github.com/v2fly/v2ray-core/v5)"
v2ray_tmp="$workdir/v2ray-core"
cp -R "$v2ray_src"/. "$v2ray_tmp"
chmod -R u+w "$v2ray_tmp"
patch -p1 -d "$v2ray_tmp" < patches/v2ray-amneziawg.patch

go mod edit -replace=golang.zx2c4.com/wireguard="$awg_tmp"
go mod edit -replace=github.com/v2fly/v2ray-core/v5="$v2ray_tmp"

CGO_LDFLAGS="-Wl,-z,max-page-size=16384" gomobile bind -v -androidapi 21 -trimpath -ldflags="-s -buildid=" -tags="with_clash" "github.com/dyhkwong/libsagernetcore" || exit 1

proj=../../app/libs
if [ -d $proj ]; then
  cp -vf libsagernetcore.aar $proj
fi
