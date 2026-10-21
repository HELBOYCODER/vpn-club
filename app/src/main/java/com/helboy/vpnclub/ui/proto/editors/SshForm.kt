package com.helboy.vpnclub.ui.proto.editors

import dev.valid7996.narcicgeteway.domain.config.SshProfile

/** The SSH tunnel form: text as typed, mapped to and from [SshProfile]. */
data class SshForm(
    val name: String = "",
    val host: String = "",
    val port: String = "22",
    val username: String = "",
    val authType: String = SshProfile.AUTH_PASSWORD,
    val password: String = "",
    val privateKey: String = "",
    val passphrase: String = "",
) {
    val usesKey: Boolean get() = authType == SshProfile.AUTH_KEY

    fun check(): FormCheck = formCheck {
        val h = host.trim()
        if (h.isEmpty()) error(F_HOST, "آدرس سرور SSH را وارد کنید.")
        else if (FormText.hasWhitespace(h)) error(F_HOST, "آدرس نباید فاصله داشته باشد.")
        if (FormText.port(port) == null) error(F_PORT, "پورت باید عددی بین ۱ تا ۶۵۵۳۵ باشد.")
        if (usesKey) {
            if (privateKey.isBlank()) error(F_KEY, "کلید خصوصی را وارد کنید یا از فایل بخوانید.")
            else if (!privateKey.contains("PRIVATE KEY")) warn(F_KEY, "این متن شبیه کلید خصوصی (PEM) نیست.")
        } else if (password.isEmpty()) {
            error(F_PASSWORD, "رمز عبور را وارد کنید.")
        }
    }

    fun toProfile(): SshProfile = SshProfile(
        host = host.trim(),
        port = FormText.port(port) ?: 22,
        username = username.trim(),
        authType = authType,
        password = password,
        privateKey = privateKey.trim(),
        keyPassphrase = passphrase,
    )

    companion object {
        const val F_HOST = "host"
        const val F_PORT = "port"
        const val F_KEY = "key"
        const val F_PASSWORD = "password"

        fun from(profile: SshProfile?, name: String = ""): SshForm =
            if (profile == null) SshForm(name = name) else SshForm(
                name = name,
                host = profile.host,
                port = profile.port.toString(),
                username = profile.username,
                authType = profile.authType,
                password = profile.password,
                privateKey = profile.privateKey,
                passphrase = profile.keyPassphrase,
            )
    }
}
