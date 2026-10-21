package com.helboy.vpnclub.ui.proto.editors

import dev.valid7996.narcicgeteway.domain.config.Ikev2Auth
import dev.valid7996.narcicgeteway.domain.config.Ikev2Profile
import dev.valid7996.narcicgeteway.domain.config.OpenConnectProfile

/**
 * The IKEv2 form. Passwords and pre-shared keys are stored sealed: the form never holds the stored value in a
 * visible field. [password] / [psk] stay empty unless the person typed a new one; empty means "keep what is stored".
 */
data class Ikev2Form(
    val name: String = "",
    val server: String = "",
    val remoteId: String = "",
    val localId: String = "",
    val authType: Ikev2Auth = Ikev2Auth.EAP_MSCHAPV2,
    val username: String = "",
    val password: String = "",
    val psk: String = "",
    val caCertPem: String = "",
    val caLabel: String = "",
    val userCertAlias: String = "",
    val ikeProposal: String = "",
    val espProposal: String = "",
    val mtu: String = DEFAULT_MTU.toString(),
    val storedPassword: String = "",
    val storedPsk: String = "",
) {
    val hasStoredPassword: Boolean get() = storedPassword.isNotBlank()
    val hasStoredPsk: Boolean get() = storedPsk.isNotBlank()

    /**
     * Messages per field. The rules are those of [Ikev2Profile.isValid]; [proposalOk] is the platform's proposal
     * parser (`Ikev2CertBridge.validateProposal`), absent when the platform has none.
     */
    fun check(proposalOk: ((spec: String, child: Boolean) -> Boolean)? = null): FormCheck = formCheck {
        if (server.isBlank()) error(F_SERVER, "آدرس سرور را وارد کنید.")
        else if (FormText.hasWhitespace(server)) error(F_SERVER, "آدرس نباید فاصله داشته باشد.")
        when (authType) {
            Ikev2Auth.EAP_MSCHAPV2 -> {
                if (username.isBlank()) error(F_USER, "نام کاربری را وارد کنید.")
                if (password.isBlank() && !hasStoredPassword) error(F_PASSWORD, "رمز عبور را وارد کنید.")
            }
            Ikev2Auth.CERTIFICATE -> if (userCertAlias.isBlank()) error(F_CERT, "یک گواهی کاربر انتخاب کنید.")
            Ikev2Auth.PSK -> if (psk.isBlank() && !hasStoredPsk) error(F_PSK, "کلید مشترک را وارد کنید.")
            Ikev2Auth.EAP_TLS -> error(F_SERVER, "EAP-TLS پشتیبانی نمی‌شود؛ نوع گواهی را انتخاب کنید.")
        }
        if (proposalOk != null) {
            if (ikeProposal.isNotBlank() && !proposalOk(ikeProposal, false)) error(F_IKE, "الگوریتم ناشناخته. مثل aes256-sha256-modp2048.")
            if (espProposal.isNotBlank() && !proposalOk(espProposal, true)) error(F_ESP, "الگوریتم ناشناخته. مثل aes256-sha256.")
        }
        val m = mtu.trim().toIntOrNull()
        if (mtu.isNotBlank() && (m == null || m !in MIN_MTU..MAX_MTU)) warn(F_MTU, "MTU بین ۱۲۸۰ تا ۱۵۰۰ تنظیم می‌شود.")
    }

    fun toProfile(): Ikev2Profile = Ikev2Profile(
        server = server.trim(),
        remoteId = remoteId.trim(),
        localId = localId.trim(),
        authType = authType,
        username = username.trim(),
        password = if (password.isNotEmpty()) Ikev2Profile.sealPassword(password) else storedPassword,
        psk = if (psk.isNotEmpty()) Ikev2Profile.sealPsk(psk) else storedPsk,
        caCertPem = caCertPem,
        userCertAlias = userCertAlias,
        ikeProposal = ikeProposal.trim(),
        espProposal = espProposal.trim(),
        mtu = mtu.trim().toIntOrNull()?.coerceIn(MIN_MTU, MAX_MTU) ?: DEFAULT_MTU,
    )

    companion object {
        const val DEFAULT_MTU = 1400
        const val MIN_MTU = 1280
        const val MAX_MTU = 1500
        const val F_SERVER = "server"
        const val F_USER = "username"
        const val F_PASSWORD = "password"
        const val F_PSK = "psk"
        const val F_CERT = "cert"
        const val F_IKE = "ike"
        const val F_ESP = "esp"
        const val F_MTU = "mtu"

        fun from(p: Ikev2Profile?, name: String = ""): Ikev2Form =
            if (p == null) Ikev2Form(name = name) else Ikev2Form(
                name = name,
                server = p.server,
                remoteId = p.remoteId,
                localId = p.localId,
                authType = p.effectiveAuth,
                username = p.username,
                caCertPem = p.caCertPem,
                userCertAlias = p.userCertAlias,
                ikeProposal = p.ikeProposal,
                espProposal = p.espProposal,
                mtu = p.mtu.toString(),
                storedPassword = p.password,
                storedPsk = p.psk,
            )
    }
}

/** The OpenConnect form: the server and its port are two fields, joined by [OpenConnectProfile.withPort]. */
data class OpenConnectForm(
    val name: String = "",
    val server: String = "",
    val port: String = "",
    val protocol: String = OpenConnectProfile.PROTO_ANYCONNECT,
    val username: String = "",
    val password: String = "",
    val authgroup: String = "",
    val certPin: String = "",
    val sni: String = "",
    val proxy: String = "",
    val mtu: String = "",
    val reconnect: String = OpenConnectProfile.DEFAULT_RECONNECT_TIMEOUT.toString(),
    val userAgent: String = "",
    val reportedOs: String = "android",
    val certP12Base64: String = "",
    val certP12Name: String = "",
    val caCert: String = "",
    val clientCert: String = "",
    val clientKey: String = "",
    val keyPassword: String = "",
    val tokenMode: String = OpenConnectProfile.TOKEN_NONE,
    val tokenSecret: String = "",
    val disableDtls: Boolean = false,
    val disableIpv6: Boolean = false,
    /** Answers the gateway's login form asked for earlier; kept so editing does not forget them. */
    val keptFormEntries: Map<String, String> = emptyMap(),
) {
    fun check(): FormCheck = formCheck {
        // The profile's own rule: a server is required.
        if (!OpenConnectProfile(server = server.trim()).isValid) error(F_SERVER, "آدرس سرور را وارد کنید.")
        else if (FormText.hasWhitespace(server)) error(F_SERVER, "آدرس نباید فاصله داشته باشد.")
        if (port.isNotBlank() && FormText.port(port) == null) error(F_PORT, "پورت باید عددی بین ۱ تا ۶۵۵۳۵ باشد.")
        if (mtu.isNotBlank() && mtu.trim().toIntOrNull() == null) error(F_MTU, "عدد صحیح وارد کنید.")
        if (reconnect.isNotBlank() && reconnect.trim().toIntOrNull() == null) error(F_RECONNECT, "عدد صحیح وارد کنید.")
        if (tokenMode != OpenConnectProfile.TOKEN_NONE && tokenSecret.isBlank()) warn(F_TOKEN, "برای این حالت راز توکن لازم است.")
        if (certP12Base64.isBlank() && (clientCert.isBlank() != clientKey.isBlank())) {
            warn(F_CLIENT_CERT, "گواهی کلاینت و کلید آن باید با هم وارد شوند.")
        }
    }

    fun toProfile(): OpenConnectProfile = OpenConnectProfile(
        server = OpenConnectProfile.withPort(server.trim(), FormText.port(port)),
        protocol = protocol,
        username = username.trim(),
        password = password,
        authgroup = authgroup.trim(),
        caCertPem = caCert.trim(),
        serverCertSha256 = certPin.trim(),
        clientCertPem = clientCert.trim(),
        clientKeyPem = clientKey.trim(),
        clientKeyPassword = keyPassword,
        userAgent = userAgent.trim(),
        reportedOs = reportedOs.trim().ifBlank { "android" },
        tokenMode = tokenMode,
        tokenSecret = tokenSecret.trim(),
        disableDtls = disableDtls,
        clientCertP12Base64 = certP12Base64,
        sni = sni.trim(),
        mtu = mtu.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0,
        reconnectTimeoutSec = reconnect.trim().toIntOrNull()?.takeIf { it > 0 } ?: OpenConnectProfile.DEFAULT_RECONNECT_TIMEOUT,
        proxy = proxy.trim(),
        disableIpv6 = disableIpv6,
        formEntries = keptFormEntries,
    )

    companion object {
        const val F_SERVER = "server"
        const val F_PORT = "port"
        const val F_MTU = "mtu"
        const val F_RECONNECT = "reconnect"
        const val F_TOKEN = "token"
        const val F_CLIENT_CERT = "clientCert"

        fun from(p: OpenConnectProfile?, name: String = "", p12Label: String = ""): OpenConnectForm {
            if (p == null) return OpenConnectForm(name = name)
            val (host, port) = OpenConnectProfile.splitPort(p.server)
            return OpenConnectForm(
                name = name,
                server = host,
                port = port?.toString() ?: "",
                protocol = p.protocol,
                username = p.username,
                password = p.password,
                authgroup = p.authgroup,
                certPin = p.serverCertSha256,
                sni = p.sni,
                proxy = p.proxy,
                mtu = p.mtu.takeIf { it > 0 }?.toString() ?: "",
                reconnect = p.reconnectTimeoutSec.toString(),
                userAgent = p.userAgent,
                reportedOs = p.reportedOs,
                certP12Base64 = p.clientCertP12Base64,
                certP12Name = p12Label,
                caCert = p.caCertPem,
                clientCert = p.clientCertPem,
                clientKey = p.clientKeyPem,
                keyPassword = p.clientKeyPassword,
                tokenMode = p.tokenMode,
                tokenSecret = p.tokenSecret,
                disableDtls = p.disableDtls,
                disableIpv6 = p.disableIpv6,
                keptFormEntries = p.formEntries,
            )
        }
    }
}
