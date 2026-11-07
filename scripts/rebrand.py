#!/usr/bin/env python3
"""Apply LamaVPN branding to the pinned, GPL-3.0 OpenGateVPN source tree."""
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else "upstream")
if not (root / "app/build.gradle.kts").exists():
    raise SystemExit("Expected an Android project at: " + str(root))

replacements = [
    ("com.payaq.opengatevpn", "com.lamavpn.app"),
    ("OpenGateVPNTheme", "LamaVPNTheme"),
    ("Theme.OpenGateVPN", "Theme.LamaVPN"),
    ("OpenGate VPN", "LamaVPN"),
    ("OpenGateVPN", "LamaVPN"),
    ("OpenGate", "LamaVPN"),
]
text_suffixes = {".kt", ".java", ".xml", ".kts", ".gradle", ".toml", ".properties", ".md", ".txt"}
for path in root.rglob("*"):
    if not path.is_file() or path.suffix.lower() not in text_suffixes:
        continue
    try:
        content = path.read_text(encoding="utf-8")
    except (UnicodeDecodeError, OSError):
        continue
    updated = content
    for old, new in replacements:
        updated = updated.replace(old, new)
    if updated != content:
        path.write_text(updated, encoding="utf-8")

gradle_file = root / "app/build.gradle.kts"
content = gradle_file.read_text(encoding="utf-8")
content = content.replace('namespace = "com.payaq.opengatevpn"', 'namespace = "com.lamavpn.app"')
content = content.replace('applicationId = "com.payaq.opengatevpn"', 'applicationId = "com.lamavpn.app"')
gradle_file.write_text(content, encoding="utf-8")

settings = root / "settings.gradle.kts"
settings.write_text(settings.read_text(encoding="utf-8").replace('rootProject.name = "LamaVPN"', 'rootProject.name = "LamaVPN"'), encoding="utf-8")

(root / "app/src/main/res/values/strings.xml").write_text(
    '<resources>\n    <string name="app_name">LamaVPN</string>\n</resources>\n',
    encoding="utf-8"
)
(root / "app/src/main/res/values/themes.xml").write_text('''<?xml version="1.0" encoding="utf-8"?>
<resources xmlns:tools="http://schemas.android.com/tools">
    <style name="Theme.LamaVPN" parent="android:Theme.Material.NoActionBar">
        <item name="android:windowBackground">#080F1D</item>
        <item name="android:colorBackground">#080F1D</item>
        <item name="android:statusBarColor">#080F1D</item>
        <item name="android:navigationBarColor">#080F1D</item>
        <item name="android:windowLightStatusBar">false</item>
        <item name="android:windowSplashScreenBackground" tools:targetApi="s">#080F1D</item>
        <item name="android:windowSplashScreenAnimatedIcon" tools:targetApi="s">@drawable/ic_opengate_logo</item>
    </style>
</resources>
''', encoding="utf-8")

(root / "app/src/main/java/com/payaq/opengatevpn/ui/theme/Color.kt").write_text('''package com.lamavpn.app.ui.theme

import androidx.compose.ui.graphics.Color

// LamaVPN — deep navy surfaces with a restrained mint signal accent.
val Void = Color(0xFF080F1D)
val Surface = Color(0xFF101A2B)
val SurfaceDim = Color(0xFF080F1D)
val SurfaceContainer = Color(0xFF17243A)
val SurfaceContainerLow = Color(0xFF111D30)
val SurfaceContainerHigh = Color(0xFF1D2D45)
val SurfaceContainerHighest = Color(0xFF263953)
val SurfaceContainerLowest = Color(0xFF0B1423)
val SurfaceBright = Color(0xFF304763)
val CardBackground = Color(0xFF101A2B)

val BorderHairline = Color(0xFF263B55)
val OutlineVariant = Color(0xFF3B536F)
val Outline = Color(0xFF8DA4BF)

val OnSurface = Color(0xFFEAF2FF)
val OnSurfaceVariant = Color(0xFFB8C8DD)
val TextMuted = Color(0xFF8297B2)

val Primary = Color(0xFF66E8C4)
val OnPrimary = Color(0xFF06251E)
val PrimaryContainer = Color(0xFF16463F)

val Secondary = Color(0xFF9BB8FF)
val SecondaryContainer = Color(0xFF263B62)

val StatusSuccess = Color(0xFF55E2A8)
val StatusError = Color(0xFFFF7188)
val StatusPaused = Color(0xFFFFC66E)

val ErrorColor = Color(0xFFFFB4AB)
val OnError = Color(0xFF690005)
val ErrorContainer = Color(0xFF93000A)
val OnErrorContainer = Color(0xFFFFDAD6)
''', encoding="utf-8")

(root / "app/src/main/res/drawable/ic_opengate_logo.xml").write_text('''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="128dp"
    android:height="128dp"
    android:viewportWidth="128"
    android:viewportHeight="128">
    <path android:fillColor="#080F1D" android:pathData="M0,0h128v128h-128z"/>
    <path
        android:fillColor="#12243A"
        android:strokeColor="#66E8C4"
        android:strokeWidth="3"
        android:pathData="M64,13 L103,28 L100,65 C97,88 82,105 64,115 C46,105 31,88 28,65 L25,28 Z"/>
    <path
        android:fillColor="#66E8C4"
        android:pathData="M42,42h12v32h32v12H42z"/>
    <path
        android:fillColor="#9BB8FF"
        android:pathData="M59,42h27v12H59z"/>
    <path
        android:fillColor="#9BB8FF"
        android:pathData="M74,54h12v32H74z"/>
</vector>
''', encoding="utf-8")

manifest = root / "app/src/main/AndroidManifest.xml"
m = manifest.read_text(encoding="utf-8")
m = m.replace('android:allowBackup="true"', 'android:allowBackup="false"')
manifest.write_text(m, encoding="utf-8")
print("LamaVPN branding applied to", root)
