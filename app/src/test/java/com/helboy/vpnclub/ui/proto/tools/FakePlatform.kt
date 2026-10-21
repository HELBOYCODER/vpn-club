package com.helboy.vpnclub.ui.proto.tools

import dev.valid7996.narcicgeteway.ui.platform.InstalledApp
import dev.valid7996.narcicgeteway.ui.platform.Platform

/** Just enough of [Platform] for the page tests. */
class FakePlatform(
    private val apps: List<InstalledApp> = emptyList(),
    private val perApp: Boolean = true,
) : Platform {
    val toasts = mutableListOf<String>()
    var clipboard: String? = null
    val shared = mutableListOf<Pair<String, ByteArray>>()

    override fun copyToClipboard(text: String) { clipboard = text }
    override fun readClipboard(): String? = clipboard
    override fun shareText(text: String) {}
    override fun openUri(url: String) {}
    override fun toast(message: String) { toasts += message }
    override fun shareFiles(files: List<Pair<String, ByteArray>>) { shared += files }

    override val supportsPerAppProxy: Boolean get() = perApp
    override fun installedApps(): List<InstalledApp> = apps
}
