#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENGINE="$ROOT/opengate-engine"
APP="$ENGINE/app"

test -f "$APP/build.gradle.kts"
test -f "$APP/src/main/java/com/payaq/opengatevpn/MainActivity.kt"

python3 - "$APP" <<'PY'
from pathlib import Path
import sys

app = Path(sys.argv[1])

# Keep the upstream Kotlin namespace so the engine and generated R references
# remain source-compatible; use a dedicated LamaVPN application ID.
gradle = app / "build.gradle.kts"
s = gradle.read_text()
s = s.replace('applicationId = "com.payaq.opengatevpn"', 'applicationId = "com.lamavpn.app"')
s = s.replace('versionName = "1.2.0"', 'versionName = "2.0.0"')
s = s.replace('version = release(37)', 'version = release(37)')
s = s.replace('targetSdk = 37', 'targetSdk = 35')
gradle.write_text(s)

# User-visible branding.
strings = app / "src/main/res/values/strings.xml"
strings.write_text('<resources>\n    <string name="app_name">LamaVPN</string>\n</resources>\n')

for rel in [
    "src/main/java/com/payaq/opengatevpn/ui/main/MainScreen.kt",
    "src/main/java/com/payaq/opengatevpn/ui/main/VpnViewModel.kt",
]:
    p = app / rel
    source = p.read_text()
    source = source.replace("OpenGate VPN", "LamaVPN")
    source = source.replace("OpenGate", "LamaVPN")
    p.write_text(source)

# Premium native dark palette: midnight navy surfaces with a bright teal accent.
colors = app / "src/main/java/com/payaq/opengatevpn/ui/theme/Color.kt"
s = colors.read_text()
replacements = {
    "Color(0xFF000000)": "Color(0xFF07111F)",
    "Color(0xFF131313)": "Color(0xFF0D1A2B)",
    "Color(0xFF1F1F1F)": "Color(0xFF14263B)",
    "Color(0xFF1B1B1B)": "Color(0xFF102137)",
    "Color(0xFF2A2A2A)": "Color(0xFF1A3048)",
    "Color(0xFF353535)": "Color(0xFF24415A)",
    "Color(0xFF0E0E0E)": "Color(0xFF091522)",
    "Color(0xFF393939)": "Color(0xFF29465F)",
    "Color(0xFF0A0A0A)": "Color(0xFF0A1726)",
    "Color(0xFF222222)": "Color(0xFF21364D)",
    "Color(0xFF444748)": "Color(0xFF38516A)",
    "Color(0xFF8E9192)": "Color(0xFF829AB1)",
    "Color(0xFFE2E2E2)": "Color(0xFFEAF5FF)",
    "Color(0xFFC4C7C8)": "Color(0xFFB6C9DB)",
    "Color(0xFF888888)": "Color(0xFF8499AF)",
    "Color(0xFFFFFFFF)": "Color(0xFF5CE8C4)",
    "Color(0xFF2F3131)": "Color(0xFF08251F)",
    "Color(0xFFC9C6C5)": "Color(0xFF91B8C5)",
    "Color(0xFF4A4949)": "Color(0xFF24465A)",
    "Color(0xFF00FF66)": "Color(0xFF4BE1A0)",
    "Color(0xFFFF3333)": "Color(0xFFFF647C)",
    "Color(0xFFFFBF00)": "Color(0xFFFFC15C)",
}
for old, new in replacements.items():
    s = s.replace(old, new)
s = s.replace("// Primary — Pure White Accent", "// Primary — LamaVPN electric teal")
s = s.replace("// Canvas & Surfaces", "// LamaVPN midnight-navy surfaces")
colors.write_text(s)

# Make the diagnostics terminal reflect the new product name and show the
# upstream relay network's real logging policy inside the app.
logs = app / "src/main/java/com/payaq/opengatevpn/ui/main/LogsTab.kt"
log_source = logs.read_text()
log_source = log_source.replace("opengate@terminal:~$", "lamavpn@terminal:~$")
log_source = log_source.replace("opengate:~#", "lamavpn:~#")
warning_marker = "        // ── Endpoint & Traffic Row ──"
privacy_note = """        // ── VPN Gate public-relay privacy notice ──
        Text(
            text = "PUBLIC RELAY PRIVACY · VPN Gate connection logs are kept for at least 3 months; volunteer relays keep packet-header logs for at least 2 weeks. Do not use these public servers for sensitive activity.",
            style = TechnicalData.copy(fontSize = 10.sp),
            color = Color(0xFFFFC15C),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFF654C26), RoundedCornerShape(8.dp))
                .background(Color(0xFF211B10))
                .padding(10.dp)
        )

"""
if warning_marker not in log_source:
    raise SystemExit("Could not find LogsTab insertion point")
log_source = log_source.replace(warning_marker, privacy_note + warning_marker, 1)
logs.write_text(log_source)

# Replace the app mark with an original LamaVPN shield vector.
logo = app / "src/main/res/drawable/ic_opengate_logo.xml"
logo.write_text('''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#07111F"
        android:pathData="M0,0h108v108h-108z" />
    <path
        android:fillColor="#5CE8C4"
        android:pathData="M54,9 L91,24 L88,56 C84,76 69,91 54,99 C39,91 24,76 20,56 L17,24 Z" />
    <path
        android:fillColor="#0D1A2B"
        android:pathData="M54,17 L82,28 L80,55 C77,70 65,82 54,88 C43,82 31,70 28,55 L26,28 Z" />
    <path
        android:fillColor="#5CE8C4"
        android:pathData="M49,31h10v18h18v10h-18v18h-10v-18h-18v-10h18z" />
</vector>
''')
PY
