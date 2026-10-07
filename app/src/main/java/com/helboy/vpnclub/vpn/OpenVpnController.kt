package com.helboy.vpnclub.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.helboy.vpnclub.data.model.VpnServer
import de.blinkt.openvpn.VpnProfile
import de.blinkt.openvpn.core.ConfigParser
import de.blinkt.openvpn.core.ConnectionStatus
import de.blinkt.openvpn.core.OpenVPNService
import de.blinkt.openvpn.core.ProfileManager
import de.blinkt.openvpn.core.VPNLaunchHelper
import de.blinkt.openvpn.core.VpnStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayInputStream
import java.io.InputStreamReader

enum class VpnConnectionState {
    DISCONNECTED,
    PREPARING,
    CONNECTING,
    AUTHENTICATING,
    CONNECTED,
    DISCONNECTING,
    ERROR
}

class OpenVpnController(private val context: Context) : VpnStatus.StateListener, VpnStatus.ByteCountListener {

    private val mainHandler = Handler(Looper.getMainLooper())

    private val _connectionState = MutableStateFlow(VpnConnectionState.DISCONNECTED)
    val connectionState: StateFlow<VpnConnectionState> = _connectionState.asStateFlow()

    private val _statusMessage = MutableStateFlow("آماده اتصال")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _downloadSpeed = MutableStateFlow("0 KB/s")
    val downloadSpeed: StateFlow<String> = _downloadSpeed.asStateFlow()

    private val _uploadSpeed = MutableStateFlow("0 KB/s")
    val uploadSpeed: StateFlow<String> = _uploadSpeed.asStateFlow()

    private val _totalDownloaded = MutableStateFlow("0 MB")
    val totalDownloaded: StateFlow<String> = _totalDownloaded.asStateFlow()

    private val _totalUploaded = MutableStateFlow("0 MB")
    val totalUploaded: StateFlow<String> = _totalUploaded.asStateFlow()

    private val _connectedServer = MutableStateFlow<VpnServer?>(null)
    val connectedServer: StateFlow<VpnServer?> = _connectedServer.asStateFlow()

    private val _logEntries = MutableStateFlow<List<String>>(emptyList())
    val logEntries: StateFlow<List<String>> = _logEntries.asStateFlow()

    private var activeProfile: VpnProfile? = null

    init {
        VpnStatus.addStateListener(this)
        VpnStatus.addByteCountListener(this)
    }

    fun release() {
        VpnStatus.removeStateListener(this)
        VpnStatus.removeByteCountListener(this)
    }

    fun isVpnServicePrepared(): Intent? {
        return VpnService.prepare(context)
    }

    fun connect(server: VpnServer): Boolean {
        try {
            _connectedServer.value = server
            _connectionState.value = VpnConnectionState.PREPARING
            _statusMessage.value = "در حال تحلیل و ساخت پروفایل اتصال..."

            val ovpnConfig = server.getDecodedOvpnConfig()
            if (ovpnConfig.isBlank()) {
                _connectionState.value = VpnConnectionState.ERROR
                _statusMessage.value = "کانفیگ سرور نامعتبر یا خالی است"
                return false
            }

            val configParser = ConfigParser()
            val reader = InputStreamReader(ByteArrayInputStream(ovpnConfig.toByteArray()))
            configParser.parseConfig(reader)

            val profile = configParser.convertProfile()
            profile.mName = "VPN CLUB — ${server.countryLong} (${server.ip})"

            // Auto-inject default VPNGate credentials (vpn / vpn)
            profile.mUsername = "vpn"
            profile.mPassword = "vpn"
            profile.mProfileCreator = context.packageName

            // Inject IPv6 ULA to avoid carrier network IPv6 routing faults
            val ula = Ipv6Ula.getOrDerive(context)
            profile.mUseIPv6 = true
            profile.mIPv6Address = "$ula/64"
            profile.mUseDefaultRoutev6 = true

            // Set clean High-Speed DNS to prevent ISP DNS hijacking
            profile.mOverrideDNS = true
            profile.mDNS1 = "1.1.1.1"
            profile.mDNS2 = "8.8.8.8"

            activeProfile = profile
            ProfileManager.setTemporaryProfile(context, profile)

            _connectionState.value = VpnConnectionState.CONNECTING
            _statusMessage.value = "در حال اتصال به ${server.countryLong}..."

            VPNLaunchHelper.startOpenVpn(profile, context, "VPNClub", true)
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error starting VPN connection", e)
            _connectionState.value = VpnConnectionState.ERROR
            _statusMessage.value = "خطا در تنظیم پروفایل: ${e.message}"
            return false
        }
    }

    fun disconnect() {
        _connectionState.value = VpnConnectionState.DISCONNECTING
        _statusMessage.value = "در حال قطع اتصال..."

        try {
            val intent = Intent(context, OpenVPNService::class.java).apply {
                action = OpenVPNService.DISCONNECT_VPN
            }
            context.startService(intent)
            ProfileManager.setConntectedVpnProfileDisconnected(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping VPN", e)
        }
    }

    override fun updateState(
        state: String?,
        logmessage: String?,
        localizedResId: Int,
        level: ConnectionStatus?,
        intent: Intent?
    ) {
        mainHandler.post {
            val cleanLog = VpnStatus.getLastCleanLogMessage(context)
            if (!cleanLog.isNullOrBlank()) {
                val current = _logEntries.value.toMutableList()
                if (current.isEmpty() || current.last() != cleanLog) {
                    current.add(cleanLog)
                    if (current.size > 50) current.removeAt(0)
                    _logEntries.value = current
                }
            }

            when (level) {
                ConnectionStatus.LEVEL_CONNECTED -> {
                    _connectionState.value = VpnConnectionState.CONNECTED
                    _statusMessage.value = "متصل شد (تونل سراسری فعال)"
                }
                ConnectionStatus.LEVEL_CONNECTING_SERVER_REPLIED -> {
                    _connectionState.value = VpnConnectionState.AUTHENTICATING
                    _statusMessage.value = "احراز هویت و دریافت IP..."
                }
                ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLIED_YET -> {
                    _connectionState.value = VpnConnectionState.CONNECTING
                    _statusMessage.value = "در حال برقراری دست‌تکان..."
                }
                ConnectionStatus.LEVEL_NOTCONNECTED -> {
                    if (_connectionState.value != VpnConnectionState.PREPARING) {
                        _connectionState.value = VpnConnectionState.DISCONNECTED
                        _statusMessage.value = "قطع اتصال"
                        _downloadSpeed.value = "0 KB/s"
                        _uploadSpeed.value = "0 KB/s"
                    }
                }
                ConnectionStatus.LEVEL_AUTH_FAILED -> {
                    _connectionState.value = VpnConnectionState.ERROR
                    _statusMessage.value = "خطای احراز هویت در سرور"
                }
                ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT -> {
                    _connectionState.value = VpnConnectionState.AUTHENTICATING
                    _statusMessage.value = "در انتظار تایید سیستم..."
                }
                else -> {
                    if (!logmessage.isNullOrBlank()) {
                        _statusMessage.value = logmessage
                    }
                }
            }
        }
    }

    override fun setConnectedVPN(uuid: String?) {
        // Ignored
    }

    override fun updateByteCount(`in`: Long, out: Long, diffIn: Long, diffOut: Long) {
        mainHandler.post {
            _downloadSpeed.value = formatSpeed(diffIn)
            _uploadSpeed.value = formatSpeed(diffOut)
            _totalDownloaded.value = formatTotalBytes(`in`)
            _totalUploaded.value = formatTotalBytes(out)
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        val speedKBs = bytesPerSec / 1024.0
        return if (speedKBs >= 1024.0) {
            String.format("%.2f MB/s", speedKBs / 1024.0)
        } else {
            String.format("%.1f KB/s", speedKBs)
        }
    }

    private fun formatTotalBytes(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            String.format("%.2f GB", mb / 1024.0)
        } else {
            String.format("%.1f MB", mb)
        }
    }

    companion object {
        private const val TAG = "OpenVpnController"
    }
}
