package com.lamavpn.app

import android.app.Activity
import android.content.Intent
import android.database.Cursor
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

private data class SavedProfile(val id: String, val name: String)

private class EncryptedConfigStore(activity: Activity) {
    private val prefs = activity.getSharedPreferences("lamavpn_profiles", Activity.MODE_PRIVATE)
    private val alias = "lamavpn.wireguard.config.aes"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(alias, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    fun save(name: String, raw: ByteArray): SavedProfile {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(raw)
        val packed = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "." +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        val id = UUID.randomUUID().toString()
        val ids = prefs.getStringSet("ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        ids.add(id)
        prefs.edit()
            .putString("config_$id", packed)
            .putString("name_$id", name)
            .putStringSet("ids", ids)
            .apply()
        return SavedProfile(id, name)
    }

    fun load(id: String): ByteArray {
        val packed = prefs.getString("config_$id", null)
            ?: throw IllegalStateException("Server profile not found")
        val pieces = packed.split(".", limit = 2)
        if (pieces.size != 2) throw IllegalStateException("Stored profile is damaged")
        val iv = Base64.decode(pieces[0], Base64.NO_WRAP)
        val encrypted = Base64.decode(pieces[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), javax.crypto.spec.GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted)
    }

    fun list(): List<SavedProfile> {
        return (prefs.getStringSet("ids", emptySet()) ?: emptySet())
            .mapNotNull { id -> prefs.getString("name_$id", null)?.let { SavedProfile(id, it) } }
            .sortedBy { it.name.lowercase() }
    }

    fun delete(id: String) {
        val ids = prefs.getStringSet("ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        ids.remove(id)
        prefs.edit().putStringSet("ids", ids).remove("config_$id").remove("name_$id").apply()
    }
}

class MainActivity : Activity() {
    private val io = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var store: EncryptedConfigStore
    private lateinit var app: LamaVpnApp
    private lateinit var root: LinearLayout
    private lateinit var statusTitle: TextView
    private lateinit var statusDetail: TextView
    private lateinit var actionButton: TextView
    private lateinit var serverList: LinearLayout
    private lateinit var trafficText: TextView
    private lateinit var orb: FrameLayout
    private var selectedId: String? = null
    private var connected = false
    private var busy = false
    private var pulse: ObjectAnimator? = null
    private val refreshStats = object : Runnable {
        override fun run() {
            val tunnel = app.activeTunnel
            if (!connected || tunnel == null) return
            try {
                val stats = app.wireGuardBackend.getStatistics(tunnel)
                val latest = stats.peers().mapNotNull { stats.peer(it)?.latestHandshakeEpochMillis() }.maxOrNull() ?: 0L
                val handshakeAge = if (latest > 0L) System.currentTimeMillis() - latest else Long.MAX_VALUE
                val handshakeOkay = handshakeAge in 0..180_000
                statusTitle.text = if (handshakeOkay) "Protected connection" else "Tunnel active"
                statusDetail.text = if (handshakeOkay) {
                    "WireGuard server handshake received"
                } else {
                    "Waiting for the server handshake. Check the imported server profile."
                }
                trafficText.text = "↓ ${formatBytes(stats.totalRx())}     ↑ ${formatBytes(stats.totalTx())}"
            } catch (_: Exception) {
                statusDetail.text = "Tunnel is active; connection statistics are temporarily unavailable."
            }
            handler.postDelayed(this, 2500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(8, 14, 29)
        window.navigationBarColor = Color.rgb(8, 14, 29)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        app = application as LamaVpnApp
        store = EncryptedConfigStore(this)
        selectedId = store.list().firstOrNull()?.id
        buildScreen()
        refreshConnectionState()
    }

    private fun buildScreen() {
        val outer = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(8, 14, 29), Color.rgb(12, 24, 43), Color.rgb(8, 14, 29))
            )
        }
        val scroll = ScrollView(this).apply { clipToPadding = false; setPadding(dp(20), dp(18), dp(20), dp(28)) }
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(root)
        outer.addView(scroll)
        setContentView(outer)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = TextView(this).apply {
            text = "◈  LAMA"; setTextColor(Color.WHITE); textSize = 21f
            setTypeface(typeface, Typeface.BOLD); letterSpacing = 0.08f
        }
        val vpn = TextView(this).apply {
            text = "VPN"; setTextColor(Color.rgb(88, 228, 194)); textSize = 21f
            setTypeface(typeface, Typeface.BOLD); letterSpacing = 0.08f
        }
        val version = TextView(this).apply {
            text = "WIREGUARD EDITION"; textSize = 9f; letterSpacing = 0.13f
            setTextColor(Color.rgb(130, 154, 180)); gravity = Gravity.CENTER
            background = rounded(Color.rgb(22, 43, 62), 50)
            setPadding(dp(11), dp(8), dp(11), dp(8))
        }
        header.addView(brand)
        header.addView(vpn)
        header.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        header.addView(version)
        root.addView(header)

        val intro = TextView(this).apply {
            text = "Your connection.\nYour control."
            textSize = 31f; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD)
            setLineSpacing(dp(1).toFloat(), 1f)
        }
        addSpace(22)
        root.addView(intro)
        root.addView(text("An encrypted tunnel powered by the official WireGuard engine.", 14f, "#91A6BF").apply {
            setPadding(0, dp(9), 0, dp(4))
        })

        addSpace(22)
        val hero = card()
        hero.setPadding(dp(19), dp(20), dp(19), dp(18))
        val pill = text("●  VPN STATUS", 10f, "#58E4C2", true).apply { letterSpacing = 0.12f }
        hero.addView(pill)
        addSpaceTo(hero, 13)
        val orbRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        orb = FrameLayout(this).apply {
            background = rounded(Color.rgb(20, 49, 61), 100).apply {
                setStroke(dp(1), Color.rgb(54, 119, 116))
            }
        }
        val orbInner = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(88, 228, 194), Color.rgb(36, 150, 175))
            ).apply { shape = GradientDrawable.OVAL }
        }
        orb.addView(orbInner, FrameLayout.LayoutParams(dp(49), dp(49), Gravity.CENTER))
        orbRow.addView(orb, LinearLayout.LayoutParams(dp(78), dp(78)))
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        labels.setPadding(dp(15), 0, 0, 0)
        statusTitle = text("Not connected", 19f, "#FFFFFF", true)
        statusDetail = text("Import a WireGuard server profile to begin.", 12f, "#91A6BF")
        labels.addView(statusTitle)
        addSpaceTo(labels, 6)
        labels.addView(statusDetail)
        orbRow.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        hero.addView(orbRow)
        addSpaceTo(hero, 15)
        trafficText = text("↓ 0 B     ↑ 0 B", 12f, "#A9C6D8", true).apply {
            background = rounded(Color.rgb(16, 34, 50), 14)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        hero.addView(trafficText)
        addSpaceTo(hero, 16)
        actionButton = text("IMPORT A SERVER PROFILE", 14f, "#06201D", true).apply {
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(88, 228, 194), 16)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            isClickable = true
            setOnClickListener { if (connected) disconnect() else if (store.list().isEmpty()) openConfigPicker() else connectSelected() }
        }
        hero.addView(actionButton)
        root.addView(hero)

        addSpace(24)
        val serverHeader = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        serverHeader.addView(text("YOUR SERVERS", 11f, "#B5C6D8", true).apply { letterSpacing = 0.14f })
        serverHeader.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        val add = text("+  ADD CONFIG", 10f, "#58E4C2", true).apply {
            setPadding(dp(11), dp(9), dp(11), dp(9))
            background = rounded(Color.rgb(20, 49, 61), 12)
            setOnClickListener { openConfigPicker() }
        }
        serverHeader.addView(add)
        root.addView(serverHeader)
        addSpace(11)
        serverList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(serverList)
        addSpace(20)
        root.addView(text("HOW IT WORKS", 11f, "#B5C6D8", true).apply { letterSpacing = 0.14f })
        addSpace(10)
        val safety = card()
        safety.addView(text("🔐  End-to-end tunnel", 14f, "#FFFFFF", true))
        addSpaceTo(safety, 5)
        safety.addView(text("Traffic is routed through the selected WireGuard server when a valid profile is active. Your private key is encrypted on this device.", 12f, "#91A6BF"))
        addSpace(12)
        safety.addView(text("🛡️  No fake locations", 14f, "#FFFFFF", true))
        addSpaceTo(safety, 5)
        safety.addView(text("Only servers included in your own imported configuration appear here. LamaVPN does not invent public servers or claim a connection without a handshake.", 12f, "#91A6BF"))
        root.addView(safety)
        addSpace(16)
        root.addView(text("LamaVPN  •  WireGuard protocol  •  Local encrypted profile storage", 10f, "#657D96").apply {
            gravity = Gravity.CENTER
        })
        renderProfiles()
    }

    private fun renderProfiles() {
        if (!::serverList.isInitialized) return
        serverList.removeAllViews()
        val profiles = store.list()
        if (profiles.isEmpty()) {
            val empty = card()
            empty.addView(text("No server profiles yet", 15f, "#FFFFFF", true))
            addSpaceTo(empty, 5)
            empty.addView(text("Import a .conf file supplied by a WireGuard VPN server administrator or provider. You can add several server locations.", 12f, "#91A6BF"))
            serverList.addView(empty)
            actionButton.text = "IMPORT A SERVER PROFILE"
            actionButton.isEnabled = true
            actionButton.alpha = 1f
            return
        }
        if (profiles.none { it.id == selectedId }) selectedId = profiles.first().id
        profiles.forEach { profile ->
            val selected = profile.id == selectedId
            val item = card().apply {
                background = rounded(if (selected) Color.rgb(20, 55, 62) else Color.rgb(18, 30, 51), 20).apply {
                    setStroke(dp(1), if (selected) Color.rgb(71, 204, 176) else Color.rgb(35, 55, 78))
                }
                setPadding(dp(15), dp(14), dp(15), dp(14))
                isClickable = true
                setOnClickListener {
                    selectedId = profile.id
                    renderProfiles()
                    updateActionLabel()
                }
                setOnLongClickListener {
                    if (connected && app.activeProfileId == profile.id) {
                        Toast.makeText(this@MainActivity, "Disconnect before removing this profile.", Toast.LENGTH_SHORT).show()
                    } else {
                        confirmDelete(profile)
                    }
                    true
                }
            }
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(text(flagFor(profile.name), 25f, "#FFFFFF"))
            val nameBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(8), 0) }
            nameBox.addView(text(profile.name, 14f, "#FFFFFF", true))
            addSpaceTo(nameBox, 4)
            nameBox.addView(text(if (selected) "Selected server profile" else "WireGuard configuration saved", 11f, "#91A6BF"))
            row.addView(nameBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(text(if (selected) "●" else "○", 17f, if (selected) "#58E4C2" else "#70869E", true))
            item.addView(row)
            serverList.addView(item)
            addSpace(9)
        }
        updateActionLabel()
    }

    private fun updateActionLabel() {
        if (::actionButton.isInitialized && !busy) {
            actionButton.text = if (connected) "DISCONNECT" else "CONNECT SECURELY"
            actionButton.isEnabled = selectedId != null
            actionButton.alpha = if (selectedId != null) 1f else 0.55f
        }
    }

    private fun requestImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/plain", "application/octet-stream"))
        }
        startActivityForResult(intent, IMPORT_REQUEST)
    }

    private fun openConfigPicker() = requestImport()

    @Deprecated("Uses the system document picker for broad Android 11 compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data?.data == null) return
        when (requestCode) {
            IMPORT_REQUEST -> importConfig(data.data!!)
            VPN_REQUEST -> if (VpnService.prepare(this) == null) connectSelected()
                else Toast.makeText(this, "VPN permission was not granted.", Toast.LENGTH_LONG).show()
        }
    }

    private fun importConfig(uri: android.net.Uri) {
        setBusy(true, "Reading configuration…")
        io.execute {
            try {
                val raw = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalArgumentException("The selected file could not be opened.")
                if (raw.isEmpty() || raw.size > 512 * 1024) throw IllegalArgumentException("The config file is empty or too large.")
                val parsed = Config.parse(ByteArrayInputStream(raw))
                if (parsed.peers.isEmpty()) throw IllegalArgumentException("The WireGuard config has no [Peer] server section.")
                val displayName = queryDisplayName(uri) ?: "WireGuard server"
                store.save(displayName.removeSuffix(".conf").removeSuffix(".CONF").ifBlank { "WireGuard server" }, raw)
                raw.fill(0)
                runOnUiThread {
                    setBusy(false)
                    selectedId = store.list().firstOrNull { it.name == displayName.removeSuffix(".conf").removeSuffix(".CONF") }?.id
                        ?: store.list().firstOrNull()?.id
                    renderProfiles()
                    statusTitle.text = "Server profile ready"
                    statusDetail.text = "Configuration imported and encrypted on this device."
                    Toast.makeText(this, "WireGuard server profile imported.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    setBusy(false)
                    statusTitle.text = "Import failed"
                    statusDetail.text = e.message ?: "This file is not a valid WireGuard configuration."
                    Toast.makeText(this, "Invalid WireGuard config.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun queryDisplayName(uri: android.net.Uri): String? {
        var name: String? = null
        val cursor: Cursor? = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        cursor?.use { if (it.moveToFirst()) name = it.getString(0) }
        return name
    }

    private fun connectSelected() {
        val id = selectedId ?: run {
            Toast.makeText(this, "Import or select a server profile first.", Toast.LENGTH_SHORT).show()
            return
        }
        if (connected || busy) return
        val permissionIntent = VpnService.prepare(this)
        if (permissionIntent != null) {
            startActivityForResult(permissionIntent, VPN_REQUEST)
            return
        }
        startWireGuard(id)
    }

    private fun startWireGuard(id: String) {
        setBusy(true, "Starting secure tunnel…")
        statusTitle.text = "Connecting"
        statusDetail.text = "Preparing the encrypted WireGuard tunnel."
        io.execute {
            try {
                val raw = store.load(id)
                val config = try { Config.parse(ByteArrayInputStream(raw)) } finally { raw.fill(0) }
                if (config.peers.isEmpty()) throw IllegalArgumentException("No WireGuard peer is configured.")
                val tunnel = LamaVpnTunnel("lamavpn") { state ->
                    runOnUiThread {
                        if (state == Tunnel.State.DOWN) {
                            connected = false
                            pulse?.cancel()
                            setBusy(false)
                            statusTitle.text = "Disconnected"
                            statusDetail.text = "The VPN tunnel is stopped."
                            updateActionLabel()
                        }
                    }
                }
                app.wireGuardBackend.setState(tunnel, Tunnel.State.UP, config)
                app.activeTunnel = tunnel
                app.activeProfileId = id
                runOnUiThread {
                    connected = true
                    setBusy(false)
                    statusTitle.text = "Tunnel active"
                    statusDetail.text = "Waiting for the server's first handshake…"
                    actionButton.text = "DISCONNECT"
                    actionButton.isEnabled = true
                    startPulse()
                    handler.removeCallbacks(refreshStats)
                    handler.post(refreshStats)
                    Toast.makeText(this, "WireGuard tunnel started.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    connected = false
                    setBusy(false)
                    statusTitle.text = "Could not connect"
                    statusDetail.text = e.message ?: "Check the server endpoint and profile."
                    updateActionLabel()
                    Toast.makeText(this, "Connection failed. Check your WireGuard profile.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun disconnect() {
        if (busy) return
        val tunnel = app.activeTunnel
        if (tunnel == null) {
            connected = false
            refreshConnectionState()
            return
        }
        setBusy(true, "Disconnecting…")
        io.execute {
            try {
                app.wireGuardBackend.setState(tunnel, Tunnel.State.DOWN, null)
                app.activeTunnel = null
                app.activeProfileId = null
                runOnUiThread {
                    connected = false
                    handler.removeCallbacks(refreshStats)
                    pulse?.cancel()
                    setBusy(false)
                    statusTitle.text = "Disconnected"
                    statusDetail.text = "Your device is no longer using this VPN tunnel."
                    trafficText.text = "↓ 0 B     ↑ 0 B"
                    updateActionLabel()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    setBusy(false)
                    statusTitle.text = "Disconnect error"
                    statusDetail.text = e.message ?: "Android could not stop the tunnel."
                }
            }
        }
    }

    private fun refreshConnectionState() {
        connected = app.activeTunnel != null
        if (connected) {
            statusTitle.text = "Tunnel active"
            statusDetail.text = "Restoring live tunnel status…"
            actionButton.text = "DISCONNECT"
            handler.removeCallbacks(refreshStats)
            handler.post(refreshStats)
            startPulse()
        } else {
            statusTitle.text = "Not connected"
            statusDetail.text = if (store.list().isEmpty()) "Import a WireGuard server profile to begin."
            else "Choose your server and start the secure tunnel."
            updateActionLabel()
        }
    }

    private fun setBusy(value: Boolean, label: String = "CONNECT SECURELY") {
        busy = value
        if (::actionButton.isInitialized) {
            actionButton.isEnabled = !value
            actionButton.alpha = if (value) 0.7f else 1f
            actionButton.text = label.uppercase()
        }
    }

    private fun startPulse() {
        val target = orb.getChildAt(0) ?: return
        pulse?.cancel()
        pulse = ObjectAnimator.ofFloat(target, View.SCALE_X, 1f, 1.08f, 1f).apply {
            duration = 2100
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        ObjectAnimator.ofFloat(target, View.SCALE_Y, 1f, 1.08f, 1f).apply {
            duration = 2100
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun confirmDelete(profile: SavedProfile) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Remove server profile?")
            .setMessage("Remove ${profile.name} and its encrypted configuration from this device?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Remove") { _, _ ->
                store.delete(profile.id)
                if (selectedId == profile.id) selectedId = store.list().firstOrNull()?.id
                renderProfiles()
                statusTitle.text = "Profile removed"
                statusDetail.text = "The encrypted configuration was deleted from this device."
            }
            .show()
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(Color.rgb(18, 30, 51), 22).apply { setStroke(dp(1), Color.rgb(35, 55, 78)) }
        setPadding(dp(17), dp(16), dp(17), dp(16))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun text(value: String, size: Float, color: String, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(Color.parseColor(color))
            if (bold) setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = true
        }

    private fun rounded(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }

    private fun addSpace(height: Int) {
        root.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun addSpaceTo(parent: LinearLayout, height: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun flagFor(name: String): String {
        val value = name.lowercase()
        return when {
            value.contains("germany") || value.contains("deutsch") || value.startsWith("de") || value.contains("almanya") -> "🇩🇪"
            value.contains("nether") || value.contains("holland") || value.startsWith("nl") -> "🇳🇱"
            value.contains("france") || value.startsWith("fr") -> "🇫🇷"
            value.contains("united states") || value.contains("usa") || value.startsWith("us") -> "🇺🇸"
            value.contains("united kingdom") || value.contains("britain") || value.startsWith("uk") -> "🇬🇧"
            value.contains("turkey") || value.contains("turkiye") || value.startsWith("tr") -> "🇹🇷"
            value.contains("azerbaijan") || value.contains("azerbaycan") || value.startsWith("az") -> "🇦🇿"
            value.contains("sweden") || value.startsWith("se") -> "🇸🇪"
            value.contains("switzerland") || value.startsWith("ch") -> "🇨🇭"
            value.contains("poland") || value.startsWith("pl") -> "🇵🇱"
            else -> "🌐"
        }
    }

    private fun formatBytes(value: Long): String {
        if (value < 1024) return "$value B"
        if (value < 1024 * 1024) return String.format("%.1f KB", value / 1024.0)
        if (value < 1024L * 1024L * 1024L) return String.format("%.1f MB", value / (1024.0 * 1024.0))
        return String.format("%.2f GB", value / (1024.0 * 1024.0 * 1024.0))
    }

    override fun onDestroy() {
        handler.removeCallbacks(refreshStats)
        super.onDestroy()
    }

    companion object {
        private const val IMPORT_REQUEST = 4001
        private const val VPN_REQUEST = 4002
    }
}
