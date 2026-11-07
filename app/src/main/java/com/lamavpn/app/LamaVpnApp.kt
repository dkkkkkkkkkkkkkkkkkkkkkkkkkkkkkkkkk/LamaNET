package com.lamavpn.app

import android.app.Application
import com.wireguard.android.backend.GoBackend

class LamaVpnApp : Application() {
    lateinit var wireGuardBackend: GoBackend
        private set

    override fun onCreate() {
        super.onCreate()
        wireGuardBackend = GoBackend(this)
    }
}
