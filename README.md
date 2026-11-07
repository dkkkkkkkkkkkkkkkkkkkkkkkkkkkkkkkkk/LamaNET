# LamaNET — Android local-network prototype

LamaNET is an Android application prototype that demonstrates a local service running on one phone.

## What this version does

- Starts a small HTTP server inside the Android app.
- Binds the server to `127.0.0.1:8080` (loopback only).
- Opens the local LamaNET page in the app's WebView.
- Works without a SIM card or mobile-data connection.

## Important limitations

This first version is a **single-device local prototype**, not a replacement for the public internet. It does not connect to worldwide websites and does not create a network for other phones. A multi-device network needs at least one other endpoint device and a supported communication path.

## Build an APK with GitHub Actions

1. Open the repository's **Actions** tab.
2. Select **Build LamaNet APK**.
3. Open the successful workflow run.
4. Download the **LamaNet-debug-apk** artifact.
5. Extract the ZIP and install `app-debug.apk` on an Android device.

The workflow builds a debug APK using JDK 17, Gradle 8.7, Android Gradle Plugin 8.6.1 and Android SDK 35.
