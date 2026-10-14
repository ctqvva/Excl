# AGENTS.md — library/core (Go core)

Gomobile wrapper that builds `app/libs/libexclavecore.aar` from the external
`github.com/exclavenetwork/libexclavecore` module (pinned in `go.mod`).

## Commands (scripts also accept invocation from the repository root)

```sh
export ANDROID_HOME=$HOME/Library/Android/sdk
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/29.0.14206865
./build.sh    # release AAR -> ../../app/libs/libexclavecore.aar (needs network + gomobile + NDK)
./debug.sh    # debug variant
go test -race profileprobe/profile_probe.go profileprobe/profile_probe_test.go  # offline probe transport checks
```

## Do

- Rebuild the AAR whenever `go.mod`/`go.sum`/`main.go`/`patches/` change, and
  before trusting Kotlin compile errors about missing `libexclavecore` APIs
  (`URL.hasPort`, `AndroidCAStore`, …) — they usually mean a stale AAR.
- Check `go list -m -f '{{.Dir}}' <module>` prints a real module-cache path
  before running `build.sh`. Never let it run with an empty dir (it would
  copy the filesystem root into tmp).
- Keep profile-probe bindings: both scripts copy `profileprobe/profile_probe*.go`
  (excluding tests) into the temporary libexclavecore module before gomobile bind.
  Probes dispatch through the tested instance, expose cancellation, and open no listeners.
- Both scripts must create `app/libs` and publish the rebuilt AAR there, including
  on a clean checkout. Gradle's `verifyNativeCoreBindings` runs before app builds
  and reports a missing/stale AAR with rebuild instructions.
- Keep AmneziaWG support: `build.sh` rewrites + patches `exclave-core`
  (`patches/v2ray-amneziawg.patch`). Verify the patch still applies after
  core version bumps.

## Do not

- Do not commit `*.aar` / `*.jar` outputs, and do not hand-edit `go.sum`.
- Do not bump the `libexclavecore` pin without rebuilding and recompiling
  the app (`./gradlew :app:compileOssDebugKotlin` from repo root).
