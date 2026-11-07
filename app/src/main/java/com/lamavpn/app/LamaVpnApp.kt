package com.lamavpn.app

import android.app.Application
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel

class LamaVpnApp : Application() {
    lateinit var wireGuardBackend: GoBackend
        private set

    var activeTunnel: LamaVpnTunnel? = null
    var activeProfileId: String? = null

    override fun onCreate() {
        super.onCreate()
        wireGuardBackend = GoBackend(this)
    }
}

class LamaVpnTunnel(
    private val tunnelName: String,
    private val stateListener: (Tunnel.State) -> Unit
) : Tunnel {
    override fun getName(): String = tunnelName

    override fun onStateChange(newState: Tunnel.State) {
        stateListener(newState)
    }
}
