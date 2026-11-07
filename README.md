# LamaVPN

A native Android VPN client built around the official WireGuard tunnel library.

## What is implemented

- Native Android dashboard with animated connection indicator.
- Real WireGuard userspace tunnel backend (no root required).
- Android VPN consent flow and system VPN interface.
- Import multiple standard WireGuard `.conf` profiles using Android's file picker.
- Validate each profile before saving.
- Encrypt imported configuration contents at rest with an AES-GCM key held in Android Keystore.
- Show live WireGuard transfer counters and recent handshake status.
- Remove stored profiles from the device.

## A real server is required

This app is a VPN **client**, not a VPN server provider. It does not ship with fake or borrowed proxy endpoints. To connect, import a valid WireGuard client configuration issued by a VPN provider or by an administrator of a WireGuard server you are authorized to use. A valid profile includes a client private key and server peer details (including endpoint and public key).

Keep the `.conf` file private. It contains a private key. Do not paste it into public GitHub issues, screenshots, or public chats. Import it directly on the phone.

The app can show a WireGuard tunnel as active before the first handshake. It marks the connection as protected only after a recent peer handshake is observed. A successful local build does not prove a remote VPN endpoint is reachable.

## Build

GitHub Actions builds the debug APK when changes are pushed to the `lamavpn` branch or when the workflow is manually started.

1. Open **Actions**.
2. Select **Build LamaVPN APK**.
3. Open the completed run.
4. Download the `LamaVPN-debug-apk` artifact.
5. Extract the ZIP and install `app-debug.apk`.

## Technology

- Kotlin / Android SDK 35
- Java 17
- Official `com.wireguard.android:tunnel` library
- Gradle 8.7 / Android Gradle Plugin 8.6.1

## Limitations of this first release

- You must supply your own authorized WireGuard profile(s).
- Server country/location is based on the name you give the imported profile; the client does not independently geolocate an endpoint.
- A stable connection depends on the selected VPN server and the phone's underlying network. No app can guarantee zero latency or uninterrupted service on every network.
