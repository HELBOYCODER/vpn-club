package com.helboy.vpnclub.ui.proto.editors

import dev.valid7996.narcicgeteway.ui.platform.FilePick
import dev.valid7996.narcicgeteway.ui.platform.Platform
import dev.valid7996.narcicgeteway.ui.servers.CertFile
import dev.valid7996.narcicgeteway.ui.servers.CertImport
import dev.valid7996.narcicgeteway.ui.servers.CertKind
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PemFilesTest {
    private val key = "-----BEGIN OPENSSH PRIVATE KEY-----\nabc\n-----END OPENSSH PRIVATE KEY-----\n"
    private val cert = "-----BEGIN CERTIFICATE-----\nabc\n-----END CERTIFICATE-----\n"

    @After fun reset() {
        CertImport.inspect = null
    }

    @Test fun `pem text is accepted only when it looks like pem`() {
        assertEquals(key.trim(), PemFiles.readText(key.toByteArray(), "PRIVATE KEY"))
        assertNull(PemFiles.readText(cert.toByteArray(), "PRIVATE KEY"))
        assertNull(PemFiles.readText("hello".toByteArray()))
        assertNull(PemFiles.readText(ByteArray(0)))
        assertEquals(cert.trim(), PemFiles.readText(cert.toByteArray(), "CERTIFICATE"))
    }

    @Test fun `an oversized file is refused`() {
        assertNull(PemFiles.readText(("-----BEGIN " + "A".repeat(300_000)).toByteArray()))
    }

    @Test fun `base64 of a bundle`() {
        assertEquals("AQID", PemFiles.base64(byteArrayOf(1, 2, 3)))
    }

    private fun platformPicking(file: FilePick?) = object : Platform {
        override fun copyToClipboard(text: String) {}
        override fun readClipboard(): String? = null
        override fun shareText(text: String) {}
        override fun openUri(url: String) {}
        override fun toast(message: String) {}
        override suspend fun pickFileBytes(): FilePick? = file
    }

    @Test fun `picking a cancelled dialog is not an error`() = runBlocking {
        assertTrue(CertFiles.pick(platformPicking(null), CertKind.Pkcs12, "no") is CertPick.Cancelled)
    }

    @Test fun `a file of the wrong kind is rejected with the given message`() = runBlocking {
        CertImport.inspect = { _, name -> CertFile(CertKind.Certificate, name, name) }
        val r = CertFiles.pick(platformPicking(FilePick("a.crt", byteArrayOf(1))), CertKind.Pkcs12, "not a bundle")
        assertEquals(CertPick.Rejected("not a bundle"), r)
    }

    @Test fun `a file of the wanted kind is returned with its info`() = runBlocking {
        CertImport.inspect = { _, name -> CertFile(CertKind.Pkcs12, name, name) }
        val r = CertFiles.pick(platformPicking(FilePick("a.p12", byteArrayOf(1))), CertKind.Pkcs12, "x")
        assertTrue(r is CertPick.Picked)
        assertEquals("a.p12", (r as CertPick.Picked).file.name)
    }

    @Test fun `without an inspector any file passes`() = runBlocking {
        val r = CertFiles.pick(platformPicking(FilePick("a.p12", byteArrayOf(1))), CertKind.Pkcs12, "x")
        assertTrue(r is CertPick.Picked)
    }

    @Test fun `the picker throwing is reported, not thrown`() = runBlocking {
        val broken = object : Platform {
            override fun copyToClipboard(text: String) {}
            override fun readClipboard(): String? = null
            override fun shareText(text: String) {}
            override fun openUri(url: String) {}
            override fun toast(message: String) {}
            override suspend fun pickFileBytes(): FilePick? = error("boom")
        }
        assertEquals(CertPick.Rejected(CertFiles.PICK_FAILED), CertFiles.pick(broken, null, "x"))
    }

    @Test fun `the pem of a certificate comes from the inspector first`() {
        assertEquals("P", CertFiles.pemOf(CertFile(CertKind.Certificate, "a", "a", pem = "P"), cert.toByteArray()))
        assertEquals(cert.trim(), CertFiles.pemOf(null, cert.toByteArray()))
        assertNull(CertFiles.pemOf(null, "nope".toByteArray()))
    }
}
