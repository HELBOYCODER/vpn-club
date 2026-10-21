package com.helboy.vpnclub.bridge

import android.content.Context
import com.helboy.vpnclub.corex.HellboyVpnService
import com.helboy.vpnclub.corex.Phase
import com.helboy.vpnclub.corex.Task
import com.helboy.vpnclub.corex.VpnController
import dev.valid7996.narcicgeteway.core.AndroidVpn
import dev.valid7996.narcicgeteway.core.Ikev2Controller
import dev.valid7996.narcicgeteway.core.VpnManager
import dev.valid7996.narcicgeteway.domain.model.ConnectionState

/**
 * Two engines live in one app: the Hellboy engine (free Psiphon/WARP, serverless, scanner-injected servers)
 * and the Narcic engine (every other protocol and the subscription/profile store). Only one tunnel may be up
 * at a time, and the Home screen has to show whichever one is running.
 *
 * This object owns both rules: [install] makes starting either tunnel stop the other, and [overlay] folds
 * the Narcic status into the Hellboy UI state so Home, the face button and the stats tiles stay unchanged.
 */
object EngineBridge {

    /** What the Narcic engine is connected to, for the Home card (the Hellboy engine has its own server card). */
    data class External(val name: String, val connected: Boolean)

    @Volatile private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        // Starting the Narcic tunnel while Hellboy is up (or the other way round) would make Android revoke one
        // of them mid-flight; stop the other side first so the user sees a clean switch instead of an error.
        AndroidVpn.beforeStart = {
            val h = VpnController.state.value
            if (h.connected || h.busyConnecting) VpnController.disconnect()
        }
        HellboyVpnService.beforeStart = {
            if (narcicBusy(VpnManager.status.value.state)) stopNarcic(app)
        }
    }

    fun narcicBusy(state: ConnectionState): Boolean = state.isActive || state.isTransitioning

    fun stopNarcic(context: Context) {
        if (Ikev2Controller.isActive) Ikev2Controller.stop(context) else AndroidVpn.stop(context)
    }

    /** True when the Narcic engine owns the tunnel (so Home's button should stop it, not start Hellboy's). */
    fun narcicOwnsTunnel(n: VpnManager.Status): Boolean = narcicBusy(n.state)

    /**
     * Folds [n] into [h]. When the Narcic engine is busy its connection state wins, because the Hellboy
     * engine cannot be running at the same time (see [install]); otherwise [h] is returned untouched.
     */
    fun overlay(h: VpnController.UiState, n: VpnManager.Status, nowMs: Long = System.currentTimeMillis()): VpnController.UiState {
        if (!narcicBusy(n.state) && n.state != ConnectionState.Error) return h
        // An old Narcic error must not hide a live Hellboy connection.
        if (n.state == ConnectionState.Error && (h.connected || h.busyConnecting)) return h
        return when (n.state) {
            ConnectionState.Connected -> h.copy(
                phase = Phase.CONNECTED, connected = true, current = null, task = Task.NONE, taskMsg = "",
                message = n.serverName.orEmpty(),
                upBps = n.uploadBps, downBps = n.downloadBps, upTotal = n.totalUpload, downTotal = n.totalDownload,
                connectedSince = nowMs - n.durationSeconds * 1000L, latencyMs = -1,
            )
            ConnectionState.Connecting, ConnectionState.Reconnecting -> h.copy(
                phase = Phase.CONNECTING, connected = false, current = null, task = Task.CONNECT,
                taskMsg = n.serverName.orEmpty(), message = n.serverName.orEmpty(),
            )
            ConnectionState.Disconnecting -> h.copy(
                phase = Phase.IDLE, connected = false, current = null, task = Task.NONE, taskMsg = "", message = "",
            )
            ConnectionState.Error -> h.copy(
                phase = Phase.FAILED, connected = false, current = null, task = Task.NONE, taskMsg = "",
                message = n.error.orEmpty(),
            )
            ConnectionState.Idle -> h
        }
    }

    fun externalOf(n: VpnManager.Status): External? =
        if (narcicOwnsTunnel(n)) External(n.serverName.orEmpty(), n.state == ConnectionState.Connected) else null
}
