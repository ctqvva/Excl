# Privacy Hardening Checklist

This fork carries local hardening for on-device VPN leak scenarios described in:

- https://habr.com/ru/articles/1020080/
- https://github.com/cherepavel/VPN-Detector
- https://github.com/loop-uh/yourvpndead
- https://github.com/xtclovver/RKNHardering

Guiding principle: make the VPN non-exploitable, not invisible. `tun0`,
split routes, and VPN-interface visibility are normal `VpnService`
fingerprints no ordinary app can hide — what we stop is on-device apps
using our local helpers as an oracle or proxy to learn the server IP.

## Threat model

Bad actor apps on the same device must not be able to:

- reuse a localhost proxy inbound to discover the active VPN server IP,
- abuse a local HTTP proxy injected into the system VPN stack,
- reach custom config listeners bound outside loopback,
- use Android's VPN bypass API to escape the tunnel unless the user explicitly allows it,
- stay inside the VPN when the user has marked them as forced-direct apps.

## Privacy controls

The app now exposes a dedicated privacy section with these controls enabled by default:

- `Require auth for local SOCKS5`
- `Allow UDP relay on local SOCKS5` disabled by default
- `Apps forced outside VPN`
- `Force loopback-only local inbounds`
- `Disable local HTTP proxy in VPN mode`
- `Harden custom configs`
- `Block app-requested VPN bypass`
- `Disable debug listeners`

## Expected behavior

- Generated local SOCKS5 inbounds use username/password auth by default.
- Local SOCKS5 UDP relay is disabled by default.
- Generated local inbounds stay on loopback even if the legacy LAN-access setting is enabled.
- VPN mode does not inject the local HTTP proxy when privacy hardening is enabled.
- Custom configs have `api` stripped, non-`tun` inbounds forced to loopback, and custom `socks` / `mixed` inbounds hardened.
- ByeByeDPI profiles always bind 127.0.0.1 on an ephemeral port; custom `--ip` / `-i` options in command lines are stripped at argv build time.
- Privacy bad-app packages are always excluded from the VPN at the OS `VpnService.Builder` layer.
- `builder.allowBypass()` is blocked by default.

## Release verification

For every release candidate:

1. Install the app with default privacy settings.
2. Connect to a test VPN server with a known public IP.
3. Install and run `VPN-Detector`.
4. Install and run `yourvpndead`.
5. Install and run `RKNHardering` (note: its proxy scan only flags
   unauthenticated proxies, and some checks need root — green is a
   "bypass not detected" signal, not proof).
6. Confirm detector apps cannot recover the VPN server IP through localhost proxy access.
7. Add detector apps to `Apps forced outside VPN` and confirm they are routed direct.
8. Enable a custom config with local inbounds and confirm only loopback listeners remain reachable.
9. Re-test after toggling `Require auth for local SOCKS5` off, to confirm the unsafe mode is obvious and intentional.

## Residual risk

- The in-app updater/probe HTTP client does not support authenticated SOCKS5 in the current core bindings, so those app-owned requests stop using SOCKS5 when local SOCKS auth is enabled.
- A hostile app routed through the VPN still learns the egress public IP from its own traffic. What we stop is use of our local helpers as an oracle or proxy.
- The honeypot (`PrivacyHoneypot`, default decoys `1080,10808,3128,8080,8888`)
  is a tripwire, not proof of safety; UID attribution can fail (`Not set`)
  when Android withholds the owner UID.
- The bad-actor seed list (`PrivacyDefaults`) is intentionally conservative —
  "every possible bad actor app" is not a solvable static list.
- Shortcut/Tasker intents are gated by a control token; old pinned shortcuts
  and Tasker actions must be recreated once after that change.
- API-only `vpn://` provisioning links still need online key provisioning.
- A full release gate still needs device-level validation with the detector apps above. Static review alone is not enough for this class of issue.

## Manual profile diagnostics

Country and speed tests dial the tested native instance directly and open no
local listener. The sheet identifies Cloudflare and the 10 MiB speed-test budget
before the user starts. Requests have deadlines and native cancellation; failure
does not select a direct-network fallback. See [profile tests](profile-tests.md).
