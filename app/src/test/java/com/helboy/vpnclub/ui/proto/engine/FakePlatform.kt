package com.helboy.vpnclub.ui.proto.engine

import dev.valid7996.narcicgeteway.ui.platform.Platform

/** The few Platform members the engine pages touch, recording what they were asked to do. */
class FakePlatform : Platform {
    val copied = mutableListOf<String>()
    val opened = mutableListOf<String>()
    val toasts = mutableListOf<String>()

    override fun copyToClipboard(text: String) { copied += text }
    override fun readClipboard(): String? = copied.lastOrNull()
    override fun shareText(text: String) {}
    override fun openUri(url: String) { opened += url }
    override fun toast(message: String) { toasts += message }
}
