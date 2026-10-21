package com.helboy.vpnclub.ui.proto

import dev.valid7996.narcicgeteway.data.config.ConfigRepository
import dev.valid7996.narcicgeteway.data.settings.SettingsRepository

/**
 * Where the Hellboy screens for the protocol engine find its stores. The Application installs the real ones
 * at start-up; tests install in-memory ones. Screens never create their own repositories.
 */
object ProtoEnv {
    @Volatile private var repoRef: ConfigRepository? = null
    @Volatile private var settingsRef: SettingsRepository? = null

    val repo: ConfigRepository
        get() = checkNotNull(repoRef) { "ProtoEnv.install was not called" }

    val settings: SettingsRepository
        get() = checkNotNull(settingsRef) { "ProtoEnv.install was not called with a SettingsRepository" }

    fun install(repo: ConfigRepository, settings: SettingsRepository? = null) {
        repoRef = repo
        if (settings != null) settingsRef = settings
    }
}
