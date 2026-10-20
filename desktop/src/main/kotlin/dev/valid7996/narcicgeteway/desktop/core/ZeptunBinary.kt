package dev.valid7996.narcicgeteway.desktop.core

import java.io.File

object ZeptunBinary {
    val WINDOWS_COMPANIONS = listOf("wintun.dll")

    fun extract(workDir: File): File? =
        BundledBinary.extract(workDir, "zeptun", "zeptun.exe", WINDOWS_COMPANIONS)
}
