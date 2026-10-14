# AGENTS.md — Excl

Excl (`origin` → `ctqvva/Excl`, branch `dev`) is a privacy-hardened Android
proxy-client fork of Exclave (`upstream` → `dyhkwong/Exclave`). It exists to
stop on-device apps from learning the active VPN server IP through local
proxy inbounds, injected HTTP proxies, custom-config listeners, or the VPN
bypass API. Guiding principle: make the VPN non-exploitable, not invisible
(`tun0` visibility itself is unpatchable app-side). Fork identity lives in
code: app name `Excl`, `Excl` log tags, Balancer / AmneziaWG / VkTurn
support, privacy-hardening defaults. There is no unit test suite and no
configured linter; verification means the compile/build commands below.

## Commands (run from repo root)

```sh
git status --short && git log --oneline --graph -8 && git submodule status
./gradlew :app:kspOssDebugKotlin      # regenerate Room schema in app/schemas
./gradlew :app:compileOssDebugKotlin  # fastest real verification (needs core AAR)
./gradlew :app:assembleOssDebug       # full debug APK
EXCLAVE_APPLICATION_ID=com.example.camera EXCLAVE_APP_LABEL=Camera ./scripts/build-stealth.sh
```

Rebuild the Go core when `library/core/*` or Kotlin core-API usage changes
(see `library/core/AGENTS.md`). Never hand-edit the generated
`app/build/generated/ksp/**`.

## Structure

- `app/` — Android app (`oss` flavor + `play` source set, debug/release).
- `library/core/` — gomobile Go core, builds `app/libs/libexclavecore.aar`.
  Has its own `AGENTS.md`.
- `library/proto`, `library/proto-stub`, `plugin/api`, `plugin/naive` —
  Gradle modules; `plugin/naive/src/main/jni/naiveproxy` is a git submodule.
- `app/schemas/…/*.json` — Room schema exports. Commit with the entity change.
- Profile tests use a dedicated Material 3 Expressive sheet; `src/legacy` overrides
  its theme with Material 3 for the Android 5/6 compatible Material library.
  Results are private preferences, not Room fields; see `docs/profile-tests.md`.
- `docs/privacy-hardening.md`, `docs/stealth-build.md` — read before touching
  those areas.
- Excl-local subsystems (no upstream counterpart — never "align" them away):
  `privacy/` (`PrivacyHoneypot`, `PrivacyDefaults`, `AppDisguiseManager`),
  balancer (`BalancerRuntimeState`, `BalancerDisplayResolver`),
  `fmt/vkturn/` + `bin/plugin/vkturn.sh`, AmneziaWG in `fmt/wireguard/`,
  `fmt/byebyedpi/` + `core/ByeDpiProxy` (`libbyedpi.so` via CMake).

## Do

- Diagnose first: `git status`, recent log, submodule status. If
  `.git/MERGE_HEAD` exists, finish or abort the merge before other work.
- Keep Excl deltas when touching files upstream also touches
  (`SagerNet.kt`, `DataStore.kt`, `ProxyEntity.kt`, `*Fmt.kt`,
  `ConfigurationFragment.kt`, `SettingsPreferenceFragment.kt`,
  `strings.xml`, `library/core/build*.sh|debug*.sh`).
- Keep `TYPE_VKTURN = 30` / `TYPE_SNELL = 31`. Upstream uses 30 for Snell,
  but Excl shipped VkTurn as 30 first — renumbering either breaks stored
  profiles. The comment in `ProxyEntity.kt` explains this; do not "fix" it.
- Keep README title `# Excl`, `Excl` log tags, VkTurn plugin wiring
  (`vkturn-plugin`/`libvkturn.so`). Do not re-add the ShadowQUIC plugin —
  upstream moved ShadowQUIC into the core; only the plugin is gone.
- Run `git diff --check` before every commit.
- Commit one logical step at a time with an imperative subject
  (`Fix …`, `Merge …`, `Harden …`); merge commits state the upstream ref
  and the Excl behavior preserved. Stage paths explicitly.
- Update this file in the same commit that changes a convention it documents.

## Do not

- Do not commit secrets (`local.properties`,
  `/service_account_credentials.json`, `*.keystore`/`*.jks`/`*.p12`/`*.pfx`)
  or build outputs (`*.apk`/`*.aab`, `app/**/release/output-metadata.json`,
  `app/**/release/baselineProfiles/`, `build/`, `.gradle/`, `.idea/`).
  `release.keystore` predates this rule and is still tracked — needs
  rotation/removal, never pile on.
- Do not use `git commit -a`, amend, push, rebase, or rewrite history unless
  explicitly requested. Do not leave uncommitted work unless asked.
- Do not bump submodule pins casually. Drift shows as `MM` — reset with
  `git submodule update --checkout -- <paths>` unless a bump is the task.
- Do not hand-edit `version.properties` or `app/.../aboutlibraries*`
  inside feature work (release chores).

## Ask first

- Weakening any privacy default, the honeypot, or bad-actor seeding
  (`docs/privacy-hardening.md` is the threat model — read it first).
- Deleting a protocol, flavor, or module (e.g. following an upstream removal
  that kills an Excl feature — check Excl-local references first).
- Changing profile `type` ints, Room entity fields, or the 36→37 migration
  path (upstream-36 DBs lack the vkTurn column; Excl→Excl upgrades are covered).
- Network-dependent operations (Go module downloads, full APK builds).

## Upstream merges

1. `git fetch upstream`; note the exact `upstream/dev` SHA.
2. `git merge upstream/dev`. Partial-clone caveat: submodule commits cannot
   be fetched from the superproject promisor (`not our ref`). If the merge
   dies on one, temporarily link the submodule stores, then remove the file:
   `printf '%s\n' "$PWD/.git/modules/<mod>/objects" \
>> .git/objects/info/alternates` … `rm .git/objects/info/alternates`.
3. Resolve keeping Excl deltas; reconcile `.gitignore` by union (keep the
   fork's secret/output rules even if upstream slimmed theirs).
4. If entities changed, run `:app:kspOssDebugKotlin` and cross-check
   `app/schemas/…/37.json` (`identityHash` and `setupQueries`) against the
   hash in generated `SagerDatabase_Impl.kt`.
5. Confirm Excl markers
   (`grep -rn "Excl\|AMNEZIA_WG\|vkTurnBean" app/src/main`) and commit with
   the upstream SHA in the message.

## Done means

`git diff --check` clean, documented verification command green, Excl
markers present after merges, `git status` shows only intended paths, one
commit per logical step.
