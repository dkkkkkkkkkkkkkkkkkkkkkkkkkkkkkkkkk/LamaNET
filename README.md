# LamaVPN — Native Android VPN client

LamaVPN is an Android-native VPN client for the public volunteer relay network provided by [VPN Gate](https://www.vpngate.net/). It uses the official VPN Gate CSV directory over HTTPS and imports the embedded OpenVPN configuration for each selected relay into a bundled OpenVPN engine.

## Features

- Native Jetpack Compose interface (not a website or WebView)
- Live public server directory from VPN Gate
- Country flags, server search, country filters, and sorting by quality / ping / speed / sessions
- Smart Connect ranking and manual server selection
- Real OpenVPN tunnel through Android's VPNService
- Connection status from the OpenVPN engine, not a decorative UI toggle
- Live upload/download traffic telemetry, connection timer, diagnostics
- Pause/resume and retry/recovery flows included in the upstream engine/client
- Android 7.0+ (API 24+) with arm64-v8a and armeabi-v7a support

## Public server source

The server list is fetched at runtime from:

`https://www.vpngate.net/api/iphone/`

VPN Gate's CSV includes the hostname, IP, score, ping, throughput, country, uptime, sessions, and a Base64-encoded OpenVPN profile. LamaVPN uses that profile; it does **not** treat a raw IP address as a WireGuard endpoint.

The public relays are volunteered by third parties. They can disappear, be overloaded, or be operated by people you do not know. Do not use community relays for banking, sensitive accounts, or other confidential activity. VPN Gate documents a two-week logging policy on its public server listing. A VPN does not make an untrusted operator trustworthy.

## Source and licensing

This build is based on [OpenGate VPN by Iyed Amiri](https://github.com/IyedAmiri/OpenGateVPN), pinned to commit `12f8ea97976f1bac0307a013b40712098b865d43`, with LamaVPN branding and palette applied by `scripts/rebrand.py`.

The upstream project and bundled OpenVPN engine are GPL-3.0. This derivative is therefore published under GPL-3.0 as well. The upstream source, its license, and its acknowledgements are retained in the build input; the source can be reconstructed using the pinned commit and the branding script in this repository. See [VPN Gate](https://www.vpngate.net/) and [OpenVPN for Android](https://github.com/schwabe/ics-openvpn) for their respective project information.

## Build

Push to the `lamavpn` branch or run **Actions → Build LamaVPN Android APK**. The workflow checks out the pinned open-source engine, applies the branding script, and runs:

`./gradlew assembleDebug`

The APK is uploaded as the `LamaVPN-native-debug-apk` artifact.

## Important

This app connects to third-party public VPN relays. It cannot guarantee uninterrupted service, anonymity, zero logging, or a particular speed. Server status and connection quality must be checked live on the device.
