# Privacy Hardening Checklist

This fork carries local hardening for on-device VPN leak scenarios described in:

- https://habr.com/ru/articles/1020080/
- https://github.com/cherepavel/VPN-Detector
- https://github.com/loop-uh/yourvpndead

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
- Privacy bad-app packages are always excluded from the VPN at the OS `VpnService.Builder` layer.
- `builder.allowBypass()` is blocked by default.

## Release verification

For every release candidate:

1. Install the app with default privacy settings.
2. Connect to a test VPN server with a known public IP.
3. Install and run `VPN-Detector`.
4. Install and run `yourvpndead`.
5. Confirm detector apps cannot recover the VPN server IP through localhost proxy access.
6. Add detector apps to `Apps forced outside VPN` and confirm they are routed direct.
7. Enable a custom config with local inbounds and confirm only loopback listeners remain reachable.
8. Re-test after toggling `Require auth for local SOCKS5` off, to confirm the unsafe mode is obvious and intentional.

## Residual risk

- The in-app updater/probe HTTP client does not support authenticated SOCKS5 in the current core bindings, so those app-owned requests stop using SOCKS5 when local SOCKS auth is enabled.
- A full release gate still needs device-level validation with the detector apps above. Static review alone is not enough for this class of issue.
