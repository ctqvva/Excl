# AGENTS.md — Excl

Excl (`origin` → `ctqvva/Excl`, branch `dev`) is a privacy-hardened Android
proxy-client fork of Exclave (`upstream` → `dyhkwong/Exclave`). Fork identity
lives in code/strings (app name `Excl`, `Excl` log tags, Balancer / AmneziaWG /
VkTurn support, privacy-hardening defaults) — **never let an upstream merge
silently rebrand it back to Exclave.**

## Progressive reading contract

Read this file only, then pull in deeper docs **only** when the task touches
their area:

| Area | Read next |
|---|---|
| Privacy behavior / leak-model changes | `docs/privacy-hardening.md` |
| Package rename, stealth builds | `docs/stealth-build.md`, `scripts/build-stealth.sh` |
| Upstream sync | “Merging upstream” below |

Repo map: `app/` (Android app, `oss`/`play` flavors), `library/` (Go core
bindings), `plugin/naive` + `plugin/shadowquic` (**git submodules**),
`buildSrc/`, `bin/`, `gradle/`, `scripts/`, `docs/`.

## Thinking protocol

1. **Diagnose first.** Before any write: `git status --short`,
   `git log --oneline --graph -8`, `git submodule status`. If a merge is in
   progress (`.git/MERGE_HEAD` exists), finish or abort it before starting
   anything else.
2. **Evidence before synthesis.** Read the files; cite `path:line` for code
   claims. `git ls-files -u` / `git diff --check` / `git grep "<<<<<<<"`
   settle merge questions — never guess.
3. **Smallest diff that solves the task.** When touching files upstream also
   touches (`SagerNet.kt`, `DataStore.kt`, `ProxyEntity.kt`, `*Fmt.kt`,
   `ConfigurationFragment.kt`, `SettingsPreferenceFragment.kt`,
   `strings.xml`, `library/core/build*.sh|debug*.sh`), keep Excl’s deltas.
4. **Verify, then commit.** `git diff --check` is mandatory. Build only what
   you changed: `./gradlew :app:assembleOssDebug`, or stealth via
   `EXCLAVE_APPLICATION_ID=… EXCLAVE_APP_LABEL=… ./scripts/build-stealth.sh`.

## Commit at every step (mandatory)

- One logical step = one commit. Finish the step, verify it, commit it —
  never batch unrelated work into a single commit and never leave a session
  with uncommitted work unless the user asked for it.
- Stage explicitly (`git add <paths>`). Never `git commit -a`; never commit
  strangers (`git status` must be clean of surprises first).
- Messages: imperative subject (`Fix …`, `Merge …`, `Harden …`), plus a body
  for merges and anything non-obvious. Merge commits must say **what
  upstream ref was merged and what Excl behavior was preserved**.
- Push, amend, rebase, or history rewrites only when explicitly requested.

## Never commit

- Secrets: `local.properties`, `/service_account_credentials.json`,
  `*.keystore`/`*.jks`/`*.p12`/`*.pfx`. (`release.keystore` predates this
  rule and is still tracked — needs rotation/removal, do **not** pile on.)
- Build outputs: `*.apk`/`*.aab`, `app/**/release/output-metadata.json`,
  `app/**/release/baselineProfiles/`, `build/`, `.gradle/`, `.idea/`.
- Shared vs local ignores: team-wide rules go in `.gitignore`; personal
  editor noise (`.vscode/`, `AGENTS.local.md`, …) goes in `.git/info/exclude`,
  which is never committed.

## Submodules & generated files

- `plugin/naive/...` (and any future submodules) are submodule checkouts.
  Don’t bump pins casually; drift shows as `MM` — reset with
  `git submodule update --checkout -- <paths>` unless a bump is the task.
- Partial clone caveat: this repo resolves `upstream` lazily
  (`blob:none`), and submodule commits cannot be fetched from the
  superproject promisor (`not our ref`). If a merge dies fetching a
  submodule SHA, temporarily link the submodule stores, merge, then
  remove the file again:
  `printf '%s\n' "$PWD/.git/modules/<mod>/objects" \
    >> .git/objects/info/alternates` … `rm .git/objects/info/alternates`.
- Room schema `app/schemas/…/*.json` regenerates when entities change —
  always commit it together with the entity change. If the identityHash
  looks stale, run `./gradlew :app:kspOssDebugKotlin` and cross-check it
  against the hash in the generated `SagerDatabase_Impl.kt`.
- `version.properties` + `app/.../aboutlibraries*` are release chores;
  don’t hand-edit them inside feature work.

## Merging upstream (safe procedure)

1. `git fetch upstream` and note the exact ref (`upstream/dev` SHA).
2. `git merge upstream/dev`; resolve with Excl deltas kept (see §3 above,
   especially README title `# Excl`, `Excl` log tags, Balancer/AWG/VkTurn,
   privacy defaults, stealth applicationId/label plumbing).
3. Reconcile `.gitignore` by union — keep this fork’s secret/output rules
   even if upstream slimmed theirs.
4. `git diff --check`, confirm Excl markers still present
   (`grep -rn "Excl\|AMNEZIA_WG\|vkTurnBean" app/src/main --include …`),
   then commit the merge with the upstream SHA in the message.
