# LamaVPN

LamaVPN is a native Android VPN client for the public VPN Gate relay directory.

## Real protocol and tunnel

This build is based on the open-source OpenGateVPN Android client and its bundled OpenVPN engine. It reads the live VPN Gate CSV directory over HTTPS, extracts the OpenVPN profiles supplied by each relay, and launches the selected profile through Android's `VpnService`. It is not a website wrapper and does not use the unrelated WireGuard protocol for VPN Gate nodes.

## Features

- Live VPN Gate public relay directory.
- Country flags, server search, filtering, ping and advertised throughput.
- Smart ranking and server selection.
- Native OpenVPN tunnel using the bundled OpenVPN engine.
- Android VPN permission dialog and system VPN notification.
- Connection state, retry handling, session timer and traffic counters.
- Server switching, pause/resume and diagnostics logs.
- Premium dark navy / teal LamaVPN branding.

## Connect

1. Open LamaVPN and allow the server directory to load.
2. Choose a country/server or use the recommended server.
3. Tap Connect and accept Android's VPN permission dialog.
4. Wait until the OpenVPN engine reports that the tunnel is connected.

The app reports Connected only after the engine reports a completed OpenVPN connection. A selected row or an active connection attempt is not proof of a working tunnel.

## About the free servers

The public relays are volunteered by third parties. They can be slow, busy, offline or disappear without notice. They are not a guaranteed commercial VPN service. Do not use volunteer relays for banking, passwords, private work, or other sensitive traffic. The relay operator may be able to observe connection metadata and unencrypted traffic. Use HTTPS and other end-to-end encryption regardless of VPN status.

VPN Gate's published public-server username and password are `vpn` / `vpn`. LamaVPN retrieves profiles from the official VPN Gate API; it does not hard-code stale IP addresses from screenshots or old lists.

Official directory: https://www.vpngate.net/en/
Official CSV API: https://www.vpngate.net/api/iphone/

## Build from source

The original OpenGateVPN engine and application are included as a pinned Git submodule at `opengate-engine`. The GitHub Actions workflow checks out that source, applies the LamaVPN branding overlay, builds the Android APK, and uploads it as the `LamaVPN-debug-apk` artifact.

To reproduce locally:

```bash
git clone --recurse-submodules https://github.com/dkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkk/LamaNET.git
cd LamaNET
git checkout lamavpn
git submodule update --init --recursive
bash scripts/rebrand-lamavpn.sh
cd opengate-engine
./gradlew assembleDebug
```

APK output: `opengate-engine/app/build/outputs/apk/debug/app-debug.apk`.

## Open-source licenses and attribution

The upstream OpenGateVPN project is licensed under GPL-3.0 and includes the OpenVPN for Android-derived `vpnLib`. This LamaVPN distribution retains the upstream source and license through the pinned submodule. The LamaVPN branding overlay and this repository's integration workflow are provided for this GPL-3.0 derivative; see `opengate-engine/LICENSE` and `opengate-engine/vpnLib/LICENSE` for upstream license terms.

Upstream project: https://github.com/IyedAmiri/OpenGateVPN
VPN Gate: https://www.vpngate.net/
